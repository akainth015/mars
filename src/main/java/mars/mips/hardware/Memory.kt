package mars.mips.hardware

import mars.Globals
import mars.ProgramStatement
import mars.Settings
import mars.mips.instructions.Instruction
import mars.simulator.Exceptions
import mars.util.Binary
import java.util.*

/**
 * Represents MIPS memory.  Different segments are represented by different data structs.
 *
 * @author Pete Sanderson
 * @version August 2003
 */
/////////////////////////////////////////////////////////////////////
// NOTE: This implementation is purely big-endian.  MIPS can handle either one.
/////////////////////////////////////////////////////////////////////
object Memory : Observable() {
    /**
     * Constant representing byte order of each memory word.  Little-endian means lowest
     * numbered byte is right most [3][2][1][0].
     */
    private const val LITTLE_ENDIAN = true
    private const val BLOCK_LENGTH_WORDS = 1024 // allocated blocksize 1024 ints == 4K bytes
    private const val BLOCK_TABLE_LENGTH = 1024 // Each entry of table points to a block.
    private const val MMIO_TABLE_LENGTH = 16 // Each entry of table points to a 4K block.
    private const val TEXT_BLOCK_LENGTH_WORDS = 1024 // allocated blocksize 1024 ints == 4K bytes
    private const val TEXT_BLOCK_TABLE_LENGTH = 1024 // Each entry of table points to a block.

    private var observables = newMemoryObserversCollection
    private var dataBlockTable: Array<IntArray?> = arrayOfNulls(BLOCK_TABLE_LENGTH)
    private var kernelDataBlockTable: Array<IntArray?> = arrayOfNulls(BLOCK_TABLE_LENGTH)
    private var stackBlockTable: Array<IntArray?> = arrayOfNulls(BLOCK_TABLE_LENGTH)

