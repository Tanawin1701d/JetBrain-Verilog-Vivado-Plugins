package com.hdl.tcl

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class TclLexer : LexerBase() {
    private var buffer: CharSequence? = null
    private var startOffset = 0
    private var endOffset = 0
    private var currentOffset = 0
    private var currentTokenType: IElementType? = null

    companion object {
        val KEYWORDS = setOf(
            "proc", "set", "if", "else", "elseif", "for", "foreach", "while",
            "switch", "return", "break", "continue", "catch", "variable",
            "global", "package", "namespace", "upvar", "uplevel", "expr",
            "list", "lindex", "lappend", "llength", "lsort", "lrange", "linsert",
            "lreplace", "lsearch", "lassign", "split", "join", "string",
            "regexp", "regsub", "file", "open", "close", "read", "puts", "gets",
            "seek", "tell", "flush", "socket", "fconfigure", "fileevent",
            "vwait", "update", "after", "exit", "source", "load", "unload",
            "info", "interp", "slave", "alias", "hidden", "eval", "subst",
            "time", "history", "rename", "unknown", "encoding", "binary",
            "scan", "format", "clock", "dict", "apply", "coroutine", "yield",
            "yieldto", "tailcall", "trace", "cd", "pwd", "glob", "exec", "pid", "error"
        )
        
        val VIVADO_COMMANDS = setOf(
            "create_bd_cell", "create_bd_intf_pin", "create_bd_pin", "set_property",
            "connect_bd_intf_net", "connect_bd_net", "current_bd_instance",
            "get_bd_cells", "get_bd_intf_pins", "get_bd_pins", "get_property",
            "assign_bd_address", "validate_bd_design", "save_bd_design",
            "create_bd_intf_port", "create_bd_port", "get_bd_addr_spaces", "get_bd_addr_segs"
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
                currentTokenType = TclTokenTypes.WHITE_SPACE
            }
            char == '#' -> {
                // In TCL, # is a comment only if it appears where a command can start
                // For simplicity in syntax highlighting, we often treat it as a line comment
                while (currentOffset < endOffset && buffer!![currentOffset] != '\n') {
                    currentOffset++
                }
                currentTokenType = TclTokenTypes.LINE_COMMENT
            }
            char == '$' -> {
                currentOffset++
                if (currentOffset < endOffset && buffer!![currentOffset] == '{') {
                    currentOffset++
                    while (currentOffset < endOffset && buffer!![currentOffset] != '}') {
                        currentOffset++
                    }
                    if (currentOffset < endOffset) currentOffset++
                } else {
                    while (currentOffset < endOffset && (buffer!![currentOffset].isLetterOrDigit() || buffer!![currentOffset] == '_' || buffer!![currentOffset] == ':')) {
                        currentOffset++
                    }
                }
                currentTokenType = TclTokenTypes.VARIABLE
            }
            char == '"' -> {
                currentOffset++
                while (currentOffset < endOffset && buffer!![currentOffset] != '"') {
                    if (buffer!![currentOffset] == '\\') currentOffset++
                    currentOffset++
                }
                if (currentOffset < endOffset) currentOffset++
                currentTokenType = TclTokenTypes.STRING
            }
            char == '{' || char == '}' -> {
                currentOffset++
                currentTokenType = TclTokenTypes.BRACE
            }
            char == '[' || char == ']' -> {
                currentOffset++
                currentTokenType = TclTokenTypes.BRACKET
            }
            char.isLetter() || char == '_' || char == '-' -> {
                while (currentOffset < endOffset && (buffer!![currentOffset].isLetterOrDigit() || buffer!![currentOffset] == '_' || buffer!![currentOffset] == ':' || buffer!![currentOffset] == '-')) {
                    currentOffset++
                }
                val text = buffer!!.subSequence(startOffset, currentOffset).toString()
                currentTokenType = when {
                    text in KEYWORDS -> TclTokenTypes.KEYWORD
                    text in VIVADO_COMMANDS -> TclTokenTypes.COMMAND
                    else -> TclTokenTypes.IDENTIFIER
                }
            }
            char.isDigit() -> {
                while (currentOffset < endOffset && (buffer!![currentOffset].isDigit() || buffer!![currentOffset] == '.')) {
                    currentOffset++
                }
                currentTokenType = TclTokenTypes.NUMBER
            }
            char in "+-*/%<>=!&|^~" -> {
                currentOffset++
                currentTokenType = TclTokenTypes.OPERATOR
            }
            char in ";," -> {
                currentOffset++
                currentTokenType = TclTokenTypes.PUNCTUATION
            }
            else -> {
                currentOffset++
                currentTokenType = TclTokenTypes.BAD_CHARACTER
            }
        }
    }

    override fun getBufferSequence(): CharSequence = buffer!!
    override fun getBufferEnd(): Int = endOffset
}
