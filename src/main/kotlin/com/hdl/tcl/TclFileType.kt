package com.hdl.tcl

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class TclFileType private constructor() : LanguageFileType(TclLanguage) {
    override fun getName(): String = "Tcl"
    override fun getDescription(): String = "Tcl scripting language"
    override fun getDefaultExtension(): String = "tcl"
    override fun getIcon(): Icon? = TclIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = TclFileType()
    }
}