    // This will be a Singleton class, only one instance is ever created.  Since I know the 
    // Memory object is always needed, I'll go ahead and create it at the time of class loading.
    // (greedy rather than lazy instantiation).  The constructor is private and getInstance()
    // always returns this instance.
    private var memoryMapBlockTable: Array<IntArray?> = arrayOfNulls(MMIO_TABLE_LENGTH)
    private var textBlockTable: Array<Array<ProgramStatement?>?> = arrayOfNulls(TEXT_BLOCK_TABLE_LENGTH)
    private var kernelTextBlockTable: Array<Array<ProgramStatement?>?> = arrayOfNulls(TEXT_BLOCK_TABLE_LENGTH)

    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Explicitly clear the contents of memory.  Typically done at start of assembly.
     */
    fun clear() {
        setConfiguration()
        initialize()
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Determine whether the current memory configuration has a maximum address that can be stored
     * in 16 bits.
     *
     * @return true if maximum address can be stored in 16 bits or less, false otherwise
     */
    fun usingCompactMemoryConfiguration(): Boolean {
        return kernelHighAddress and 0x00007fff == kernelHighAddress
    }

    ////////////////////////////////////////////////////////////////////////////////
    private fun initialize() {
        heapAddress = heapBaseAddress
        textBlockTable = arrayOfNulls<Array<ProgramStatement?>?>(TEXT_BLOCK_TABLE_LENGTH)
        dataBlockTable = arrayOfNulls(BLOCK_TABLE_LENGTH) // array of null int[] references
        kernelTextBlockTable = arrayOfNulls<Array<ProgramStatement?>?>(TEXT_BLOCK_TABLE_LENGTH)
        kernelDataBlockTable = arrayOfNulls(BLOCK_TABLE_LENGTH)
        stackBlockTable = arrayOfNulls(BLOCK_TABLE_LENGTH)
        memoryMapBlockTable = arrayOfNulls(MMIO_TABLE_LENGTH)
        System.gc() // call garbage collector on any Table memory just deallocated.
    }

    /**
     * Returns the next available word-aligned heap address.  There is no recycling and
     * no heap management!  There is however nearly 4MB of heap space available in Mars.
     *
     * @param numBytes Number of bytes requested.  Should be multiple of 4, otherwise next higher multiple of 4 allocated.
     * @return address of allocated heap storage.
     * @throws IllegalArgumentException if number of requested bytes is negative or exceeds available heap storage
     */
    @Throws(IllegalArgumentException::class)
    fun allocateBytesFromHeap(numBytes: Int): Int {
        val result = heapAddress
        require(numBytes >= 0) { "request ($numBytes) is negative heap amount" }
        var newHeapAddress = heapAddress + numBytes
        if (newHeapAddress % 4 != 0) {
            newHeapAddress = newHeapAddress + (4 - newHeapAddress % 4) // next higher multiple of 4
        }
        require(newHeapAddress < dataSegmentLimitAddress) { "request ($numBytes) exceeds available heap storage" }
        heapAddress = newHeapAddress
        return result
    }

    /**
     * Starting at the given address, write the given value over the given number of bytes.
     * This one does not check for word boundaries, and copies one byte at a time.
     * If length == 1, takes value from low order byte.  If 2, takes from low order half-word.
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored starting at that address.
     * @param length  Number of bytes to be written.
     * @return old value that was replaced by the set operation
     */
    // Allocates blocks if necessary.
    @Throws(AddressErrorException::class)
    operator fun set(address: Int, value: Int, length: Int): Int {
        var oldValue = 0
        if (Globals.debug) println("memory[$address] set to $value($length bytes)")
        val relativeByteAddress: Int
        if (inDataSegment(address)) {
            // in data segment.  Will write one byte at a time, w/o regard to boundaries.
            relativeByteAddress = address - dataSegmentBaseAddress // relative to data segment start, in bytes
            oldValue = storeBytesInTable(dataBlockTable, relativeByteAddress, length, value)
        } else if (address > stackLimitAddress && address <= stackBaseAddress) {
            // in stack.  Handle similarly to data segment write, except relative byte
            // address calculated "backward" because stack addresses grow down from base.
            relativeByteAddress = stackBaseAddress - address
            oldValue = storeBytesInTable(stackBlockTable, relativeByteAddress, length, value)
        } else if (inTextSegment(address)) {
            // Burch Mod (Jan 2013): replace throw with call to setStatement
            // DPS adaptation 5-Jul-2013: either throw or call, depending on setting
            if (Globals.getSettings().getBooleanSetting(Settings.SELF_MODIFYING_CODE_ENABLED)) {
                val oldStatement = getStatementNoNotify(address)
                if (oldStatement != null) {
                    oldValue = oldStatement.binaryStatement
                }
                setStatement(address, ProgramStatement(value, address))
            } else {
                throw AddressErrorException(
                    "Cannot write directly to text segment!",
                    Exceptions.ADDRESS_EXCEPTION_STORE, address
                )
            }
        } else if (address >= memoryMapBaseAddress && address < memoryMapLimitAddress) {
            // memory mapped I/O.
            relativeByteAddress = address - memoryMapBaseAddress
            oldValue = storeBytesInTable(memoryMapBlockTable, relativeByteAddress, length, value)
        } else if (inKernelDataSegment(address)) {
            // in kernel data segment.  Will write one byte at a time, w/o regard to boundaries.
            relativeByteAddress = address - kernelDataBaseAddress // relative to data segment start, in bytes
            oldValue = storeBytesInTable(kernelDataBlockTable, relativeByteAddress, length, value)
        } else if (inKernelTextSegment(address)) {
            // DEVELOPER: PLEASE USE setStatement() TO WRITE TO KERNEL TEXT SEGMENT...
            throw AddressErrorException(
                "DEVELOPER: You must use setStatement() to write to kernel text segment!",
                Exceptions.ADDRESS_EXCEPTION_STORE, address
            )
        } else {
            // falls outside Mars addressing range
            throw AddressErrorException(
                "address out of range ",
                Exceptions.ADDRESS_EXCEPTION_STORE, address
            )
        }
        notifyAnyObservers(AccessNotice.WRITE, address, length, value)
        return oldValue
    }

    /**
     * Starting at the given word address, write the given value over 4 bytes (a word).
     * It must be written as is, without adjusting for byte order (little vs big endian).
     * Address must be word-aligned.
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored starting at that address.
     * @return old value that was replaced by the set operation.
     * @throws AddressErrorException If address is not on word boundary.
     */
    @Throws(AddressErrorException::class)
    fun setRawWord(address: Int, value: Int): Int {
        val relative: Int
        var oldValue = 0
        if (address % WORD_LENGTH_BYTES != 0) {
            throw AddressErrorException(
                "store address not aligned on word boundary ",
                Exceptions.ADDRESS_EXCEPTION_STORE, address
            )
        }
        if (inDataSegment(address)) {
            // in data segment
            relative = address - dataSegmentBaseAddress shr 2 // convert byte address to words
            oldValue = storeWordInTable(dataBlockTable, relative, value)
        } else if (address > stackLimitAddress && address <= stackBaseAddress) {
            // in stack.  Handle similarly to data segment write, except relative
            // address calculated "backward" because stack addresses grow down from base.
            relative = stackBaseAddress - address shr 2 // convert byte address to words
            oldValue = storeWordInTable(stackBlockTable, relative, value)
        } else if (inTextSegment(address)) {
            // Burch Mod (Jan 2013): replace throw with call to setStatement
            // DPS adaptation 5-Jul-2013: either throw or call, depending on setting
            if (Globals.getSettings().getBooleanSetting(Settings.SELF_MODIFYING_CODE_ENABLED)) {
                val oldStatement = getStatementNoNotify(address)
                if (oldStatement != null) {
                    oldValue = oldStatement.binaryStatement
                }
                setStatement(address, ProgramStatement(value, address))
            } else {
                throw AddressErrorException(
                    "Cannot write directly to text segment!",
                    Exceptions.ADDRESS_EXCEPTION_STORE, address
                )
            }
        } else if (address >= memoryMapBaseAddress && address < memoryMapLimitAddress) {
            // memory mapped I/O.
            relative = address - memoryMapBaseAddress shr 2 // convert byte address to word
            oldValue = storeWordInTable(memoryMapBlockTable, relative, value)
        } else if (inKernelDataSegment(address)) {
            // in data segment
            relative = address - kernelDataBaseAddress shr 2 // convert byte address to words
            oldValue = storeWordInTable(kernelDataBlockTable, relative, value)
        } else if (inKernelTextSegment(address)) {
            // DEVELOPER: PLEASE USE setStatement() TO WRITE TO KERNEL TEXT SEGMENT...
            throw AddressErrorException(
                "DEVELOPER: You must use setStatement() to write to kernel text segment!",
                Exceptions.ADDRESS_EXCEPTION_STORE, address
            )
        } else {
            // falls outside Mars addressing range
            throw AddressErrorException(
                "store address out of range ",
                Exceptions.ADDRESS_EXCEPTION_STORE, address
            )
        }
        notifyAnyObservers(AccessNotice.WRITE, address, WORD_LENGTH_BYTES, value)
        if (Globals.getSettings().backSteppingEnabled) {
            Globals.program.backStepper.addMemoryRestoreRawWord(address, oldValue)
        }
        return oldValue
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Starting at the given word address, write the given value over 4 bytes (a word).
     * The address must be word-aligned.
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored starting at that address.
     * @return old value that was replaced by setWord operation.
     * @throws AddressErrorException If address is not on word boundary.
     */
    @Throws(AddressErrorException::class)
    fun setWord(address: Int, value: Int): Int {
        if (address % WORD_LENGTH_BYTES != 0) {
            throw AddressErrorException(
                "store address not aligned on word boundary ",
                Exceptions.ADDRESS_EXCEPTION_STORE, address
            )
        }
        return if (Globals.getSettings().backSteppingEnabled) Globals.program.backStepper.addMemoryRestoreWord(
            address,
            set(address, value, WORD_LENGTH_BYTES)
        ) else set(address, value, WORD_LENGTH_BYTES)
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Starting at the given halfword address, write the lower 16 bits of given value
     * into 2 bytes (a halfword).
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored starting at that address.  Only low order 16 bits used.
     * @return old value that was replaced by setHalf operation.
     * @throws AddressErrorException If address is not on halfword boundary.
     */
    @Throws(AddressErrorException::class)
    fun setHalf(address: Int, value: Int): Int {
        if (address % 2 != 0) {
            throw AddressErrorException(
                "store address not aligned on halfword boundary ",
                Exceptions.ADDRESS_EXCEPTION_STORE, address
            )
        }
        return if (Globals.getSettings().backSteppingEnabled) Globals.program.backStepper.addMemoryRestoreHalf(
            address,
            set(address, value, 2)
        ) else set(address, value, 2)
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Writes low order 8 bits of given value into specified Memory byte.
     *
     * @param address Address of Memory byte to be set.
     * @param value   Value to be stored at that address.  Only low order 8 bits used.
     * @return old value that was replaced by setByte operation.
     */
    @Throws(AddressErrorException::class)
    fun setByte(address: Int, value: Int): Int {
        return if (Globals.getSettings().backSteppingEnabled) Globals.program.backStepper.addMemoryRestoreByte(
            address,
            set(address, value, 1)
        ) else set(address, value, 1)
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Writes 64 bit double value starting at specified Memory address.  Note that
     * high-order 32 bits are stored in higher (second) memory word regardless
     * of "endianness".
     *
     * @param address Starting address of Memory address to be set.
     * @param value   Value to be stored at that address.
     * @return old value that was replaced by setDouble operation.
     */
    @Throws(AddressErrorException::class)
    fun setDouble(address: Int, value: Double): Double {
        val oldHighOrder: Int
        val oldLowOrder: Int
        val longValue = java.lang.Double.doubleToLongBits(value)
        oldHighOrder = set(address + 4, Binary.highOrderLongToInt(longValue), 4)
        oldLowOrder = set(address, Binary.lowOrderLongToInt(longValue), 4)
        return java.lang.Double.longBitsToDouble(Binary.twoIntsToLong(oldHighOrder, oldLowOrder))
    }
    ////////////////////////////////////////////////////////////////////////////////
    /**
     * Stores ProgramStatement in Text Segment.
     *
     * @param address   Starting address of Memory address to be set.  Must be word boundary.
     * @param statement Machine code to be stored starting at that address -- for simulation
     * purposes, actually stores reference to ProgramStatement instead of 32-bit machine code.
     * @throws AddressErrorException If address is not on word boundary or is outside Text Segment.
     * @see ProgramStatement
     */
    @Throws(AddressErrorException::class)
    fun setStatement(address: Int, statement: ProgramStatement) {
        if (address % 4 != 0 || !(inTextSegment(address) || inKernelTextSegment(address))) {
            throw AddressErrorException(
                "store address to text segment out of range or not aligned to word boundary ",
                Exceptions.ADDRESS_EXCEPTION_STORE, address
            )
        }
        if (Globals.debug) println("memory[" + address + "] set to " + statement.binaryStatement)
        if (inTextSegment(address)) {
            storeProgramStatement(address, statement, textBaseAddress, textBlockTable)
        } else {
            storeProgramStatement(address, statement, kernelTextBaseAddress, kernelTextBlockTable)
        }
    }
    ////////////////////////////////////////////////////////////////////////////////
    /**
     * Starting at the given word address, read the given number of bytes (max 4).
     * This one does not check for word boundaries, and copies one byte at a time.
     * If length == 1, puts value in low order byte.  If 2, puts into low order half-word.
     *
     * @param address Starting address of Memory address to be read.
     * @param length  Number of bytes to be read.
     * @return Value stored starting at that address.
     */
    @Throws(AddressErrorException::class)
    operator fun get(address: Int, length: Int): Int {
        return get(address, length, true)
    }

    //////////
    // Does the real work, but includes option to NOT notify observers.
    @Throws(AddressErrorException::class)
    private operator fun get(address: Int, length: Int, notify: Boolean): Int {
        val value: Int
        val relativeByteAddress: Int
        if (inDataSegment(address)) {
            // in data segment.  Will read one byte at a time, w/o regard to boundaries.
            relativeByteAddress = address - dataSegmentBaseAddress // relative to data segment start, in bytes
            value = fetchBytesFromTable(dataBlockTable, relativeByteAddress, length)
        } else if (address > stackLimitAddress && address <= stackBaseAddress) {
            // in stack. Similar to data, except relative address computed "backward"
            relativeByteAddress = stackBaseAddress - address
            value = fetchBytesFromTable(stackBlockTable, relativeByteAddress, length)
        } else if (address >= memoryMapBaseAddress && address < memoryMapLimitAddress) {
            // memory mapped I/O.
            relativeByteAddress = address - memoryMapBaseAddress
            value = fetchBytesFromTable(memoryMapBlockTable, relativeByteAddress, length)
        } else if (inTextSegment(address)) {
            // Burch Mod (Jan 2013): replace throw with calls to getStatementNoNotify & getBinaryStatement
            // DPS adaptation 5-Jul-2013: either throw or call, depending on setting
            value = if (Globals.getSettings().getBooleanSetting(Settings.SELF_MODIFYING_CODE_ENABLED)) {
                val stmt = getStatementNoNotify(address)
                stmt?.binaryStatement ?: 0
            } else {
                throw AddressErrorException(
                    "Cannot read directly from text segment!",
                    Exceptions.ADDRESS_EXCEPTION_LOAD, address
                )
            }
        } else if (inKernelDataSegment(address)) {
            // in kernel data segment.  Will read one byte at a time, w/o regard to boundaries.
            relativeByteAddress = address - kernelDataBaseAddress // relative to data segment start, in bytes
            value = fetchBytesFromTable(kernelDataBlockTable, relativeByteAddress, length)
        } else if (inKernelTextSegment(address)) {
            // DEVELOPER: PLEASE USE getStatement() TO READ FROM KERNEL TEXT SEGMENT...
            throw AddressErrorException(
                "DEVELOPER: You must use getStatement() to read from kernel text segment!",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        } else {
            // falls outside Mars addressing range
            throw AddressErrorException(
                "address out of range ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        if (notify) notifyAnyObservers(AccessNotice.READ, address, length, value)
        return value
    }

    /**
     * Starting at the given word address, read a 4 byte word as an int.
     * It transfers the 32 bit value "raw" as stored in memory, and does not adjust
     * for byte order (big or little endian).  Address must be word-aligned.
     *
     * @param address Starting address of word to be read.
     * @return Word (4-byte value) stored starting at that address.
     * @throws AddressErrorException If address is not on word boundary.
     */
    // Note: the logic here is repeated in getRawWordOrNull() below.  Logic is
    // simplified by having this method just call getRawWordOrNull() then
    // return either the int of its return value, or 0 if it returns null.
    // Doing so would be detrimental to simulation runtime performance, so
    // I decided to keep the duplicate logic.
    @Throws(AddressErrorException::class)
    fun getRawWord(address: Int): Int {
        val value: Int
        val relative: Int
        if (address % WORD_LENGTH_BYTES != 0) {
            throw AddressErrorException(
                "address for fetch not aligned on word boundary",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        if (inDataSegment(address)) {
            // in data segment
            relative = address - dataSegmentBaseAddress shr 2 // convert byte address to words
            value = fetchWordFromTable(dataBlockTable, relative)
        } else if (address > stackLimitAddress && address <= stackBaseAddress) {
            // in stack. Similar to data, except relative address computed "backward"
            relative = stackBaseAddress - address shr 2 // convert byte address to words
            value = fetchWordFromTable(stackBlockTable, relative)
        } else if (address >= memoryMapBaseAddress && address < memoryMapLimitAddress) {
            // memory mapped I/O.
            relative = address - memoryMapBaseAddress shr 2
            value = fetchWordFromTable(memoryMapBlockTable, relative)
        } else if (inTextSegment(address)) {
            // Burch Mod (Jan 2013): replace throw with calls to getStatementNoNotify & getBinaryStatement
            // DPS adaptation 5-Jul-2013: either throw or call, depending on setting
            value = if (Globals.getSettings().getBooleanSetting(Settings.SELF_MODIFYING_CODE_ENABLED)) {
                val stmt = getStatementNoNotify(address)
                stmt?.binaryStatement ?: 0
            } else {
                throw AddressErrorException(
                    "Cannot read directly from text segment!",
                    Exceptions.ADDRESS_EXCEPTION_LOAD, address
                )
            }
        } else if (inKernelDataSegment(address)) {
            // in kernel data segment
            relative = address - kernelDataBaseAddress shr 2 // convert byte address to words
            value = fetchWordFromTable(kernelDataBlockTable, relative)
        } else if (inKernelTextSegment(address)) {
            // DEVELOPER: PLEASE USE getStatement() TO READ FROM KERNEL TEXT SEGMENT...
            throw AddressErrorException(
                "DEVELOPER: You must use getStatement() to read from kernel text segment!",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        } else {
            // falls outside Mars addressing range
            throw AddressErrorException(
                "address out of range ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        notifyAnyObservers(AccessNotice.READ, address, WORD_LENGTH_BYTES, value)
        return value
    }

    /**
     * Starting at the given word address, read a 4 byte word as an int and return Integer.
     * It transfers the 32 bit value "raw" as stored in memory, and does not adjust
     * for byte order (big or little endian).  Address must be word-aligned.
     *
     *
     * Returns null if reading from text segment and there is no instruction at the
     * requested address. Returns null if reading from data segment and this is the
     * first reference to the MARS 4K memory allocation block (i.e., an array to
     * hold the memory has not been allocated).
     *
     *
     * This method was developed by Greg Giberling of UC Berkeley to support the memory
     * dump feature that he implemented in Fall 2007.
     *
     * @param address Starting address of word to be read.
     * @return Word (4-byte value) stored starting at that address as an Integer.  Conditions
     * that cause return value null are described above.
     * @throws AddressErrorException If address is not on word boundary.
     */
    // See note above, with getRawWord(), concerning duplicated logic.
    @Throws(AddressErrorException::class)
    fun getRawWordOrNull(address: Int): Int? {
        var value: Int? = null
        val relative: Int
        if (address % WORD_LENGTH_BYTES != 0) {
            throw AddressErrorException(
                "address for fetch not aligned on word boundary",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        if (inDataSegment(address)) {
            // in data segment
            relative = address - dataSegmentBaseAddress shr 2 // convert byte address to words
            value = fetchWordOrNullFromTable(dataBlockTable, relative)
        } else if (address > stackLimitAddress && address <= stackBaseAddress) {
            // in stack. Similar to data, except relative address computed "backward"
            relative = stackBaseAddress - address shr 2 // convert byte address to words
            value = fetchWordOrNullFromTable(stackBlockTable, relative)
        } else if (inTextSegment(address) || inKernelTextSegment(address)) {
            try {
                value =
                    if (getStatementNoNotify(address) == null) null else getStatementNoNotify(address)!!.binaryStatement
            } catch (ignored: AddressErrorException) {
            }
        } else if (inKernelDataSegment(address)) {
            // in kernel data segment
            relative = address - kernelDataBaseAddress shr 2 // convert byte address to words
            value = fetchWordOrNullFromTable(kernelDataBlockTable, relative)
        } else {
            // falls outside Mars addressing range
            throw AddressErrorException("address out of range ", Exceptions.ADDRESS_EXCEPTION_LOAD, address)
        }
        // Do not notify observers.  This read operation is initiated by the
        // dump feature, not the executing MIPS program.
        return value
    }

    /**
     * Look for first "null" memory value in an address range.  For text segment (binary code), this
     * represents a word that does not contain an instruction.  Normally use this to find the end of
     * the program.  For data segment, this represents the first block of simulated memory (block length
     * currently 4K words) that has not been referenced by an assembled/executing program.
     *
     * @param baseAddress  lowest MIPS address to be searched; the starting point
     * @param limitAddress highest MIPS address to be searched
     * @return lowest address within specified range that contains "null" value as described above.
     * @throws AddressErrorException if the base address is not on a word boundary
     */
    @Throws(AddressErrorException::class)
    fun getAddressOfFirstNull(baseAddress: Int, limitAddress: Int): Int {
        var address = baseAddress
        while (address < limitAddress) {
            if (getRawWordOrNull(address) == null) {
                break
            }
            address += WORD_LENGTH_BYTES
        }
        return address
    }

    /**
     * Starting at the given word address, read a 4 byte word as an int.
     * Does not use "get()"; we can do it faster here knowing we're working only
     * with full words.
     *
     * @param address Starting address of word to be read.
     * @return Word (4-byte value) stored starting at that address.
     * @throws AddressErrorException If address is not on word boundary.
     */
    @Throws(AddressErrorException::class)
    fun getWord(address: Int): Int {
        if (address % WORD_LENGTH_BYTES != 0) {
            throw AddressErrorException(
                "fetch address not aligned on word boundary ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        return get(address, WORD_LENGTH_BYTES, true)
    }

    /**
     * Starting at the given word address, read a 4 byte word as an int.
     * Does not use "get()"; we can do it faster here knowing we're working only
     * with full words.  Observers are NOT notified.
     *
     * @param address Starting address of word to be read.
     * @return Word (4-byte value) stored starting at that address.
     * @throws AddressErrorException If address is not on word boundary.
     */
    @Throws(AddressErrorException::class)
    fun getWordNoNotify(address: Int): Int {
        if (address % WORD_LENGTH_BYTES != 0) {
            throw AddressErrorException(
                "fetch address not aligned on word boundary ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        return get(address, WORD_LENGTH_BYTES, false)
    }

    /**
     * Starting at the given word address, read a 2 byte word into lower 16 bits of int.
     *
     * @param address Starting address of word to be read.
     * @return Halfword (2-byte value) stored starting at that address, stored in lower 16 bits.
     * @throws AddressErrorException If address is not on halfword boundary.
     */
    @Throws(AddressErrorException::class)
    fun getHalf(address: Int): Int {
        if (address % 2 != 0) {
            throw AddressErrorException(
                "fetch address not aligned on halfword boundary ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        return get(address, 2)
    }

    /**
     * Reads specified Memory byte into low order 8 bits of int.
     *
     * @param address Address of Memory byte to be read.
     * @return Value stored at that address.  Only low order 8 bits used.
     */
    @Throws(AddressErrorException::class)
    fun getByte(address: Int): Int {
        return get(address, 1)
    }

    /**
     * Gets ProgramStatement from Text Segment.
     *
     * @param address Starting address of Memory address to be read.  Must be word boundary.
     * @return reference to ProgramStatement object associated with that address, or null if none.
     * @throws AddressErrorException If address is not on word boundary or is outside Text Segment.
     * @see ProgramStatement
     */
    @Throws(AddressErrorException::class)
    fun getStatement(address: Int): ProgramStatement? {
        return getStatement(address, true)
        /*
         if (address % 4 != 0 || !(inTextSegment(address) || inKernelTextSegment(address))) {
            throw new AddressErrorException(
               "fetch address for text segment out of range or not aligned to word boundary ",
               Exceptions.ADDRESS_EXCEPTION_LOAD, address);
         }
         if (inTextSegment(address)) {
            return readProgramStatement(address, textBaseAddress, textBlockTable, true);
         }
         else {
            return readProgramStatement(address, kernelTextBaseAddress, kernelTextBlockTable,true);
         }
      	*/
    }
    ///////////////////////////////////////////////////////////////////////////
    //  ALL THE OBSERVABLE STUFF GOES HERE.  FOR COMPATIBILITY, Memory IS STILL
    //  EXTENDING OBSERVABLE, BUT WILL NOT USE INHERITED METHODS.  WILL INSTEAD
    //  USE A COLLECTION OF MemoryObserver OBJECTS, EACH OF WHICH IS COMBINATION
    //  OF AN OBSERVER WITH AN ADDRESS RANGE.
    /**
     * Gets ProgramStatement from Text Segment without notifying observers.
     *
     * @param address Starting address of Memory address to be read.  Must be word boundary.
     * @return reference to ProgramStatement object associated with that address, or null if none.
     * @throws AddressErrorException If address is not on word boundary or is outside Text Segment.
     * @see ProgramStatement
     */
    @Throws(AddressErrorException::class)
    fun getStatementNoNotify(address: Int): ProgramStatement? {
        return getStatement(address, false)
        /*
         if (address % 4 != 0 || !(inTextSegment(address) || inKernelTextSegment(address))) {
            throw new AddressErrorException(
               "fetch address for text segment out of range or not aligned to word boundary ",
               Exceptions.ADDRESS_EXCEPTION_LOAD, address);
         }
         if (inTextSegment(address)) {
            return readProgramStatement(address, textBaseAddress, textBlockTable, false);
         }
         else {
            return readProgramStatement(address, kernelTextBaseAddress, kernelTextBlockTable, false);
         }
      	*/
    }

    @Throws(AddressErrorException::class)
    private fun getStatement(address: Int, notify: Boolean): ProgramStatement? {
        if (isUnaligned(address)) {
            throw AddressErrorException(
                "fetch address for text segment not aligned to word boundary ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        if (!Globals.getSettings().getBooleanSetting(Settings.SELF_MODIFYING_CODE_ENABLED)
            && !(inTextSegment(address) || inKernelTextSegment(address))
        ) {
            throw AddressErrorException(
                "fetch address for text segment out of range ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, address
            )
        }
        return if (inTextSegment(address)) readProgramStatement(
            address,
            textBaseAddress,
            textBlockTable,
            notify
        ) else if (inKernelTextSegment(address)) readProgramStatement(
            address,
            kernelTextBaseAddress,
            kernelTextBlockTable,
            notify
        ) else ProgramStatement(get(address, WORD_LENGTH_BYTES), address)
    }

    /**
     * Method to accept registration from observer for any memory address.  Overrides
     * inherited method.  Note to observers: this class delegates Observable operations
     * so notices will come from the delegate, not the memory object.
     *
     * @param obs the observer
     */
    override fun addObserver(obs: Observer) {
        try {  // split so start address always >= end address
            this.addObserver(obs, 0, 0x7ffffffc)
            this.addObserver(obs, -0x80000000, -0x4)
        } catch (aee: AddressErrorException) {
            println("Internal Error in Memory.addObserver: $aee")
        }
    }

    /**
     * Method to accept registration from observer for specific address.  This includes
     * the memory word starting at the given address. Note to observers: this class delegates Observable operations
     * so notices will come from the delegate, not the memory object.
     *
     * @param obs  the observer
     * @param addr the memory address which must be on word boundary
     */
    @Throws(AddressErrorException::class)
    fun addObserver(obs: Observer?, addr: Int) {
        this.addObserver(obs, addr, addr)
    }

    /**
     * Method to accept registration from observer for specific address range.  The
     * last byte included in the address range is the last byte of the word specified
     * by the ending address. Note to observers: this class delegates Observable operations
     * so notices will come from the delegate, not the memory object.
     *
     * @param obs       the observer
     * @param startAddr the low end of memory address range, must be on word boundary
     * @param endAddr   the high end of memory address range, must be on word boundary
     */
    @Throws(AddressErrorException::class)
    fun addObserver(obs: Observer?, startAddr: Int, endAddr: Int) {
        if (startAddr % WORD_LENGTH_BYTES != 0) {
            throw AddressErrorException(
                "address not aligned on word boundary ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, startAddr
            )
        }
        if (endAddr != startAddr && endAddr % WORD_LENGTH_BYTES != 0) {
            throw AddressErrorException(
                "address not aligned on word boundary ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, startAddr
            )
        }
        // upper half of address space (above 0x7fffffff) has sign bit 1 thus is seen as
        // negative.
        if (startAddr >= 0 && endAddr < 0) {
            throw AddressErrorException(
                "range cannot cross 0x8000000; please split it up",
                Exceptions.ADDRESS_EXCEPTION_LOAD, startAddr
            )
        }
        if (endAddr < startAddr) {
            throw AddressErrorException(
                "end address of range < start address of range ",
                Exceptions.ADDRESS_EXCEPTION_LOAD, startAddr
            )
        }
        observables.add(MemoryObservable(obs, startAddr, endAddr))
    }

    /**
     * Return number of observers
     */
    override fun countObservers(): Int {
        return observables.size
    }

    /**
     * Remove specified memory observers
     *
     * @param obs Observer to be removed
     */
    override fun deleteObserver(obs: Observer) {
        for (observable in observables) {
            observable.deleteObserver(obs)
        }
    }

    /**
     * Remove all memory observers
     */
    override fun deleteObservers() {
        // just drop the collection
        observables = newMemoryObserversCollection
    }

    /**
     * Overridden to be unavailable.  The notice that an Observer
     * receives does not come from the memory object itself, but
     * instead from a delegate.
     *
     * @throws UnsupportedOperationException
     */
    override fun notifyObservers() {
        throw UnsupportedOperationException()
    }

    /**
     * Overridden to be unavailable.  The notice that an Observer
     * receives does not come from the memory object itself, but
     * instead from a delegate.
     *
     * @throws UnsupportedOperationException
     */
    override fun notifyObservers(obj: Any) {
        throw UnsupportedOperationException()
    }

    // Vectors are thread-safe
    private val newMemoryObserversCollection: MutableCollection<MemoryObservable>
        get() = Vector() // Vectors are thread-safe

    /*********************************  THE HELPERS   */ ////////////////////////////////////////////////////////////////////////////////
    //
    // Method to notify any observers of memory operation that has just occurred.
    //
    // The "|| Globals.getGui()==null" is a hack added 19 July 2012 DPS.  IF MIPS simulation
    // is from command mode, Globals.program is null but still want ability to observe.
    private fun notifyAnyObservers(type: Int, address: Int, length: Int, value: Int) {
        if ((Globals.program != null || Globals.getGui() == null) && observables.size > 0) {
            val it: Iterator<MemoryObservable> = observables.iterator()
            var mo: MemoryObservable
            while (it.hasNext()) {
                mo = it.next()
                if (mo.match(address)) {
                    mo.notifyObserver(MemoryAccessNotice(type, address, length, value))
                }
            }
        }
    }

    private fun storeBytesInTable(
        blockTable: Array<IntArray?>,
        relativeByteAddress: Int, length: Int, value: Int
    ): Int {
        return storeOrFetchBytesInTable(blockTable, relativeByteAddress, length, value, STORE)
    }

    private fun fetchBytesFromTable(blockTable: Array<IntArray?>, relativeByteAddress: Int, length: Int): Int {
        return storeOrFetchBytesInTable(blockTable, relativeByteAddress, length, 0, FETCH)
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Helper method to fetch 1, 2 or 4 byte value from table that represents MIPS
    // memory.  Originally used just for data segment, but now also used for stack.
    // Both use different tables but same storage method and same table size
    // and block size.
    //
    ////////////////////////////////////////////////////////////////////////////////
    //
    // The helper's helper.  Works for either storing or fetching, little or big endian.
    // When storing/fetching bytes, most of the work is calculating the correct array element(s)
    // and element byte(s).  This method performs either store or fetch, as directed by its
    // client using STORE or FETCH in last arg.
    // Modified 29 Dec 2005 to return old value of replaced bytes, for STORE.
    //
    @Synchronized
    private fun storeOrFetchBytesInTable(
        blockTable: Array<IntArray?>,
        relativeByteAddress: Int, length: Int, value: Int, op: Boolean
    ): Int {
        var relativeByteAddress = relativeByteAddress
        var value = value
        var relativeWordAddress: Int
        var block: Int
        var offset: Int
        var bytePositionInMemory: Int
        var oldValue = 0 // for STORE, return old values of replaced bytes
        val loopStopper = 3 - length
        // IF added DPS 22-Dec-2008. NOTE: has NOT been tested with Big-Endian.
        // Fix provided by Saul Spatz; comments that follow are his.
        // If address in stack segment is 4k + m, with 0 < m < 4, then the
        // relativeByteAddress we want is stackBaseAddress - 4k + m, but the
        // address actually passed in is stackBaseAddress - (4k + m), so we
        // need to add 2m.  Because of the change in sign, we get the
        // expression 4-delta below in place of m.
        if (blockTable.contentEquals(stackBlockTable)) {
            val delta = relativeByteAddress % 4
            if (delta != 0) {
                relativeByteAddress += 4 - delta shl 1
            }
        }
        var bytePositionInValue = 3
        while (bytePositionInValue > loopStopper) {
            bytePositionInMemory = relativeByteAddress % 4
            relativeWordAddress = relativeByteAddress shr 2
            block = relativeWordAddress / BLOCK_LENGTH_WORDS // Block number
            offset = relativeWordAddress % BLOCK_LENGTH_WORDS // Word within that block
            if (blockTable[block] == null) {
                if (op == STORE) blockTable[block] = IntArray(BLOCK_LENGTH_WORDS) else return 0
            }
            if (byteOrder == LITTLE_ENDIAN) bytePositionInMemory = 3 - bytePositionInMemory
            if (op == STORE) {
                oldValue = replaceByte(
                    blockTable[block]!![offset], bytePositionInMemory,
                    oldValue, bytePositionInValue
                )
                blockTable[block]!![offset] = replaceByte(
                    value, bytePositionInValue,
                    blockTable[block]!![offset], bytePositionInMemory
                )
            } else { // op == FETCH
                value = replaceByte(
                    blockTable[block]!![offset], bytePositionInMemory,
                    value, bytePositionInValue
                )
            }
            relativeByteAddress++
            bytePositionInValue--
        }
        return if (op == STORE) oldValue else value
    }

    @Synchronized
    private fun storeWordInTable(blockTable: Array<IntArray?>, relative: Int, value: Int): Int {
        val block = relative / BLOCK_LENGTH_WORDS
        val offset = relative % BLOCK_LENGTH_WORDS
        if (blockTable[block] == null) {
            // First time writing to this block, so allocate the space.
            blockTable[block] = IntArray(BLOCK_LENGTH_WORDS)
        }
        val oldValue = blockTable[block]!![offset]
        blockTable[block]!![offset] = value
        return oldValue
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Helper method to store 4 byte value in table that represents MIPS memory.
    // Originally used just for data segment, but now also used for stack.
    // Both use different tables but same storage method and same table size
    // and block size.  Assumes address is word aligned, no endian processing.
    // Modified 29 Dec 2005 to return overwritten value.
    @Synchronized
    private fun fetchWordFromTable(blockTable: Array<IntArray?>, relative: Int): Int {
        val block = relative / BLOCK_LENGTH_WORDS
        val offset = relative % BLOCK_LENGTH_WORDS
        return if (blockTable[block] != null) {
            blockTable[block]!![offset]
        } else 0
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Helper method to fetch 4 byte value from table that represents MIPS memory.
    // Originally used just for data segment, but now also used for stack.
    // Both use different tables but same storage method and same table size
    // and block size.  Assumes word alignment, no endian processing.
    //
    @Synchronized
    private fun fetchWordOrNullFromTable(blockTable: Array<IntArray?>, relative: Int): Int? {
        val block = relative / BLOCK_LENGTH_WORDS
        val offset = relative % BLOCK_LENGTH_WORDS
        return blockTable[block]?.get(offset)
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Helper method to fetch 4 byte value from table that represents MIPS memory.
    // Originally used just for data segment, but now also used for stack.
    // Both use different tables but same storage method and same table size
    // and block size.  Assumes word alignment, no endian processing.
    //
    // This differs from "fetchWordFromTable()" in that it returns an Integer and
    // returns null instead of 0 if the 4K table has not been allocated.  Developed
    // by Greg Gibeling of UC Berkeley, fall 2007.
    //
    ////////////////////////////////////////////////////////////////////////////////////
    // Returns result of substituting specified byte of source value into specified byte
    // of destination value. Byte positions are 0-1-2-3, listed from most to least
    // significant.  No endian issues.  This is a private helper method used by get() & set().
    private fun replaceByte(sourceValue: Int, bytePosInSource: Int, destValue: Int, bytePosInDest: Int): Int {
        return ( // Set source byte value into destination byte position; set other 24 bits to 0's...
                (sourceValue shr 24 - (bytePosInSource shl 3) and 0xFF
                        shl 24 - (bytePosInDest shl 3)) // and bitwise-OR it with...
                        or  // Set 8 bits in destination byte position to 0's, other 24 bits are unchanged.
                        (destValue and (0xFF shl 24 - (bytePosInDest shl 3)).inv()))
    }

    ///////////////////////////////////////////////////////////////////////
    // Store a program statement at the given address.  Address has already been verified
    // as valid.  It may be either in user or kernel text segment, as specified by arguments.
    private fun storeProgramStatement(
        address: Int, statement: ProgramStatement,
        baseAddress: Int, blockTable: Array<Array<ProgramStatement?>?>
    ) {
        val relative = address - baseAddress shr 2 // convert byte address to words
        val block = relative / BLOCK_LENGTH_WORDS
        val offset = relative % BLOCK_LENGTH_WORDS
        if (block < TEXT_BLOCK_TABLE_LENGTH) {
            if (blockTable[block] == null) {
                // No instructions are stored in this block, so allocate the block.
                blockTable[block] = arrayOfNulls(BLOCK_LENGTH_WORDS)
            }
            blockTable[block]!![offset] = statement
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Read a program statement from the given address.  Address has already been verified
    // as valid.  It may be either in user or kernel text segment, as specified by arguments.
    // Returns associated ProgramStatement or null if none.
    // Last parameter controls whether or not observers will be notified.
    private fun readProgramStatement(
        address: Int,
        baseAddress: Int,
        blockTable: Array<Array<ProgramStatement?>?>,
        notify: Boolean
    ): ProgramStatement? {
        val relative = address - baseAddress shr 2 // convert byte address to words
        val block = relative / TEXT_BLOCK_LENGTH_WORDS
        val offset = relative % TEXT_BLOCK_LENGTH_WORDS
        if (block < TEXT_BLOCK_TABLE_LENGTH) {
            return if (blockTable[block] == null || blockTable[block]!![offset] == null) {
                // No instructions are stored in this block or offset.
                if (notify) notifyAnyObservers(AccessNotice.READ, address, Instruction.INSTRUCTION_LENGTH, 0)
                null
            } else {
                if (notify) notifyAnyObservers(
                    AccessNotice.READ,
                    address,
                    Instruction.INSTRUCTION_LENGTH,
                    blockTable[block]!![offset]!!.binaryStatement
                )
                blockTable[block]!![offset]
            }
        }
        if (notify) notifyAnyObservers(AccessNotice.READ, address, Instruction.INSTRUCTION_LENGTH, 0)
        return null
    }

    /////////////////////////////////////////////////////////////////////////
    // Private class whose objects will represent an observable-observer pair
    // for a given memory address or range.
    class MemoryObservable(obs: Observer?, private val lowAddress: Int, private val highAddress: Int) : Observable(),
        Comparable<MemoryObservable> {
        fun match(address: Int): Boolean {
            return address >= lowAddress && address <= highAddress - 1 + WORD_LENGTH_BYTES
        }

        fun notifyObserver(notice: MemoryAccessNotice?) {
            setChanged()
            this.notifyObservers(notice)
        }

        // Useful to have for future refactoring, if it actually becomes worthwhile to sort
        // these or put 'em in a tree (rather than sequential search through list).
        override fun compareTo(other: MemoryObservable): Int {
            if (lowAddress < other.lowAddress || lowAddress == other.lowAddress && highAddress < other.highAddress) {
                return -1
            }
            return if (lowAddress > other.lowAddress || highAddress > other.highAddress) {
                -1
            } else 0
            // they have to be equal at this point.
        }

        init {
            addObserver(obs)
        }
    }

    /**
     * MIPS word length in bytes.
     */
    // NOTE:  Much of the code is hardwired for 4 byte words.  Refactoring this is low priority.
    const val WORD_LENGTH_BYTES = 4

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Helper method to store 1, 2 or 4 byte value in table that represents MIPS
    // memory. Originally used just for data segment, but now also used for stack.
    // Both use different tables but same storage method and same table size
    // and block size.
    // Modified 29 Dec 2005 to return old value of replaced bytes.
    //
    private const val STORE = true
    private const val FETCH = false

    /**
     * base address for (user) text segment: 0x00400000
     */
    @JvmField
    var textBaseAddress = MemoryConfiguration.Default.textBaseAddress.toInt()

    /**
     * base address for (user) data segment: 0x10000000
     */
    @JvmField
    var dataSegmentBaseAddress = MemoryConfiguration.Default.dataSegmentBaseAddress.toInt()

    /**
     * base address for .extern directive: 0x10000000
     */
    @JvmField
    var externBaseAddress = MemoryConfiguration.Default.externBaseAddress.toInt()

    /**
     * base address for storing globals
     */
    @JvmField
    var globalPointer = MemoryConfiguration.Default.globalPointer.toInt()

    /**
     * base address for storage of non-global static data in data segment: 0x10010000 (from SPIM)
     */
    @JvmField
    var dataBaseAddress = MemoryConfiguration.Default.dataBaseAddress.toInt()

    /**
     * base address for heap: 0x10040000 (I think from SPIM not MIPS)
     */
    @JvmField
    var heapBaseAddress = MemoryConfiguration.Default.heapBaseAddress.toInt()

    /**
     * starting address for stack: 0x7fffeffc (this is from SPIM not MIPS)
     */
    @JvmField
    var stackPointer = MemoryConfiguration.Default.stackPointer.toInt()

    /**
     * base address for stack: 0x7ffffffc (this is mine - start of highest word below kernel space)
     */
    @JvmField
    var stackBaseAddress = MemoryConfiguration.Default.stackBaseAddress.toInt()

    /**
     * highest address accessible in user (not kernel) mode.
     */
    @JvmField
    var userHighAddress = MemoryConfiguration.Default.userSpaceHighAddress.toInt()

    /**
     * kernel boundary.  Only OS can access this or higher address
     */
    var kernelBaseAddress = MemoryConfiguration.Default.kernelSpaceBaseAddress.toInt()
    // Memory will maintain a collection of observables.  Each one is associated
    // with a specific memory address or address range, and each will have at least
    // one observer registered with it.  When memory access is made, make sure only
    // observables associated with that address send notices to their observers.
    // This assures that observers are not bombarded with notices from memory
    // addresses they do not care about.
    //
    // Would like a tree-like implementation, but that is complicated by this fact:
    // key for insertion into the tree would be based on Comparable using both low
    // and high end of address range, but retrieval from the tree has to be based
    // on target address being ANYWHERE IN THE RANGE (not an exact key match).
    /**
     * base address for kernel text segment: 0x80000000
     */
    @JvmField
    var kernelTextBaseAddress = MemoryConfiguration.Default.kTextBaseAddress.toInt()
    // The data segment is allocated in blocks of 1024 ints (4096 bytes).  Each block is
    // referenced by a "block table" entry, and the table has 1024 entries.  The capacity
    // is thus 1024 entries * 4096 bytes = 4 MB.  Should be enough to cover most
    // programs!!  Beyond that it would go to an "indirect" block (similar to Unix i-nodes),
    // which is not implemented.
    //
    // Although this scheme is an array of arrays, it is relatively space-efficient since
    // only the table is created initially. A 4096-byte block is not allocated until a value
    // is written to an address within it.  Thus most small programs will use only 8K bytes
    // of space (the table plus one block).  The index into both arrays is easily computed
    // from the address; access time is constant.
    //
    // SPIM stores statically allocated data (following first .data directive) starting
    // at location 0x10010000.  This is the first Data Segment word beyond the reach of $gp
    // used in conjunction with signed 16 bit immediate offset.  $gp has value 0x10008000
    // and with the signed 16 bit offset can reach from 0x10008000 - 0xFFFF = 0x10000000
    // (Data Segment base) to 0x10008000 + 0x7FFF = 0x1000FFFF (the byte preceding 0x10010000).
    //
    // Using my scheme, 0x10010000 falls at the beginning of the 17'th block -- table entry 16.
    // SPIM uses a heap base address of 0x10040000 which is not part of the MIPS specification.
    // (I don't have a reference for that offhand...)  Using my scheme, 0x10040000 falls at
    // the start of the 65'th block -- table entry 64.  That leaves (1024-64) * 4096 = 3,932,160
    // bytes of space available without going indirect.
    /**
     * starting address for exception handlers: 0x80000180
     */
    @JvmField
    var exceptionHandlerAddress = MemoryConfiguration.Default.exceptionBaseAddress.toInt()

    /**
     * base address for kernel data segment: 0x90000000
     */
    @JvmField
    var kernelDataBaseAddress = MemoryConfiguration.Default.kDataBaseAddress.toInt()

    /**
     * starting address for memory mapped I/O: 0xffff0000 (-65536)
     */
    @JvmField
    var memoryMapBaseAddress = MemoryConfiguration.Default.mmioBaseAddress.toInt()

    /**
     * highest address acessible in kernel mode.
     */
    @JvmField
    var kernelHighAddress = MemoryConfiguration.Default.kernelSpaceHighAddress.toInt()

    // The stack is modeled similarly to the data segment.  It cannot share the same
    // data structure because the stack base address is very large.  To store it in the
    // same data structure would require implementation of indirect blocks, which has not
    // been realized.  So the stack gets its own table of blocks using the same dimensions
    // and allocation scheme used for data segment.
    //
    // The other major difference is the stack grows DOWNWARD from its base address, not
    // upward.  I.e., the stack base is the largest stack address. This turns the whole
    // scheme for translating memory address to block-offset on its head!  The simplest
    // solution is to calculate relative address (offset from base) by subtracting the
    // desired address from the stack base address (rather than subtracting base address
    // from desired address).  Thus as the address gets smaller the offset gets larger.
    // Everything else works the same, so it shares some private helper methods with
    // data segment algorithms.
    var heapAddress = 0

    // Memory mapped I/O is simulated with a separate table using the same structure and
    // logic as data segment.  Memory is allocated in 4K byte blocks.  But since MMIO
    // address range is limited to 0xffff0000 to 0xfffffffc, there are only 64K bytes
    // total.  Thus there will be a maximum of 16 blocks, and I suspect never more than
    // one since only the first few addresses are typically used.  The only exception
    // may be a rogue program generating such addresses in a loop.  Note that the
    // MMIO addresses are interpreted by Java as negative numbers since it does not
    // have unsigned types.  As long as the absolute address is correctly translated
    // into a table offset, this is of no concern.
    @JvmField
    var dataSegmentLimitAddress = dataSegmentBaseAddress +
            BLOCK_LENGTH_WORDS * BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES

    @JvmField
    var textLimitAddress = textBaseAddress +
            TEXT_BLOCK_LENGTH_WORDS * TEXT_BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES

    // I use a similar scheme for storing instructions.  MIPS text segment ranges from
    // 0x00400000 all the way to data segment (0x10000000) a range of about 250 MB!  So
    // I'll provide table of blocks with similar capacity.  This differs from data segment
    // somewhat in that the block entries do not contain int's, but instead contain
    // references to ProgramStatement objects.
    var kernelDataSegmentLimitAddress = kernelDataBaseAddress +
            BLOCK_LENGTH_WORDS * BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES

    @JvmField
    var kernelTextLimitAddress = kernelTextBaseAddress +
            TEXT_BLOCK_LENGTH_WORDS * TEXT_BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES
    var stackLimitAddress = stackBaseAddress -
            BLOCK_LENGTH_WORDS * BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES
    var memoryMapLimitAddress = memoryMapBaseAddress +
            BLOCK_LENGTH_WORDS * MMIO_TABLE_LENGTH * WORD_LENGTH_BYTES
    // Set "top" address boundary to go with each "base" address.  This determines permissable
    // address range for user program.  Currently limit is 4MB, or 1024 * 1024 * 4 bytes based
    // on the table structures described above (except memory mapped IO, limited to 64KB by range).
    /**
     * Current setting for endian (default LITTLE_ENDIAN)
     */
    private const val byteOrder = LITTLE_ENDIAN

    /**
     * Sets current memory configuration for simulated MIPS.  Configuration is
     * collection of memory segment addresses. e.g. text segment starting at
     * address 0x00400000.  Configuration can be modified starting with MARS 3.7.
     */
    fun setConfiguration() {
        textBaseAddress = MemoryConfigurations.currentConfiguration.textBaseAddress.toInt() //0x00400000;
        dataSegmentBaseAddress =
            MemoryConfigurations.currentConfiguration.dataSegmentBaseAddress.toInt() //0x10000000;
        externBaseAddress = MemoryConfigurations.currentConfiguration.externBaseAddress.toInt() //0x10000000;
        globalPointer = MemoryConfigurations.currentConfiguration.globalPointer.toInt() //0x10008000;
        dataBaseAddress =
            MemoryConfigurations.currentConfiguration.dataBaseAddress.toInt() //0x10010000; // from SPIM not MIPS
        heapBaseAddress =
            MemoryConfigurations.currentConfiguration.heapBaseAddress.toInt() //0x10040000; // I think from SPIM not MIPS
        stackPointer = MemoryConfigurations.currentConfiguration.stackPointer.toInt() //0x7fffeffc;
        stackBaseAddress = MemoryConfigurations.currentConfiguration.stackBaseAddress.toInt() //0x7ffffffc;
        userHighAddress = MemoryConfigurations.currentConfiguration.userSpaceHighAddress.toInt() //0x7fffffff;
        kernelBaseAddress =
            MemoryConfigurations.currentConfiguration.kernelSpaceBaseAddress.toInt() //0x80000000;
        kernelTextBaseAddress =
            MemoryConfigurations.currentConfiguration.kTextBaseAddress.toInt() //0x80000000;
        exceptionHandlerAddress =
            MemoryConfigurations.currentConfiguration.exceptionBaseAddress.toInt() //0x80000180;
        kernelDataBaseAddress =
            MemoryConfigurations.currentConfiguration.kDataBaseAddress.toInt() //0x90000000;
        memoryMapBaseAddress = MemoryConfigurations.currentConfiguration.mmioBaseAddress.toInt() //0xffff0000;
        kernelHighAddress =
            MemoryConfigurations.currentConfiguration.kernelSpaceHighAddress.toInt() //0xffffffff;
        dataSegmentLimitAddress =
            MemoryConfigurations.currentConfiguration.dataSegmentLimitAddress.toInt()
                .coerceAtMost(dataSegmentBaseAddress + BLOCK_LENGTH_WORDS * BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES)
        textLimitAddress = Math.min(
            MemoryConfigurations.currentConfiguration.textLimitAddress.toInt(),
            textBaseAddress +
                    TEXT_BLOCK_LENGTH_WORDS * TEXT_BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES
        )
        kernelDataSegmentLimitAddress = Math.min(
            MemoryConfigurations.currentConfiguration.kernelDataSegmentLimitAddress.toInt(),
            kernelDataBaseAddress +
                    BLOCK_LENGTH_WORDS * BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES
        )
        kernelTextLimitAddress = Math.min(
            MemoryConfigurations.currentConfiguration.kernelTextLimitAddress.toInt(),
            kernelTextBaseAddress +
                    TEXT_BLOCK_LENGTH_WORDS * TEXT_BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES
        )
        stackLimitAddress = Math.max(
            MemoryConfigurations.currentConfiguration.stackLimitAddress.toInt(),
            stackBaseAddress -
                    BLOCK_LENGTH_WORDS * BLOCK_TABLE_LENGTH * WORD_LENGTH_BYTES
        )
        memoryMapLimitAddress = Math.min(
            MemoryConfigurations.currentConfiguration.memoryMapLimitAddress.toInt(),
            memoryMapBaseAddress +
                    BLOCK_LENGTH_WORDS * MMIO_TABLE_LENGTH * WORD_LENGTH_BYTES
        )
    }

    /**
     * Utility to determine if given address is word-aligned.
     *
     * @param address the address to check
     * @return true if address is word-aligned, false otherwise
     */
    @JvmStatic
    fun isUnaligned(address: Int): Boolean {
        return address % WORD_LENGTH_BYTES != 0
    }

    /**
     * Utility to determine if given address is doubleword-aligned.
     *
     * @param address the address to check
     * @return true if address is doubleword-aligned, false otherwise
     */
    @JvmStatic
    fun doublewordAligned(address: Int): Boolean {
        return address % (WORD_LENGTH_BYTES + WORD_LENGTH_BYTES) == 0
    }

    /**
     * Handy little utility to find out if given address is in MARS text
     * segment (starts at Memory.textBaseAddress).
     * Note that MARS does not implement the entire MIPS text segment space,
     * but it does implement enough for hundreds of thousands of lines
     * of code.
     *
     * @param address integer memory address
     * @return true if that address is within MARS-defined text segment,
     * false otherwise.
     */
    @JvmStatic
    fun inTextSegment(address: Int): Boolean {
        return address >= textBaseAddress && address < textLimitAddress
    }
    /*  *******************************  THE SETTER METHODS  ******************************/ ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Handy little utility to find out if given address is in MARS kernel
     * text segment (starts at Memory.kernelTextBaseAddress).
     *
     * @param address integer memory address
     * @return true if that address is within MARS-defined kernel text segment,
     * false otherwise.
     */
    @JvmStatic
    fun inKernelTextSegment(address: Int): Boolean {
        return address >= kernelTextBaseAddress && address < kernelTextLimitAddress
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Handy little utility to find out if given address is in MARS data
     * segment (starts at Memory.dataSegmentBaseAddress).
     * Note that MARS does not implement the entire MIPS data segment space,
     * but it does support at least 4MB.
     *
     * @param address integer memory address
     * @return true if that address is within MARS-defined data segment,
     * false otherwise.
     */
    @JvmStatic
    fun inDataSegment(address: Int): Boolean {
        return address >= dataSegmentBaseAddress && address < dataSegmentLimitAddress
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Handy little utility to find out if given address is in MARS kernel data
     * segment (starts at Memory.kernelDataSegmentBaseAddress).
     *
     * @param address integer memory address
     * @return true if that address is within MARS-defined kernel data segment,
     * false otherwise.
     */
    @JvmStatic
    fun inKernelDataSegment(address: Int): Boolean {
        return address >= kernelDataBaseAddress && address < kernelDataSegmentLimitAddress
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /**
     * Handy little utility to find out if given address is in the Memory Map area
     * starts at Memory.memoryMapBaseAddress, range 0xffff0000 to 0xffffffff.
     *
     * @param address integer memory address
     * @return true if that address is within MARS-defined memory map (MMIO) area,
     * false otherwise.
     */
    @JvmStatic
    fun inMemoryMapSegment(address: Int): Boolean {
        return address in memoryMapBaseAddress until kernelHighAddress
    }

    /*
     * Private constructor for Memory.  Separate data structures for text and data segments.
     **/
    init {
        initialize()
    }
}