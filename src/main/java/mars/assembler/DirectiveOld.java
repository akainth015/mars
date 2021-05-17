package mars.assembler;

import java.util.ArrayList;

/*
Copyright (c) 2003-2012,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */

/**
 * Class representing MIPS assembler directives.  If Java had enumerated types, these
 * would probably be implemented that way.  Each directive is represented by a unique object.
 * The directive name is indicative of the directive it represents.  For example, DATA
 * represents the MIPS .data directive.
 *
 * @author Pete Sanderson
 * @version August 2003
 **/

public final class DirectiveOld {
    private static final ArrayList directiveList = new ArrayList();

    public static final DirectiveOld DATA = new DirectiveOld(".data", "Subsequent items stored in Data segment at next available address");
    public static final DirectiveOld TEXT = new DirectiveOld(".text", "Subsequent items (instructions) stored in Text segment at next available address");
    public static final DirectiveOld WORD = new DirectiveOld(".word", "Store the listed value(s) as 32 bit words on word boundary");
    public static final DirectiveOld ASCII = new DirectiveOld(".ascii", "Store the string in the Data segment but do not add null terminator");
    public static final DirectiveOld ASCIIZ = new DirectiveOld(".asciiz", "Store the string in the Data segment and add null terminator");
    public static final DirectiveOld BYTE = new DirectiveOld(".byte", "Store the listed value(s) as 8 bit bytes");
    public static final DirectiveOld ALIGN = new DirectiveOld(".align", "Align next data item on specified byte boundary (0=byte, 1=half, 2=word, 3=double)");
    public static final DirectiveOld HALF = new DirectiveOld(".half", "Store the listed value(s) as 16 bit halfwords on halfword boundary");
    public static final DirectiveOld SPACE = new DirectiveOld(".space", "Reserve the next specified number of bytes in Data segment");
    public static final DirectiveOld DOUBLE = new DirectiveOld(".double", "Store the listed value(s) as double precision floating point");
    public static final DirectiveOld FLOAT = new DirectiveOld(".float", "Store the listed value(s) as single precision floating point");
    public static final DirectiveOld EXTERN = new DirectiveOld(".extern", "Declare the listed label and byte length to be a global data field");
    public static final DirectiveOld KDATA = new DirectiveOld(".kdata", "Subsequent items stored in Kernel Data segment at next available address");
    public static final DirectiveOld KTEXT = new DirectiveOld(".ktext", "Subsequent items (instructions) stored in Kernel Text segment at next available address");
    public static final DirectiveOld GLOBL = new DirectiveOld(".globl", "Declare the listed label(s) as global to enable referencing from other files");
    public static final DirectiveOld SET = new DirectiveOld(".set", "Set assembler variables.  Currently ignored but included for SPIM compatability");
    /*  EQV added by DPS 11 July 2012 */
    public static final DirectiveOld EQV = new DirectiveOld(".eqv", "Substitute second operand for first. First operand is symbol, second operand is expression (like #define)");
    /* MACRO and END_MACRO added by Mohammad Sekhavat Oct 2012 */
    public static final DirectiveOld MACRO = new DirectiveOld(".macro", "Begin macro definition.  See .end_macro");
    public static final DirectiveOld END_MACRO = new DirectiveOld(".end_macro", "End macro definition.  See .macro");
    /*  INCLUDE added by DPS 11 Jan 2013 */
    public static final DirectiveOld INCLUDE = new DirectiveOld(".include", "Insert the contents of the specified file.  Put filename in quotes.");
    private final String descriptor;
    private final String description; // help text

    private DirectiveOld() {
        // private ctor assures no objects can be created other than those above.
        this.descriptor = "generic";
        this.description = "";
        directiveList.add(this);
    }

    private DirectiveOld(String name, String description) {
        this.descriptor = name;
        this.description = description;
        directiveList.add(this);
    }

    /**
     * Find Directive object, if any, which matches the given String.
     *
     * @param str A String containing candidate directive name (e.g. ".ascii")
     * @return If match is found, returns matching Directives object, else returns <tt>null</tt>.
     **/

    public static DirectiveOld matchDirective(String str) {
        DirectiveOld match;
        for (int i = 0; i < directiveList.size(); i++) {
            match = (DirectiveOld) directiveList.get(i);
            if (str.equalsIgnoreCase(match.descriptor)) {
                return match;
            }
        }
        return null;
    }


    /**
     * Find Directive object, if any, which contains the given string as a prefix. For example,
     * ".a" will match ".ascii", ".asciiz" and ".align"
     *
     * @param str A String
     * @return If match is found, returns ArrayList of matching Directives objects, else returns <tt>null</tt>.
     **/

    public static ArrayList prefixMatchDirectives(String str) {
        ArrayList matches = null;
        for (int i = 0; i < directiveList.size(); i++) {
            if (((DirectiveOld) directiveList.get(i)).descriptor.toLowerCase().startsWith(str.toLowerCase())) {
                if (matches == null) {
                    matches = new ArrayList();
                }
                matches.add(directiveList.get(i));
            }
        }
        return matches;
    }

    /**
     * Produces List of Directive objects
     *
     * @return MIPS Directive
     **/
    public static ArrayList getDirectiveList() {
        return directiveList;
    }

    /**
     * Lets you know whether given directive is for integer (WORD,HALF,BYTE).
     *
     * @param direct a MIPS directive
     * @return true if given directive is FLOAT or DOUBLE, false otherwise
     **/
    public static boolean isIntegerDirective(Directive direct) {
        return direct == Directive.WORD || direct == Directive.HALF || direct == Directive.BYTE;
    }

    /**
     * Lets you know whether given directive is for floating number (FLOAT,DOUBLE).
     *
     * @param direct a MIPS directive
     * @return true if given directive is FLOAT or DOUBLE, false otherwise.
     **/
    public static boolean isFloatingDirective(Directive direct) {
        return direct == Directive.FLOAT || direct == Directive.DOUBLE;
    }

    /**
     * Produces String-ified version of Directive object
     *
     * @return String representing Directive: its MIPS name
     **/

    public String toString() {
        return this.descriptor;
    }

    /**
     * Get name of this Directives object
     *
     * @return name of this MIPS directive as a String
     **/

    public String getName() {
        return this.descriptor;
    }

    /**
     * Get description of this Directives object
     *
     * @return description of this MIPS directive (for help purposes)
     **/

    public String getDescription() {
        return this.description;
    }

}