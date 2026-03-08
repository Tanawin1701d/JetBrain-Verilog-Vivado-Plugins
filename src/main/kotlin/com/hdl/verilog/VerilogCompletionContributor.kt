package com.hdl.verilog

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class VerilogCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            VerilogKeywordCompletionProvider()
        )
    }
}

class VerilogKeywordCompletionProvider : CompletionProvider<CompletionParameters>() {
    companion object {
        private val KEYWORDS = listOf(
            "module", "endmodule", "input", "output", "inout", "wire", "reg",
            "always", "initial", "begin", "end", "if", "else", "case", "endcase",
            "for", "while", "assign", "parameter", "localparam", "integer",
            "posedge", "negedge", "or", "and", "default", "function", "endfunction",
            "task", "endtask", "generate", "endgenerate", "genvar", "logic",
            "bit", "byte", "shortint", "int", "longint", "time", "real",
            "supply0", "supply1", "tri", "triand", "trior", "tri0", "tri1",
            "uwire", "wand", "wor", "signed", "unsigned"
        )

        private val SYSTEM_TASKS = listOf(
            "\$display", "\$monitor", "\$time", "\$finish", "\$stop",
            "\$readmemh", "\$readmemb", "\$writememh", "\$writememb",
            "\$dumpfile", "\$dumpvars", "\$random"
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
                    .withTypeText("keyword")
                    .bold()
            )
        }

        SYSTEM_TASKS.forEach { task ->
            resultSet.addElement(
                LookupElementBuilder.create(task)
                    .withTypeText("system task")
            )
        }
    }
}
