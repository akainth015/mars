package mars.mips.instructions.syscalls;

import mars.Globals;
import mars.ProcessingException;
import mars.ProgramStatement;
import mars.mips.hardware.RegisterFile;
import mars.simulator.Exceptions;


/**
 * Service to allocate amount of heap memory specified in $a0, putting address into $v0.
 */

public class SyscallSbrk extends AbstractSyscall {
    /**
     * Build an instance of the Sbrk syscall.  Default service number
     * is 9 and name is "Sbrk".
     */
    public SyscallSbrk() {
        super(9, "Sbrk");
    }

    /**
     * Performs syscall function to allocate amount of heap memory specified in $a0, putting address into $v0.
     */
    public void simulate(ProgramStatement statement) throws ProcessingException {
        int address = 0;
        try {
            address = Globals.memory.allocateBytesFromHeap(RegisterFile.getValue(4));
        } catch (IllegalArgumentException iae) {
            throw new ProcessingException(statement,
                    iae.getMessage() + " (syscall " + this.getNumber() + ")",
                    Exceptions.SYSCALL_EXCEPTION);
        }
        RegisterFile.updateRegister(2, address);
    }
}