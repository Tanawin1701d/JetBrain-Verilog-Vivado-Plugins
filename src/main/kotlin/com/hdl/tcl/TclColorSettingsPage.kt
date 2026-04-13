package com.hdl.tcl

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import javax.swing.Icon

class TclColorSettingsPage : ColorSettingsPage {
    private val DESCRIPTORS = arrayOf(
        AttributesDescriptor("Keyword", TclSyntaxHighlighter.KEYWORD),
        AttributesDescriptor("Command", TclSyntaxHighlighter.COMMAND),
        AttributesDescriptor("Comment", TclSyntaxHighlighter.COMMENT),
        AttributesDescriptor("String", TclSyntaxHighlighter.STRING),
        AttributesDescriptor("Number", TclSyntaxHighlighter.NUMBER),
        AttributesDescriptor("Variable", TclSyntaxHighlighter.VARIABLE),
        AttributesDescriptor("Operator", TclSyntaxHighlighter.OPERATOR),
        AttributesDescriptor("Identifier", TclSyntaxHighlighter.IDENTIFIER),
        AttributesDescriptor("Braces", TclSyntaxHighlighter.BRACE),
        AttributesDescriptor("Brackets", TclSyntaxHighlighter.BRACKET),
        AttributesDescriptor("Punctuation", TclSyntaxHighlighter.PUNCTUATION)
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Tcl"

    override fun getIcon(): Icon = TclIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = TclSyntaxHighlighter()

    override fun getDemoText(): String = """
        # Tcl sample code
        proc greet {name} {
            set message "Hello, ${'$'}name!"
            puts ${'$'}message
        }

        greet "World"
        set count 0
        while {${'$'}count < 5} {
            puts "Count is ${'$'}count"
            incr count
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey>? = null
}
