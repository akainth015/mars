package mars.assembler

import mars.*
import java.io.File

/*
Copyright (c) 2003-2013,  Pete Sanderson and Kenneth Vollmar

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
 * A tokenizer is capable of tokenizing a complete MIPS program, or a given line from
 * a MIPS program.  Since MIPS is line-oriented, each line defines a complete statement.
 * Tokenizing is the process of analyzing the input MIPS program for the purpose of
 * recognizing each MIPS language element.  The types of language elements are known as "tokens".
 * MIPS tokens are defined in the TokenTypes class.<br></br><br></br>
 * Example: <br></br>
 * The MIPS statement  <tt>here:  lw  $t3, 8($t4)   #load third member of array</tt><br></br>
 * generates the following token list<br></br>
 * IDENTIFIER, COLON, OPERATOR, REGISTER_NAME, COMMA, INTEGER_5, LEFT_PAREN,
 * REGISTER_NAME, RIGHT_PAREN, COMMENT<br></br>
 *
 * @author Pete Sanderson
 * @version August 2003
 */
class Tokenizer @JvmOverloads constructor(program: MIPSprogram? = null) {
    /**
     * Fetch this Tokenizer's error list.
     *
     * @return the error list
     */
    var errors: ErrorList
        private set
    private var sourceMIPSprogram: MIPSprogram?
    private var equivalents // DPS 11-July-2012
            : HashMap<String, String>? = null

    /**
     * Will tokenize a complete MIPS program.  MIPS is line oriented (not free format),
     * so we will be line-oriented too.
     *
     * @param p The MIPSprogram to be tokenized.
     * @return An ArrayList representing the tokenized program.  Each list member is a TokenList
     * that represents a tokenized source statement from the MIPS program.
     */
    @Throws(ProcessingException::class)
    fun tokenize(p: MIPSprogram): ArrayList<TokenList> {
        sourceMIPSprogram = p
        equivalents = HashMap() // DPS 11-July-2012
        val tokenList = ArrayList<TokenList>()
        //ArrayList source = p.getSourceList();
        val source = processIncludes(p, HashMap()) // DPS 9-Jan-2013
        p.sourceLineList = source
        var currentLineTokens: TokenList
        var sourceLine: String
        for (i in source.indices) {
            sourceLine = source[i].source
            currentLineTokens = this.tokenizeLine(i + 1, sourceLine)
            tokenList.add(currentLineTokens)
            // DPS 03-Jan-2013. Related to 11-July-2012. If source code substitution was made
            // based on .eqv directive during tokenizing, the processed line, a String, is
            // not the same object as the original line.  Thus I can use != instead of !equals()
            // This IF statement will replace original source with source modified by .eqv substitution.
            // Not needed by assembler, but looks better in the Text Segment Display.
            if (sourceLine.length > 0 && sourceLine !== currentLineTokens.processedLine) {
                source[i] = SourceLine(currentLineTokens.processedLine, source[i].mipSprogram, source[i].lineNumber)
            }
        }
        if (errors.errorsOccurred()) {
            throw ProcessingException(errors)
        }
        return tokenList
    }

    // pre-pre-processing pass through source code to process any ".include" directives.
    // When one is encountered, the contents of the included file are inserted at that
    // point.  If no .include statements, the return value is a new array list but
    // with the same lines of source code.  Uses recursion to correctly process included
    // files that themselves have .include.  Plus it will detect and report recursive
    // includes both direct and indirect.
    // DPS 11-Jan-2013
    @Throws(ProcessingException::class)
    private fun processIncludes(program: MIPSprogram, inclFiles: MutableMap<String, String>): ArrayList<SourceLine> {
        val source = program.sourceList
        val result = ArrayList<SourceLine>(source.size)
        for (i in source.indices) {
            val line = source[i]
            val tl = tokenizeLine(program, i + 1, line, false)
            var hasInclude = false
            for (ii in 0 until tl.size()) {
                if (tl[ii].value.equals(Directive.INCLUDE.directiveName, ignoreCase = true)
                    && tl.size() > ii + 1
                    && tl[ii + 1].type == TokenTypes.QUOTED_STRING
                ) {
                    var filename = tl[ii + 1].value
                    filename = filename.substring(1, filename.length - 1) // get rid of quotes
                    // Handle either absolute or relative pathname for .include file
                    if (!File(filename).isAbsolute) {
                        filename = File(program.filename).parent + File.separator + filename
                    }
                    if (inclFiles.containsKey(filename)) {
                        // This is a recursive include.  Generate error message and return immediately.
                        val t = tl[ii + 1]
                        errors.add(
                            ErrorMessage(
                                program, t.sourceLine, t.startPos,
                                "Recursive include of file $filename"
                            )
                        )
                        throw ProcessingException(errors)
                    }
                    inclFiles[filename] = filename
                    val incl = MIPSprogram()
                    try {
                        incl.readSource(filename)
                    } catch (p: ProcessingException) {
                        val t = tl[ii + 1]
                        errors.add(
                            ErrorMessage(
                                program, t.sourceLine, t.startPos,
                                "Error reading include file $filename"
                            )
                        )
                        throw ProcessingException(errors)
                    }
                    val allLines = processIncludes(incl, inclFiles)
                    result.addAll(allLines)
                    hasInclude = true
                    break
                }
            }
            if (!hasInclude) {
                result.add(SourceLine(line, program, i + 1)) //line);
            }
        }
        return result
    }

