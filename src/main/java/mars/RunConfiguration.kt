package mars

import mars.util.DisplayFormat

data class RunConfiguration(
    /**
     * Simulate the program execution. If false, it will only be assembled
     */
    val simulate: Boolean = true,
    /**
     * The format in which to display memory and register contents
     */
    val displayFormat: DisplayFormat = DisplayFormat.HEXADECIMAL,
    /**
     * The logging verbosity level
     */
    val verbose: Boolean = true,
    /**
     * Whether to assemble all the files in the directory
     */
    val assembleProject: Boolean = true,
    /**
     * Enable MARS assembler instructions like "li"
     */
    val enablePseduoInstructions: Boolean = true,
    /**
     * Enable MIPS delay slot for branch and jump instructions
     */
    val enableDelayedBranching: Boolean = false,
    /**
     * Whether to treat warnings as errors
     */
    val warningsAreErrors: Boolean = false,
    /**
     * Whether to start program execution at .text address or at main label
     */
    val startAtMain: Boolean = true,
    /**
     * Whether to count the number of instructions that are run
     */
    val countInstructions: Boolean = false,
    /**
     * Allow code to write to the .text segment of memory
     */
    val enableSMC: Boolean = false
)
