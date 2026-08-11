package com.codeai.reviewer.ui

import com.codeai.reviewer.model.ReviewFinding
import com.codeai.reviewer.model.ReviewStatus
import com.codeai.reviewer.service.ReviewService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTextArea

class CodeAiToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CodeAiPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

private class CodeAiPanel(private val project: Project) : JBPanel<CodeAiPanel>(BorderLayout()), com.intellij.openapi.Disposable {
    private val status = JBLabel("Ready")
    private val results = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val unsubscribe: () -> Unit

    init {
        border = JBUI.Borders.empty(10)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(JButton("Review Changes").apply { addActionListener { project.service<ReviewService>().reviewChanges() } })
            add(status)
        }, BorderLayout.NORTH)
        add(JBScrollPane(results), BorderLayout.CENTER)
        unsubscribe = project.service<ReviewService>().subscribe { state ->
            ApplicationManager.getApplication().invokeLater {
                status.text = state.message
                results.removeAll()
                when {
                    state.response != null -> render(state.response.findings, state.response.summary.filesReviewed)
                    state.status == ReviewStatus.IDLE -> results.add(JBLabel("Review your local changes before committing them."))
                    state.status == ReviewStatus.ERROR -> results.add(JBLabel("Error: ${state.message}"))
                }
                results.revalidate(); results.repaint()
            }
        }
    }

    private fun render(findings: List<ReviewFinding>, fileCount: Int) {
        results.add(JBLabel("$fileCount file(s) reviewed • ${findings.size} finding(s)").apply { border = JBUI.Borders.empty(10, 4) })
        if (findings.isEmpty()) results.add(JBLabel("No actionable findings above the configured threshold."))
        findings.groupBy { it.file }.forEach { (file, items) ->
            results.add(JSeparator())
            results.add(JBLabel(file).apply { border = JBUI.Borders.empty(10, 4, 4, 4) })
            items.forEach { results.add(findingPanel(it)) }
        }
    }

    private fun findingPanel(finding: ReviewFinding) = JPanel(BorderLayout(8, 4)).apply {
        border = JBUI.Borders.compound(JBUI.Borders.customLine(Color(0x777777)), JBUI.Borders.empty(8))
        add(JBLabel("${finding.severity} • ${finding.category} • line ${finding.startLine} — ${finding.title}"), BorderLayout.NORTH)
        add(JTextArea(buildString {
            append(finding.description)
            if (finding.suggestion.isNotBlank()) append("\n\nSuggestion: ${finding.suggestion}")
        }).apply { lineWrap = true; wrapStyleWord = true; isEditable = false; isOpaque = false }, BorderLayout.CENTER)
        add(JButton("Go to code").apply { addActionListener { navigate(finding) } }, BorderLayout.SOUTH)
    }

    private fun navigate(finding: ReviewFinding) {
        val base = project.basePath ?: return
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath("$base/${finding.file}") ?: return
        OpenFileDescriptor(project, file, (finding.startLine - 1).coerceAtLeast(0), 0).navigate(true)
    }

    override fun dispose() = unsubscribe()
}