    /**
     * Used only to create a token list for the example provided with each instruction
     * specification.
     *
     * @param example The example MIPS instruction to be tokenized.
     * @return An TokenList representing the tokenized instruction.  Each list member is a Token
     * that represents one language element.
     * @throws ProcessingException This occurs only if the instruction specification itself
     * contains one or more lexical (i.e. token) errors.
     */
    @Throws(ProcessingException::class)
    fun tokenizeExampleInstruction(example: String): TokenList {
        val result: TokenList
        result = tokenizeLine(sourceMIPSprogram, 0, example, false)
        if (errors.errorsOccurred()) {
            throw ProcessingException(errors)
        }
        return result
    }

    /**
     * Will tokenize one line of source code.  If lexical errors are discovered,
     * they are noted in an ErrorMessage object which is added to the ErrorList.
     * Will NOT throw an exception yet because we want to persevere beyond first error.
     *
     * @param lineNum line number from source code (used in error message)
     * @param theLine String containing source code
     * @return the generated token list for that line
     */
    /*
     *
     * Tokenizing is not as easy as it appears at first blush, because the typical
     * delimiters: space, tab, comma, can all appear inside MIPS quoted ASCII strings!
     * Also, spaces are not as necessary as they seem, the following line is accepted
     * and parsed correctly by SPIM:    label:lw,$t4,simple#comment
     * as is this weird variation:      label  :lw  $t4  ,simple ,  ,  , # comment
     *
     * as is this line:  stuff:.asciiz"# ,\n\"","aaaaa"  (interestingly, if you put
     * additional characters after the \", they are ignored!!)
     *
     * I also would like to know the starting character position in the line of each
     * token, for error reporting purposes.  StringTokenizer cannot give you this.
     *
     * Given all the above, it is just as easy to "roll my own" as to use StringTokenizer
     */
    // Modified for release 4.3, to preserve existing API.
    fun tokenizeLine(lineNum: Int, theLine: String): TokenList {
        return tokenizeLine(sourceMIPSprogram, lineNum, theLine, true)
    }

    /**
     * Will tokenize one line of source code.  If lexical errors are discovered,
     * they are noted in an ErrorMessage object which is added to the provided ErrorList
     * instead of the Tokenizer's error list. Will NOT throw an exception.
     *
     * @param lineNum         line number from source code (used in error message)
     * @param theLine         String containing source code
     * @param callerErrorList errors will go into this list instead of tokenizer's list.
     * @return the generated token list for that line
     */
    fun tokenizeLine(lineNum: Int, theLine: String, callerErrorList: ErrorList): TokenList {
        val saveList = errors
        errors = callerErrorList
        val tokens = this.tokenizeLine(lineNum, theLine)
        errors = saveList
        return tokens
    }

    /**
     * Will tokenize one line of source code.  If lexical errors are discovered,
     * they are noted in an ErrorMessage object which is added to the provided ErrorList
     * instead of the Tokenizer's error list. Will NOT throw an exception.
     *
     * @param lineNum          line number from source code (used in error message)
     * @param theLine          String containing source code
     * @param callerErrorList  errors will go into this list instead of tokenizer's list.
     * @param doEqvSubstitutes boolean param set true to perform .eqv substitutions, else false
     * @return the generated token list for that line
     */
    fun tokenizeLine(lineNum: Int, theLine: String, callerErrorList: ErrorList, doEqvSubstitutes: Boolean): TokenList {
        val saveList = errors
        errors = callerErrorList
        val tokens = this.tokenizeLine(sourceMIPSprogram, lineNum, theLine, doEqvSubstitutes)
        errors = saveList
        return tokens
    }

