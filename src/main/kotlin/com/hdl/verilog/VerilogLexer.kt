package com.hdl.verilog

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class VerilogLexer : LexerBase() {
    private var buffer: CharSequence? = null
    private var startOffset = 0
    private var endOffset = 0
    private var currentOffset = 0
    private var currentTokenType: IElementType? = null

    companion object {
        // Keywords that form structural pairs get their own token type for BraceMatcher
        private val PAIRED_KEYWORDS = mapOf(
            "begin"       to VerilogTokenTypes.BEGIN_KW,
            "end"         to VerilogTokenTypes.END_KW,
            "case"        to VerilogTokenTypes.CASE_KW,
            "endcase"     to VerilogTokenTypes.ENDCASE_KW,
            "module"      to VerilogTokenTypes.MODULE_KW,
            "endmodule"   to VerilogTokenTypes.ENDMODULE_KW,
            "function"    to VerilogTokenTypes.FUNCTION_KW,
            "endfunction" to VerilogTokenTypes.ENDFUNCTION_KW,
            "task"        to VerilogTokenTypes.TASK_KW,
            "endtask"     to VerilogTokenTypes.ENDTASK_KW,
            "generate"    to VerilogTokenTypes.GENERATE_KW,
            "endgenerate" to VerilogTokenTypes.ENDGENERATE_KW,
        )

        // Remaining keywords that don't form pairs — highlighted as KEYWORD
        private val KEYWORDS = setOf(
            "input", "output", "inout", "wire", "reg",
            "always", "initial", "if", "else",
            "for", "while", "assign", "parameter", "localparam", "integer",
            "posedge", "negedge", "or", "and", "default", "genvar"
        )
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.currentOffset = startOffset
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = currentTokenType

    override fun getTokenStart(): Int = startOffset

    override fun getTokenEnd(): Int = currentOffset

    override fun advance() {
        if (currentOffset >= endOffset) {
            currentTokenType = null
            return
        }

        startOffset = currentOffset
        val char = buffer!![currentOffset]

        when {
            // Consume all consecutive whitespace characters as a single token
            char.isWhitespace() -> {
                while (currentOffset < endOffset && buffer!![currentOffset].isWhitespace()) {
                    currentOffset++
                }
                currentTokenType = VerilogTokenTypes.WHITE_SPACE
            }
            // Scan to end of line for `//` single-line comments
            char == '/' && currentOffset + 1 < endOffset && buffer!![currentOffset + 1] == '/' -> {
                while (currentOffset < endOffset && buffer!![currentOffset] != '\n') {
                    currentOffset++
                }
                currentTokenType = VerilogTokenTypes.LINE_COMMENT
            }
            // Scan to closing `*/` for block comments, skipping the opening `/*`
            char == '/' && currentOffset + 1 < endOffset && buffer!![currentOffset + 1] == '*' -> {
                currentOffset += 2 // skip `/*`
                while (currentOffset + 1 < endOffset) {
                    if (buffer!![currentOffset] == '*' && buffer!![currentOffset + 1] == '/') {
                        currentOffset += 2 // skip closing `*/`
                        break
                    }
                    currentOffset++
                }
                currentTokenType = VerilogTokenTypes.BLOCK_COMMENT
            }
            // Scan a full word, then classify: paired keyword → its own token, plain keyword → KEYWORD, else IDENTIFIER
            char.isLetter() || char == '_' -> {
                while (currentOffset < endOffset && (buffer!![currentOffset].isLetterOrDigit() || buffer!![currentOffset] == '_')) {
                    currentOffset++
                }
                val text = buffer!!.subSequence(startOffset, currentOffset).toString()
                currentTokenType = PAIRED_KEYWORDS[text]
                    ?: if (text in KEYWORDS) VerilogTokenTypes.KEYWORD else VerilogTokenTypes.IDENTIFIER
            }
            // Scan a Verilog numeric literal, including base prefixes like 4'b1010 or 8'hFF
            char.isDigit() -> {
                while (currentOffset < endOffset && (buffer!![currentOffset].isDigit() || buffer!![currentOffset] in "'bhdoxBHDOX_")) {
                    currentOffset++
                }
                currentTokenType = VerilogTokenTypes.NUMBER
            }
            // Scan a double-quoted string, respecting `\"` escape sequences
            char == '"' -> {
                currentOffset++ // skip opening `"`
                while (currentOffset < endOffset && buffer!![currentOffset] != '"') {
                    if (buffer!![currentOffset] == '\\') currentOffset++ // skip escaped character
                    currentOffset++
                }
                if (currentOffset < endOffset) currentOffset++ // skip closing `"`
                currentTokenType = VerilogTokenTypes.STRING
            }
            // Each bracket gets its own token type so BraceMatcher can pair them correctly
            char == '(' -> { currentOffset++; currentTokenType = VerilogTokenTypes.LPAREN   }
            char == ')' -> { currentOffset++; currentTokenType = VerilogTokenTypes.RPAREN   }
            char == '{' -> { currentOffset++; currentTokenType = VerilogTokenTypes.LBRACE   }
            char == '}' -> { currentOffset++; currentTokenType = VerilogTokenTypes.RBRACE   }
            char == '[' -> { currentOffset++; currentTokenType = VerilogTokenTypes.LBRACKET }
            char == ']' -> { currentOffset++; currentTokenType = VerilogTokenTypes.RBRACKET }
            // Single punctuation character
            char in ";,." -> {
                currentOffset++
                currentTokenType = VerilogTokenTypes.PUNCTUATION
            }
            // Single operator character
            char in "+-*/%<>=!&|^~?" -> {
                currentOffset++
                currentTokenType = VerilogTokenTypes.OPERATOR
            }
            // Anything unrecognized — flagged so the IDE can highlight it as an error
            else -> {
                currentOffset++
                currentTokenType = VerilogTokenTypes.BAD_CHARACTER
            }
        }
    }

    override fun getBufferSequence(): CharSequence = buffer!!
    override fun getBufferEnd(): Int = endOffset
}
