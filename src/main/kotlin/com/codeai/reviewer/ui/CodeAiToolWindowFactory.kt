package com.codeai.reviewer.ui

import com.codeai.reviewer.model.ReviewFinding
import com.codeai.reviewer.model.ReviewMode
import com.codeai.reviewer.model.ReviewResponse
import com.codeai.reviewer.model.ReviewSeverity
import com.codeai.reviewer.model.ReviewStatus
import com.codeai.reviewer.service.ReviewService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.UIManager

class CodeAiToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CodeAiPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

private class CodeAiPanel(private val project: Project) : JBPanel<CodeAiPanel>(BorderLayout()), Disposable {
    private val reviewService = project.service<ReviewService>()
    private val status = JBLabel("Ready")
    private val results = ResponsiveColumnPanel()
    private val modeButtons = linkedMapOf<ReviewMode, JToggleButton>()
    private val runAllButton = JButton("▷  Run All")
    private var selectedMode = ReviewMode.GENERAL
    private var latestState = reviewService.state()
    private val unsubscribe: () -> Unit

    init {
        border = JBUI.Borders.empty(10)
        add(createHeader(), BorderLayout.NORTH)
        add(JBScrollPane(results).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.emptyTop(10)
        }, BorderLayout.CENTER)

        unsubscribe = reviewService.subscribe { state ->
            ApplicationManager.getApplication().invokeLater {
                latestState = state
                status.text = state.message
                val busy = state.status == ReviewStatus.COLLECTING_CHANGES || state.status == ReviewStatus.REVIEWING
                modeButtons.values.forEach { it.isEnabled = !busy }
                runAllButton.isEnabled = !busy
                renderSelectedMode()
            }
        }
    }

    private fun createHeader(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.emptyBottom(4)
        val selector = JPanel(BorderLayout(0, 6))
        val modeSelector = JPanel(GridLayout(2, 3, 4, 4))
        val group = ButtonGroup()
        ReviewMode.entries.forEach { mode ->
            val button = JToggleButton(modeButtonText(mode)).apply {
                isSelected = mode == selectedMode
                toolTipText = "Show the ${mode.displayName.lowercase()} review"
                addActionListener {
                    selectedMode = mode
                    renderSelectedMode()
                    val hasResult = latestState.responses.containsKey(mode) || latestState.errors.containsKey(mode)
                    val busy = latestState.status == ReviewStatus.COLLECTING_CHANGES ||
                        latestState.status == ReviewStatus.REVIEWING
                    if (!hasResult && !busy) reviewService.reviewChanges(mode)
                }
            }
            modeButtons[mode] = button
            group.add(button)
            modeSelector.add(button)
        }
        modeSelector.add(JPanel())
        selector.add(modeSelector, BorderLayout.CENTER)
        selector.add(runAllButton.apply {
            toolTipText = "Run all five reviews"
            addActionListener { reviewService.reviewAll() }
        }, BorderLayout.SOUTH)
        add(selector, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(status) }, BorderLayout.SOUTH)
    }

    private fun renderSelectedMode() {
        results.removeAll()
        val response = latestState.responses[selectedMode]
        val error = latestState.errors[selectedMode]
        val running = latestState.activeMode == selectedMode && latestState.status == ReviewStatus.REVIEWING

        when {
            running && response == null -> addMessage("Running ${selectedMode.displayName} review…")
            error != null -> addError(error)
            response == null -> addEmptyState()
            selectedMode.textResult -> renderTextResponse(response)
            else -> renderFindings(response)
        }
        if (running && response != null) {
            results.add(Box.createVerticalStrut(8))
            results.add(infoPanel("Refreshing ${selectedMode.displayName} results…"))
        }
        results.revalidate()
        results.repaint()
    }

    private fun addEmptyState() {
        results.add(infoPanel(
            "${selectedMode.displayName} review",
            when (selectedMode) {
                ReviewMode.GENERAL -> "Find correctness, API design, maintainability, database, and error-handling issues."
                ReviewMode.SECURITY -> "Find injection, authentication, authorization, secret, and data-exposure risks."
                ReviewMode.PERFORMANCE -> "Find inefficient queries, blocking calls, memory, concurrency, and scalability issues."
                ReviewMode.TESTS -> "Generate focused unit and integration test scenarios with useful code examples."
                ReviewMode.EXPLAIN -> "Explain what changed, why it matters, and the important risks and design decisions."
            },
            "Click ${selectedMode.displayName} above to run this review, or Run All for every section.",
        ))
    }

    private fun addMessage(message: String) { results.add(infoPanel(message)) }

    private fun addError(message: String) {
        results.add(infoPanel("${selectedMode.displayName} review failed", message).apply {
            border = JBUI.Borders.compound(JBUI.Borders.customLine(JBColor.RED), JBUI.Borders.empty(12))
        })
    }

    private fun renderFindings(response: ReviewResponse) {
        results.add(sectionHeader("Review Results", "Found ${response.findings.size} issue${if (response.findings.size == 1) "" else "s"}"))
        results.add(Box.createVerticalStrut(12))
        if (response.findings.isEmpty()) {
            results.add(infoPanel("No actionable findings", "Nothing matched the configured severity and confidence thresholds."))
            return
        }
        response.findings.forEachIndexed { index, finding ->
            results.add(findingPanel(finding))
            if (index != response.findings.lastIndex) results.add(Box.createVerticalStrut(12))
        }
    }

    private fun renderTextResponse(response: ReviewResponse) {
        val title = if (selectedMode == ReviewMode.TESTS) "Test Suggestions" else "Code Explanation"
        results.add(sectionHeader(title))
        results.add(Box.createVerticalStrut(12))
        results.add(JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border()),
                JBUI.Borders.empty(18),
            )
            add(htmlPane(MarkdownRenderer.toHtml(response.markdownExplanation.ifBlank { "No response was returned." })), BorderLayout.CENTER)
        })
    }

    private fun sectionHeader(title: String, trailing: String = "") = JPanel(BorderLayout()).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(4, 2, 8, 2)
        add(JBLabel(title).apply { font = font.deriveFont(java.awt.Font.BOLD, font.size2D + 2f) }, BorderLayout.WEST)
        if (trailing.isNotBlank()) add(JBLabel(trailing), BorderLayout.EAST)
    }

    private fun findingPanel(finding: ReviewFinding) = JPanel(BorderLayout(0, 0)).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.customLine(JBColor.border())

        add(JPanel(BorderLayout(10, 6)).apply {
            border = JBUI.Borders.empty(12)
            add(severityIcon(finding.severity), BorderLayout.WEST)
            add(JPanel(BorderLayout(4, 5)).apply {
                add(htmlPane("<b>${MarkdownRenderer.inline(finding.title)}</b>"), BorderLayout.CENTER)
                add(JBLabel("${shortFile(finding.file)}    Line ${finding.startLine}    ${finding.category.name.replace('_', ' ')}"), BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
            add(severityBadge(finding.severity), BorderLayout.EAST)
        }, BorderLayout.NORTH)

        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.compound(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0), JBUI.Borders.empty(12))
            if (finding.description.isNotBlank()) {
                add(sectionLabel("DETAILS"))
                add(htmlPane(MarkdownRenderer.toHtml(finding.description)))
                add(Box.createVerticalStrut(10))
            }
            if (finding.codeSnippet.isNotBlank()) {
                add(codeSnippetPanel(finding.codeSnippet))
                add(Box.createVerticalStrut(10))
            }
            if (finding.suggestion.isNotBlank()) {
                add(sectionLabel("SUGGESTION"))
                add(JPanel(BorderLayout()).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    isOpaque = true
                    background = JBColor(ColorValue(0xEEF1FF), ColorValue(0x313449))
                    border = JBUI.Borders.empty(10)
                    add(htmlPane(MarkdownRenderer.toHtml(finding.suggestion)), BorderLayout.CENTER)
                })
                add(Box.createVerticalStrut(10))
            }
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                add(JButton("Go to code").apply { addActionListener { navigate(finding) } })
            })
        }, BorderLayout.CENTER)
    }

    private fun codeSnippetPanel(code: String) = JPanel(BorderLayout(0, 6)).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(sectionLabel("CODE SNIPPET"), BorderLayout.WEST)
            add(JButton("Copy").apply { addActionListener { copyToClipboard(code) } }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(JBScrollPane(JEditorPane("text/plain", code).apply {
            isEditable = false
            isOpaque = false
            foreground = ColorValue(0xD8DEE9)
            font = UIManager.getFont("TextArea.font")
        }).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            preferredSize = Dimension(100, (code.lines().size.coerceIn(2, 8) * 20) + 16)
            viewport.isOpaque = false
        }, BorderLayout.CENTER)
        isOpaque = true
        background = JBColor(ColorValue(0x111827), ColorValue(0x111827))
        border = JBUI.Borders.empty(10)
    }

    private fun sectionLabel(text: String) = JBLabel(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        foreground = JBColor.GRAY
        font = font.deriveFont(java.awt.Font.BOLD)
    }

    private fun severityIcon(severity: ReviewSeverity) = JBLabel(when (severity) {
        ReviewSeverity.CRITICAL, ReviewSeverity.HIGH -> "✕"
        ReviewSeverity.MEDIUM -> "⚠"
        ReviewSeverity.LOW, ReviewSeverity.INFO -> "ⓘ"
    }).apply {
        foreground = severityColor(severity)
        font = font.deriveFont(java.awt.Font.BOLD, font.size2D + 4f)
    }

    private fun severityBadge(severity: ReviewSeverity) = JBLabel(severity.name).apply {
        isOpaque = true
        border = JBUI.Borders.empty(3, 7)
        foreground = JBColor.WHITE
        background = severityColor(severity)
    }

    private fun severityColor(severity: ReviewSeverity): Color = when (severity) {
            ReviewSeverity.CRITICAL -> JBColor(ColorValue(0xB71C1C), ColorValue(0xD32F2F))
            ReviewSeverity.HIGH -> JBColor(ColorValue(0xD84315), ColorValue(0xE64A19))
            ReviewSeverity.MEDIUM -> JBColor(ColorValue(0xB26A00), ColorValue(0xC77C00))
            ReviewSeverity.LOW -> JBColor(ColorValue(0x2E7D32), ColorValue(0x388E3C))
            ReviewSeverity.INFO -> JBColor(ColorValue(0x1565C0), ColorValue(0x1976D2))
        }

    private fun modeButtonText(mode: ReviewMode) = when (mode) {
        ReviewMode.GENERAL -> "◫ General"
        ReviewMode.SECURITY -> "♢ Security"
        ReviewMode.PERFORMANCE -> "ϟ Performance"
        ReviewMode.TESTS -> "✓ Tests"
        ReviewMode.EXPLAIN -> "▤ Explain"
    }

    private fun shortFile(path: String) = path.substringAfterLast('/')

    private fun copyToClipboard(text: String) = Toolkit.getDefaultToolkit().systemClipboard
        .setContents(StringSelection(text), null)

    private fun infoPanel(vararg lines: String) = JPanel(BorderLayout()).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.compound(JBUI.Borders.customLine(JBColor.border()), JBUI.Borders.empty(14))
        add(htmlPane(lines.mapIndexed { index, line ->
            if (index == 0) "<b>${escapeHtml(line)}</b>" else escapeHtml(line)
        }.joinToString("<br><br>")), BorderLayout.CENTER)
    }

    private fun htmlPane(body: String) = JEditorPane(
        "text/html",
        """<html><head><style>
            body { margin: 0; }
            h1, h2, h3 { margin-top: 8px; margin-bottom: 5px; }
            ul, ol { margin-top: 4px; margin-bottom: 8px; margin-left: 20px; }
            li { margin-bottom: 4px; }
            pre { white-space: pre-wrap; margin: 7px 0; padding: 8px; background-color: #2b2b2b; color: #d8d8d8; }
            code { font-family: monospace; }
            blockquote { margin-left: 10px; color: #808080; }
        </style></head><body>$body</body></html>""".trimIndent(),
    ).apply {
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty()
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        font = UIManager.getFont("Label.font")
        minimumSize = Dimension(0, 0)
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun navigate(finding: ReviewFinding) {
        val base = project.basePath ?: return
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath("$base/${finding.file}") ?: return
        OpenFileDescriptor(project, file, (finding.startLine - 1).coerceAtLeast(0), 0).navigate(true)
    }

    override fun dispose() = unsubscribe()
}

private class ResponsiveColumnPanel : JPanel(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(2)
    }

    override fun getPreferredScrollableViewportSize() = Dimension(520, 600)
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 18
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height - 36
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}

private fun ColorValue(rgb: Int) = java.awt.Color(rgb)