    /**
     * Will tokenize one line of source code.  If lexical errors are discovered,
     * they are noted in an ErrorMessage object which is added to the provided ErrorList
     * instead of the Tokenizer's error list. Will NOT throw an exception.
     *
     * @param program          MIPSprogram containing this line of source
     * @param lineNum          line number from source code (used in error message)
     * @param theLine          String containing source code
     * @param doEqvSubstitutes boolean param set true to perform .eqv substitutions, else false
     * @return the generated token list for that line
     */
    fun tokenizeLine(program: MIPSprogram?, lineNum: Int, theLine: String, doEqvSubstitutes: Boolean): TokenList {
        var result = TokenList()
        if (theLine.length == 0) return result
        // will be faster to work with char arrays instead of strings
        var c: Char
        val line = theLine.toCharArray()
        var linePos = 0
        val token = CharArray(line.size)
        var tokenPos = 0
        var tokenStartPos = 1
        var insideQuotedString = false
        if (Globals.debug) println("source line --->$theLine<---")
        // Each iteration of this loop processes one character in the source line.
        while (linePos < line.size) {
            c = line[linePos]
            if (insideQuotedString) { // everything goes into token
                token[tokenPos++] = c
                if (c == '"' && token[tokenPos - 2] != '\\') { // If quote not preceded by backslash, this is end
                    processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                    tokenPos = 0
                    insideQuotedString = false
                }
            } else { // not inside a quoted string, so be sensitive to delimiters
                when (c) {
                    '#' -> {
                        if (tokenPos > 0) {
                            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                        }
                        tokenStartPos = linePos + 1
                        tokenPos = line.size - linePos
                        System.arraycopy(line, linePos, token, 0, tokenPos)
                        processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                        linePos = line.size
                        tokenPos = 0
                    }
                    ' ', '\t', ',' -> if (tokenPos > 0) {
                        processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                        tokenPos = 0
                    }
                    '+', '-' -> {
                        // Here's the REAL hack: recognizing signed exponent in E-notation floating point!
                        // (e.g. 1.2e-5) Add the + or - to the token and keep going.  DPS 17 Aug 2005
                        if (tokenPos > 0 && line.size >= linePos + 2 && Character.isDigit(line[linePos + 1]) &&
                            (line[linePos - 1] == 'e' || line[linePos - 1] == 'E')
                        ) {
                            token[tokenPos++] = c
                            break
                        }
                        // End of REAL hack.
                        if (tokenPos > 0) {
                            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                            tokenPos = 0
                        }
                        tokenStartPos = linePos + 1
                        token[tokenPos++] = c
                        if (!((result.isEmpty || result[result.size() - 1].type != TokenTypes.IDENTIFIER) &&
                                    line.size >= linePos + 2 && Character.isDigit(line[linePos + 1]))
                        ) {
                            // treat it as binary.....
                            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                            tokenPos = 0
                        }
                    }
                    ':', '(', ')' -> {
                        if (tokenPos > 0) {
                            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                            tokenPos = 0
                        }
                        tokenStartPos = linePos + 1
                        token[tokenPos++] = c
                        processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                        tokenPos = 0
                    }
                    '"' -> {
                        if (tokenPos > 0) {
                            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                            tokenPos = 0
                        }
                        tokenStartPos = linePos + 1
                        token[tokenPos++] = c
                        insideQuotedString = true
                    }
                    '\'' -> {
                        if (tokenPos > 0) {
                            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                            tokenPos = 0
                        }
                        // Our strategy is to process the whole thing right now...
                        tokenStartPos = linePos + 1
                        token[tokenPos++] = c // Put the quote in token[0]
                        val lookaheadChars = line.size - linePos - 1
                        // need minimum 2 more characters, 1 for char and 1 for ending quote
                        if (lookaheadChars < 2) break // gonna be an error
                        c = line[++linePos]
                        token[tokenPos++] = c // grab second character, put it in token[1]
                        if (c == '\'') break // gonna be an error: nothing between the quotes
                        c = line[++linePos]
                        token[tokenPos++] = c // grab third character, put it in token[2]
                        // Process if we've either reached second, non-escaped, quote or end of line.
                        if (c == '\'' && token[1] != '\\' || lookaheadChars == 2) {
                            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                            tokenPos = 0
                            tokenStartPos = linePos + 1
                            break
                        }
                        // At this point, there is at least one more character on this line. If we're
                        // still here after seeing a second quote, it was escaped.  Not done yet;
                        // we either have an escape code, an octal code (also escaped) or invalid.
                        c = line[++linePos]
                        token[tokenPos++] = c // grab fourth character, put it in token[3]
                        // Process, if this is ending quote for escaped character or if at end of line
                        if (c == '\'' || lookaheadChars == 3) {
                            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                            tokenPos = 0
                            tokenStartPos = linePos + 1
                            break
                        }
                        // At this point, we've handled all legal possibilities except octal, e.g. '\377'
                        // Proceed, if enough characters remain to finish off octal.
                        if (lookaheadChars >= 5) {
                            c = line[++linePos]
                            token[tokenPos++] = c // grab fifth character, put it in token[4]
                            if (c != '\'') {
                                // still haven't reached end, last chance for validity!
                                c = line[++linePos]
                                token[tokenPos++] = c // grab sixth character, put it in token[5]
                            }
                        }
                        // process no matter what...we either have a valid character by now or not
                        processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
                        tokenPos = 0
                        tokenStartPos = linePos + 1
                    }
                    else -> {
                        if (tokenPos == 0) tokenStartPos = linePos + 1
                        token[tokenPos++] = c
                    }
                }
            } // if (insideQuotedString)
            linePos++
        } // while
        if (tokenPos > 0) {
            processCandidateToken(token, program, lineNum, theLine, tokenPos, tokenStartPos, result)
        }
        if (doEqvSubstitutes) {
            result = processEqv(program, lineNum, theLine, result) // DPS 11-July-2012
        }
        return result
    }

