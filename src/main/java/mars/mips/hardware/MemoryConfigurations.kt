package mars.mips.hardware

import mars.Globals
import java.util.*

/**
 * Models the collection of MIPS memory configurations.
 * The default configuration is based on SPIM.  Starting with MARS 3.7,
 * the configuration can be changed.
 *
 * @author Pete Sanderson
 * @version August 2009
 */
object MemoryConfigurations {
    // Be careful, these arrays are parallel and position-sensitive.
    // The getters in this and in MemoryConfiguration depend on this
    // sequence.  Should be refactored...  The order comes from the
    // original listed order in Memory.java, where most of these were
    // "final" until Mars 3.7 and changeable memory configurations.
    @JvmField
    val configurationItemNames = arrayOf(
        ".text base address",
        "data segment base address",
        ".extern base address",
        "global pointer \$gp",
        ".data base address",
        "heap base address",
        "stack pointer \$sp",
        "stack base address",
        "user space high address",
        "kernel space base address",
        ".ktext base address",
        "exception handler address",
        ".kdata base address",
        "MMIO base address",
        "kernel space high address",
        "data segment limit address",
        "text limit address",
        "kernel data segment limit address",
        "kernel text limit address",
        "stack limit address",
        "memory map limit address"
    )
    private val configurations = MemoryConfiguration.values()

    @JvmStatic
    val defaultConfiguration = MemoryConfiguration.Default

    var currentConfiguration: MemoryConfiguration = defaultConfiguration
        set(config) {
            if (config !== mars.mips.hardware.MemoryConfigurations.currentConfiguration) {
                Globals.memory.clear()
                RegisterFile.getUserRegister("\$gp").changeResetValue(config.globalPointer.toInt())
                RegisterFile.getUserRegister("\$sp").changeResetValue(config.stackPointer.toInt())
                RegisterFile.getProgramCounterRegister().changeResetValue(config.textBaseAddress.toInt())
                RegisterFile.initializeProgramCounter(config.textBaseAddress.toInt())
                RegisterFile.resetRegisters()
            }
            field = config
        }

    @JvmStatic
    fun buildConfigurationCollection() {
        currentConfiguration = getConfigurationByName(Globals.getSettings().memoryConfiguration)
    }

    @JvmStatic
    val configurationsIterator: Iterator<MemoryConfiguration>
        get() = Arrays.stream(configurations).iterator()

    @JvmStatic
    fun getConfigurationByName(name: String?): MemoryConfiguration {
        return MemoryConfiguration.valueOf(name!!)
    }
}