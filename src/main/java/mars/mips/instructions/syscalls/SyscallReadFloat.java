package mars.mips.instructions.syscalls;

import mars.ProcessingException;
import mars.ProgramStatement;
import mars.mips.hardware.Coprocessor1;
import mars.simulator.Exceptions;
import mars.util.SystemIO;


/**
 * Service to read the bits of input float into $f0
 */

public class SyscallReadFloat extends AbstractSyscall {
    /**
     * Build an instance of the Read Float syscall.  Default service number
     * is 6 and name is "ReadFloat".
     */
    public SyscallReadFloat() {
        super(6, "ReadFloat");
    }

    /**
     * Performs syscall function to read the bits of input float into $f0
     */
    public void simulate(ProgramStatement statement) throws ProcessingException {
        float floatValue = 0;
        try {
            floatValue = SystemIO.readFloat(this.getNumber());
        } catch (NumberFormatException e) {
            throw new ProcessingException(statement,
                    "invalid float input (syscall " + this.getNumber() + ")",
                    Exceptions.SYSCALL_EXCEPTION);
        }
        Coprocessor1.updateRegister(0, Float.floatToRawIntBits(floatValue));
    }
}