    // Process the .eqv directive, which needs to be applied prior to tokenizing of subsequent statements.
    // This handles detecting that theLine contains a .eqv directive, in which case it needs
    // to be added to the HashMap of equivalents.  It also handles detecting that theLine
    // contains a symbol that was previously defined in an .eqv directive, in which case
    // the substitution needs to be made.
    // DPS 11-July-2012
    private fun processEqv(program: MIPSprogram?, lineNum: Int, theLine: String, tokens: TokenList): TokenList {
        // See if it is .eqv directive.  If so, record it...
        // Have to assure it is a well-formed statement right now (can't wait for assembler).
        var theLine = theLine
        if (tokens.size() > 2 && (tokens[0].type == TokenTypes.DIRECTIVE || tokens[2].type == TokenTypes.DIRECTIVE)) {
            // There should not be a label but if there is, the directive is in token position 2 (ident, colon, directive).
            val dirPos = if (tokens[0].type == TokenTypes.DIRECTIVE) 0 else 2
            if (Directive.matchDirective(tokens[dirPos].value) == Directive.EQV) {
                // Get position in token list of last non-comment token
                val tokenPosLastOperand =
                    tokens.size() - if (tokens[tokens.size() - 1].type == TokenTypes.COMMENT) 2 else 1
                // There have to be at least two non-comment tokens beyond the directive
                if (tokenPosLastOperand < dirPos + 2) {
                    errors.add(
                        ErrorMessage(
                            program, lineNum, tokens[dirPos].startPos,
                            "Too few operands for " + Directive.EQV.directiveName + " directive"
                        )
                    )
                    return tokens
                }
                // Token following the directive has to be IDENTIFIER
                if (tokens[dirPos + 1].type != TokenTypes.IDENTIFIER) {
                    errors.add(
                        ErrorMessage(
                            program, lineNum, tokens[dirPos].startPos,
                            "Malformed ${Directive.EQV.directiveName} directive"
                        )
                    )
                    return tokens
                }
                val symbol = tokens[dirPos + 1].value
                // Make sure the symbol is not contained in the expression.  Not likely to occur but if left
                // undetected it will result in infinite recursion.  e.g.  .eqv ONE, (ONE)
                for (i in dirPos + 2 until tokens.size()) {
                    if (tokens[i].value == symbol) {
                        errors.add(
                            ErrorMessage(
                                program, lineNum, tokens[dirPos].startPos,
                                "Cannot substitute $symbol for itself in ${Directive.EQV.directiveName} directive"
                            )
                        )
                        return tokens
                    }
                }
                // Expected syntax is symbol, expression.  I'm allowing the expression to comprise
                // multiple tokens, so I want to get everything from the IDENTIFIER to either the
                // COMMENT or to the end.
                val startExpression = tokens[dirPos + 2].startPos
                val endExpression = tokens[tokenPosLastOperand].startPos + tokens[tokenPosLastOperand].value.length
                val expression = theLine.substring(startExpression - 1, endExpression - 1)
                // Symbol cannot be redefined - the only reason for this is to act like the Gnu .eqv
                if (equivalents!!.containsKey(symbol) && equivalents!![symbol] != expression) {
                    errors.add(
                        ErrorMessage(
                            program, lineNum, tokens[dirPos + 1].startPos,
                            "\"$symbol\" is already defined"
                        )
                    )
                    return tokens
                }
                equivalents!![symbol] = expression
                return tokens
            }
        }
        // Check if a substitution from defined .eqv is to be made.  If so, make one.
        var substitutionMade = false
        for (i in 0 until tokens.size()) {
            val token = tokens[i]
            if (token.type == TokenTypes.IDENTIFIER && equivalents != null && equivalents!!.containsKey(token.value)) {
                // do the substitution
                val sub = equivalents!![token.value]
                val startPos = token.startPos
                theLine =
                    theLine.substring(0, startPos - 1) + sub + theLine.substring(startPos + token.value.length - 1)
                substitutionMade =
                    true // one substitution per call.  If there are multiple, will catch next one on the recursion
                break
            }
        }
        tokens.processedLine = theLine // DPS 03-Jan-2013. Related to changes of 11-July-2012.
        return if (substitutionMade) tokenizeLine(lineNum, theLine) else tokens
    }

