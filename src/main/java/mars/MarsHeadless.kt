package mars

import mars.RunConfiguration
import mars.util.DisplayFormat
import org.apache.commons.cli.*
import java.util.*

fun main(args: Array<String>) {
    val options = Options().apply {
        addOption("h", "Display the help menu")

        addOption("a", "Assemble without simulating")
        addOption("ae", true, "Terminate with exit code if an assembly error occurs")
        addOption("d", "Print debugging statements")
        addOption("db", "Enable delayed branching")
        addOption("ic", "Display count of MIPS basic instructions executed")
        addOption("nc", "Hide the copyright notice")
        addOption("np", "Disable pseudo-instructions")
        addOption("p", "Assemble all files in the same directory as the given file")
        addOption("se", true, "Terminate with exit code if a simulation error occurs")
        addOption("sm", "Start execution at main")
        addOption("smc", "Enable self-modifying code")
        addOption("we", "Assembler warnings will be considered errors")

        val dump = Option.builder("dump")
            .numberOfArgs(2)
            .desc("Dump <segment> of memory to <file> as a binary blob. <segment> can be an address range, .text, or .data")
            .build()
        addOption(dump)

        val memoryConfiguration = Option.builder("mc")
            .argName("configuration")
            .desc("Default, CompactDataAtZero, or CompactTextAtZero")
            .build()
        addOption(memoryConfiguration)

        val displayFormat = OptionGroup().apply {
            addOption("ascii", "Display memory and register contents in ASCII")
            addOption("dec", "Display memory and register contents in decimal")
            addOption("hex", "Display memory and register contents in hex")
        }
        addOptionGroup(displayFormat)
    }

    val parser = DefaultParser()
    val cmd = parser.parse(options, args, true)

    if (!cmd.hasOption("nc")) {
        println("MARS ${Globals.version} Copyright ${Globals.copyrightYears} ${Globals.copyrightHolders}")
    }

    if (cmd.hasOption("h")) {
        val helpFormatter = HelpFormatter()
        helpFormatter.printHelp("mars", options)
        return
    }

    Globals.debug = cmd.hasOption("d")
    val runConfiguration = RunConfiguration(
        !cmd.hasOption("a"),
        displayFormat = when {
            cmd.hasOption("ascii") -> DisplayFormat.ASCII
            cmd.hasOption("dec") -> DisplayFormat.DECIMAL
            else -> DisplayFormat.HEXADECIMAL
        },
        cmd.hasOption("v"),
        cmd.hasOption("p"),
        !cmd.hasOption("np"),
        cmd.hasOption("db"),
        cmd.hasOption("we"),
        cmd.hasOption("sm"),
        cmd.hasOption("ic"),
        cmd.hasOption("smc")
    )

    println(Arrays.toString(cmd.getOptionValues("dump")))
}
