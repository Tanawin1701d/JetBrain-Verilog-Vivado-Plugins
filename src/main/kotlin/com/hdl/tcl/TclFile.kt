package com.hdl.tcl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class TclFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TclLanguage) {
    override fun getFileType(): FileType = TclFileType.INSTANCE
    override fun toString(): String = "Tcl File"
}