    // Given candidate token and its position, will classify and record it.
    private fun processCandidateToken(
        token: CharArray, program: MIPSprogram?, line: Int, theLine: String,
        tokenPos: Int, tokenStartPos: Int, tokenList: TokenList
    ) {
        var value = String(token, 0, tokenPos)
        if (value.length > 0 && value[0] == '\'') value = preprocessCharacterLiteral(value)
        val type = TokenTypes.matchTokenType(value)
        if (type == TokenTypes.ERROR) {
            errors.add(
                ErrorMessage(
                    program, line, tokenStartPos,
                    "$theLine\nInvalid language element: $value"
                )
            )
        }
        val toke = Token(type, value, program, line, tokenStartPos)
        tokenList.add(toke)
    }

    // If passed a candidate character literal, attempt to translate it into integer constant.
    // If the translation fails, return original value.
    private fun preprocessCharacterLiteral(value: String): String {
        // must start and end with quote and have something in between
        if (value.length < 3 || value[0] != '\'' || value[value.length - 1] != '\'') {
            return value
        }
        val quotesRemoved = value.substring(1, value.length - 1)
        // if not escaped, then if one character left return its value else return original.
        if (quotesRemoved[0] != '\\') {
            return if (quotesRemoved.length == 1) Integer.toString(quotesRemoved[0].toInt()) else value
        }
        // now we know it is escape sequence and have to decode which of the 8: ',",\,n,t,b,r,f
        if (quotesRemoved.length == 2) {
            val escapedCharacterIndex = escapedCharacters.indexOf(quotesRemoved[1])
            return if (escapedCharacterIndex >= 0) escapedCharactersValues[escapedCharacterIndex] else value
        }
        // last valid possibility is 3 digit octal code 000 through 377
        if (quotesRemoved.length == 4) {
            try {
                val intValue = quotesRemoved.substring(1).toInt(8)
                if (intValue >= 0 && intValue <= 255) {
                    return Integer.toString(intValue)
                }
            } catch (ignored: NumberFormatException) {
            } // if not valid octal, will fall through and reject
        }
        return value
    }

    companion object {
        // The 8 escaped characters are: single quote, double quote, backslash, newline (linefeed),
        // tab, backspace, return, form feed.  The characters and their corresponding decimal codes:
        private const val escapedCharacters = "'\"\\ntbrf0"
        private val escapedCharactersValues = arrayOf("39", "34", "92", "10", "9", "8", "13", "12", "0")
    }
    /**
     * Constructor for use with existing MIPSprogram.  Designed to be used with Macro feature.
     *
     * @param program A previously-existing MIPSprogram object or null if none.
     */
    /**
     * Simple constructor. Initializes empty error list.
     */
    init {
        errors = ErrorList()
        sourceMIPSprogram = program
    }
}