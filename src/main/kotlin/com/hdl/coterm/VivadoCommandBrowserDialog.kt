package com.hdl.coterm

import com.hdl.vivado.catalog.VivadoCommandCatalog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.DefaultListModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * Search the whole UG835 Tcl reference from the console panel.
 *
 * The MCP client reaches these ~770 commands through searchVivadoCommands /
 * describeVivadoCommand; this is the same catalogue for the person watching. Without it the
 * Run Command palette (30 curated entries) would be the human's entire view of a surface the
 * assistant can drive in full.
 *
 * Picking a command drops its name into the console input rather than running it — the
 * arguments are the interesting part, and the user should type them deliberately.
 */
class VivadoCommandBrowserDialog(project: Project) : DialogWrapper(project, true) {

    /** The command the user chose, or null if they cancelled. */
    var selectedCommand: String? = null
        private set

    private val searchField = JBTextField()
    private val includeAllBox = JCheckBox("Include rarely-used commands", false)
    private val categoryBox = JComboBox(
        (listOf(ANY_CATEGORY) + VivadoCommandCatalog.categories).toTypedArray()
    )

    private val resultsModel = DefaultListModel<VivadoCommandCatalog.CatalogEntry>()
    private val resultsList = JBList(resultsModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = EntryRenderer()
    }

    private val detailArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        border = JBUI.Borders.empty(6)
    }

    private val countLabel = JLabel()

    init {
        title = "Vivado Tcl Commands (UG835)"
        isModal = true
        init()                              // creates okAction, so the rename below must follow it
        setOKButtonText("Insert into Console")
        wire()
        refresh()
    }

    override fun getPreferredFocusedComponent(): JComponent = searchField

    override fun createCenterPanel(): JComponent {
        val filters = JPanel(BorderLayout(6, 0)).apply {
            add(JLabel("Search:"), BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
            add(JPanel(BorderLayout(6, 0)).apply {
                add(categoryBox, BorderLayout.WEST)
                add(includeAllBox, BorderLayout.EAST)
            }, BorderLayout.EAST)
        }

        val split = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            JBScrollPane(resultsList).apply { preferredSize = Dimension(340, 420) },
            JBScrollPane(detailArea).apply { preferredSize = Dimension(500, 420) }
        ).apply {
            dividerLocation = 340
            border = null
        }

        return JPanel(BorderLayout(0, 6)).apply {
            border = JBUI.Borders.empty(8)
            preferredSize = Dimension(880, 520)
            add(filters, BorderLayout.NORTH)
            add(split, BorderLayout.CENTER)
            add(countLabel, BorderLayout.SOUTH)
        }
    }

    private fun wire() {
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refresh()
        })
        categoryBox.addActionListener { refresh() }
        includeAllBox.addActionListener { refresh() }

        resultsList.addListSelectionListener { e ->
            if (e.valueIsAdjusting) return@addListSelectionListener
            showDetails(resultsList.selectedValue)
        }
    }

    private fun refresh() {
        val category = (categoryBox.selectedItem as? String)?.takeIf { it != ANY_CATEGORY }
        val hits = VivadoCommandCatalog.search(
            query = searchField.text.orEmpty(),
            category = category,
            includeAll = includeAllBox.isSelected
        )

        resultsModel.clear()
        hits.take(MAX_RESULTS).forEach { resultsModel.addElement(it) }
        countLabel.text = when {
            hits.isEmpty() -> "No matching commands."
            hits.size > MAX_RESULTS -> "${hits.size} matches — showing the first $MAX_RESULTS."
            else -> "${hits.size} match${if (hits.size == 1) "" else "es"}."
        }

        if (!resultsModel.isEmpty) resultsList.selectedIndex = 0 else showDetails(null)
    }

    private fun showDetails(entry: VivadoCommandCatalog.CatalogEntry?) {
        selectedCommand = entry?.name
        detailArea.text = when {
            entry == null -> ""
            // The reference text is only read when something is actually selected — it comes
            // off a 3 MB resource, so paging the whole catalogue in would be wasteful.
            else -> VivadoCommandCatalog.details(entry.name)?.let { "${entry.name}\n\n$it" }
                ?: "${entry.name}\n\n${entry.summary}\n\n${entry.syntax}"
        }
        detailArea.caretPosition = 0
        isOKActionEnabled = entry != null
    }

    override fun doOKAction() {
        selectedCommand = resultsList.selectedValue?.name
        super.doOKAction()
    }

    private class EntryRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): java.awt.Component {
            val entry = value as? VivadoCommandCatalog.CatalogEntry
            val label = super.getListCellRendererComponent(
                list, entry?.name ?: value, index, isSelected, cellHasFocus
            ) as JLabel
            if (entry != null) {
                label.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                label.toolTipText = entry.summary.takeIf { it.isNotBlank() }
            }
            return label
        }
    }

    companion object {
        private const val ANY_CATEGORY = "All categories"
        // The list is a picker, not a report; a caller who needs everything narrows the query.
        private const val MAX_RESULTS = 300
    }
}
