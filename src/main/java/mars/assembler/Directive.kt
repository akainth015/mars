package mars.assembler

import java.util.*

enum class Directive(val directiveName: String, val description: String) {
    DATA(".data", "Subsequent items stored in Data segment at next available address"),
    TEXT(".text", "Subsequent items (instructions) stored in Text segment at next available address"),
    WORD(".word", "Store the listed value(s) as 32 bit words on word boundary"),
    ASCII(".ascii", "Store the string in the Data segment but do not add null terminator"),
    ASCIIZ(".asciiz", "Store the string in the Data segment and add null terminator"),
    BYTE(".byte", "Store the listed value(s) as 8 bit bytes"),
    ALIGN(".align", "Align next data item on specified byte boundary (0=byte, 1=half, 2=word, 3=double)"),
    HALF(".half", "Store the listed value(s) as 16 bit halfwords on halfword boundary"),
    SPACE(".space", "Reserve the next specified number of bytes in Data segment"),
    DOUBLE(".double", "Store the listed value(s) as double precision floating point"),
    FLOAT(".float", "Store the listed value(s) as single precision floating point"),
    EXTERN(".extern", "Declare the listed label and byte length to be a global data field"),
    KDATA(".kdata", "Subsequent items stored in Kernel Data segment at next available address"),
    KTEXT(".ktext", "Subsequent items (instructions) stored in Kernel Text segment at next available address"),
    GLOBL(".globl", "Declare the listed label(s) as global to enable referencing from other files"),
    SET(".set", "Set assembler variables.  Currently ignored but included for SPIM compatability"),

    EQV(".eqv", "Substitute second operand for first. First operand is symbol, second operand is expression (like #define)"),
    MACRO(".macro", "Begin macro definition.  See .end_macro"),
    END_MACRO(".end_macro", "End macro definition.  See .macro"),
    INCLUDE(".include", "Insert the contents of the specified file.  Put filename in quotes.");

    companion object {
        fun getDirectiveFromToken(token: String): Directive {
            return values().first { it.directiveName == token }
        }
    }
}
