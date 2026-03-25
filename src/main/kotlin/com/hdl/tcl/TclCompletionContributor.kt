package com.hdl.tcl

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class TclCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            TclKeywordCompletionProvider()
        )
    }
}

class TclKeywordCompletionProvider : CompletionProvider<CompletionParameters>() {
    companion object {
        private val KEYWORDS = listOf(
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

        private val VIVADO_COMMANDS = listOf(
            "create_bd_cell", "create_bd_intf_pin", "create_bd_pin", "set_property",
            "connect_bd_intf_net", "connect_bd_net", "current_bd_instance",
            "get_bd_cells", "get_bd_intf_pins", "get_bd_pins", "get_property",
            "assign_bd_address", "validate_bd_design", "save_bd_design",
            "create_bd_intf_port", "create_bd_port", "get_bd_addr_spaces", "get_bd_addr_segs",
            "create_bd_design", "open_bd_design", "close_bd_design", "get_bd_designs",
            "import_files", "add_files", "remove_files", "update_compile_order",
            "launch_runs", "wait_on_run", "open_run", "close_run",
            "get_projects", "current_project", "create_project", "open_project", "close_project"
        )
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        resultSet: CompletionResultSet
    ) {
        KEYWORDS.forEach { keyword ->
            resultSet.addElement(
                LookupElementBuilder.create(keyword)
                    .withTypeText("Tcl keyword")
                    .bold()
            )
        }

        VIVADO_COMMANDS.forEach { command ->
            resultSet.addElement(
                LookupElementBuilder.create(command)
                    .withTypeText("Vivado command")
            )
        }
    }
}
