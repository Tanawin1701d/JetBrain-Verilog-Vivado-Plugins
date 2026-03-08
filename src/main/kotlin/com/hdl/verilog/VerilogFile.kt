package com.hdl.verilog

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class VerilogFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VerilogLanguage) {
    override fun getFileType(): FileType = VerilogFileType.INSTANCE
    override fun toString(): String = "Verilog File"
}
