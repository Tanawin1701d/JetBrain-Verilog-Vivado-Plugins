package com.hdl.verilog

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class VerilogFileType private constructor() : LanguageFileType(VerilogLanguage) {
    override fun getName(): String = "Verilog"
    override fun getDescription(): String = "Verilog Hardware Description Language"
    override fun getDefaultExtension(): String = "v"
    override fun getIcon(): Icon? = VerilogIcons.FILE

    companion object {
        val INSTANCE = VerilogFileType()
    }
}
