package com.hdl.verilog

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import javax.swing.Icon

class VerilogColorSettingsPage : ColorSettingsPage {
    private val DESCRIPTORS = arrayOf(
        AttributesDescriptor("Keyword", VerilogSyntaxHighlighter.KEYWORD),
        AttributesDescriptor("Comment", VerilogSyntaxHighlighter.COMMENT),
        AttributesDescriptor("String", VerilogSyntaxHighlighter.STRING),
        AttributesDescriptor("Number", VerilogSyntaxHighlighter.NUMBER),
        AttributesDescriptor("Operator", VerilogSyntaxHighlighter.OPERATOR),
        AttributesDescriptor("Identifier", VerilogSyntaxHighlighter.IDENTIFIER),
        AttributesDescriptor("Braces", VerilogSyntaxHighlighter.BRACE),
        AttributesDescriptor("Punctuation", VerilogSyntaxHighlighter.PUNCTUATION)
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Verilog"

    override fun getIcon(): Icon = VerilogIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = VerilogSyntaxHighlighter()

    override fun getDemoText(): String = """
        // Verilog sample code
        module top(
            input clk,
            input reset,
            output reg [3:0] count
        );
            always @(posedge clk or posedge reset) begin
                if (reset) begin
                    count <= 4'b0000;
                end else begin
                    count <= count + 1;
                end
            end
        endmodule
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey>? = null
}
