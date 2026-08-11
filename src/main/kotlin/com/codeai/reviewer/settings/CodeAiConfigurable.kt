package com.codeai.reviewer.settings

import com.codeai.reviewer.client.CodeAiApiClient
import com.codeai.reviewer.model.ProviderMode
import com.codeai.reviewer.model.ReviewSeverity
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class CodeAiConfigurable : Configurable {
    private val mode = JComboBox(ProviderMode.entries.toTypedArray())
    private val url = JBTextField()
    private val model = JBTextField()
    private val token = JBPasswordField()
    private val severity = JComboBox(ReviewSeverity.entries.toTypedArray())
    private val maxFiles = JBTextField()
    private val secrets = JBCheckBox("Detect likely secrets and skip sensitive files")
    private val confirmation = JBCheckBox("Confirm before sending source code to the configured service")
    private var panel: JPanel? = null

    override fun getDisplayName() = "CodeAI Reviewer"

    override fun createComponent(): JComponent {
        reset()
        val test = JButton("Test Connection").apply {
            addActionListener {
                apply()
                val result = runCatching { CodeAiApiClient().testConnection() }
                Messages.showInfoMessage(result.fold({ it }, { "Connection failed: ${it.message}" }), "CodeAI Reviewer")
            }
        }
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Provider"), mode)
            .addLabeledComponent(JBLabel("Server URL"), url)
            .addLabeledComponent(JBLabel("Model"), model)
            .addLabeledComponent(JBLabel("API token (optional)"), token)
            .addLabeledComponent(JBLabel("Minimum severity"), severity)
            .addLabeledComponent(JBLabel("Maximum files"), maxFiles)
            .addComponent(secrets)
            .addComponent(confirmation)
            .addComponent(JPanel(BorderLayout()).apply { add(test, BorderLayout.WEST) })
            .addComponentFillVertically(JPanel(), 0)
            .panel.also { panel = it }
    }

    override fun isModified(): Boolean {
        val s = CodeAiSettings.getInstance().state
        return mode.selectedItem != s.providerMode || url.text.trim() != s.serverUrl || model.text.trim() != s.model ||
            severity.selectedItem != s.minimumSeverity || maxFiles.text.toIntOrNull() != s.maximumFiles ||
            secrets.isSelected != s.detectSecrets || confirmation.isSelected != s.confirmBeforeSending ||
            String(token.password) != (SecureTokenStore.get() ?: "")
    }

    override fun apply() {
        val s = CodeAiSettings.getInstance().state
        s.providerMode = mode.selectedItem as ProviderMode
        s.serverUrl = url.text.trim().trimEnd('/')
        s.model = model.text.trim()
        s.minimumSeverity = severity.selectedItem as ReviewSeverity
        s.maximumFiles = maxFiles.text.toIntOrNull()?.coerceIn(1, 100) ?: 20
        s.detectSecrets = secrets.isSelected
        s.confirmBeforeSending = confirmation.isSelected
        SecureTokenStore.set(String(token.password))
    }

    override fun reset() {
        val s = CodeAiSettings.getInstance().state
        mode.selectedItem = s.providerMode
        url.text = s.serverUrl
        model.text = s.model
        token.text = SecureTokenStore.get() ?: ""
        severity.selectedItem = s.minimumSeverity
        maxFiles.text = s.maximumFiles.toString()
        secrets.isSelected = s.detectSecrets
        confirmation.isSelected = s.confirmBeforeSending
    }

    override fun disposeUIResources() { panel = null }
}
