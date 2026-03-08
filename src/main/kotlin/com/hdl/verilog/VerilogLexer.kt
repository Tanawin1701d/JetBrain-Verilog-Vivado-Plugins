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
        private val KEYWORDS = setOf(
            "module", "endmodule", "input", "output", "inout", "wire", "reg",
            "always", "initial", "begin", "end", "if", "else", "case", "endcase",
            "for", "while", "assign", "parameter", "localparam", "integer",
            "posedge", "negedge", "or", "and", "default", "function", "endfunction",
            "task", "endtask", "generate", "endgenerate", "genvar"
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
            char.isWhitespace() -> {
                while (currentOffset < endOffset && buffer!![currentOffset].isWhitespace()) {
                    currentOffset++
                }
                currentTokenType = VerilogTokenTypes.WHITE_SPACE
            }
            char == '/' && currentOffset + 1 < endOffset && buffer!![currentOffset + 1] == '/' -> {
                while (currentOffset < endOffset && buffer!![currentOffset] != '\n') {
                    currentOffset++
                }
                currentTokenType = VerilogTokenTypes.LINE_COMMENT
            }
            char == '/' && currentOffset + 1 < endOffset && buffer!![currentOffset + 1] == '*' -> {
                currentOffset += 2
                while (currentOffset + 1 < endOffset) {
                    if (buffer!![currentOffset] == '*' && buffer!![currentOffset + 1] == '/') {
                        currentOffset += 2
                        break
                    }
                    currentOffset++
                }
                currentTokenType = VerilogTokenTypes.BLOCK_COMMENT
            }
            char.isLetter() || char == '_' -> {
                while (currentOffset < endOffset && (buffer!![currentOffset].isLetterOrDigit() || buffer!![currentOffset] == '_')) {
                    currentOffset++
                }
                val text = buffer!!.subSequence(startOffset, currentOffset).toString()
                currentTokenType = if (text in KEYWORDS) VerilogTokenTypes.KEYWORD else VerilogTokenTypes.IDENTIFIER
            }
            char.isDigit() -> {
                while (currentOffset < endOffset && (buffer!![currentOffset].isDigit() || buffer!![currentOffset] in "'bhdoxBHDOX_")) {
                    currentOffset++
                }
                currentTokenType = VerilogTokenTypes.NUMBER
            }
            char == '"' -> {
                currentOffset++
                while (currentOffset < endOffset && buffer!![currentOffset] != '"') {
                    if (buffer!![currentOffset] == '\\') currentOffset++
                    currentOffset++
                }
                if (currentOffset < endOffset) currentOffset++
                currentTokenType = VerilogTokenTypes.STRING
            }
            char in "(){}[]" -> {
                currentOffset++
                currentTokenType = VerilogTokenTypes.BRACE
            }
            char in ";,." -> {
                currentOffset++
                currentTokenType = VerilogTokenTypes.PUNCTUATION
            }
            char in "+-*/%<>=!&|^~?" -> {
                currentOffset++
                currentTokenType = VerilogTokenTypes.OPERATOR
            }
            else -> {
                currentOffset++
                currentTokenType = VerilogTokenTypes.BAD_CHARACTER
            }
        }
    }

    override fun getBufferSequence(): CharSequence = buffer!!
    override fun getBufferEnd(): Int = endOffset
}
