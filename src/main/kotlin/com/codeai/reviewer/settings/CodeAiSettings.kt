package com.codeai.reviewer.settings

import com.codeai.reviewer.model.ProviderMode
import com.codeai.reviewer.model.ReviewSeverity
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "CodeAiReviewerSettings", storages = [Storage("codeai-reviewer.xml")])
@Service(Service.Level.APP)
class CodeAiSettings : PersistentStateComponent<CodeAiSettings.State> {
    data class State(
        var providerMode: ProviderMode = ProviderMode.OPENAI_COMPATIBLE,
        var serverUrl: String = "https://openrouter.ai/api",
        var model: String = "openrouter/auto",
        var minimumSeverity: ReviewSeverity = ReviewSeverity.LOW,
        var maximumFiles: Int = 20,
        var detectSecrets: Boolean = true,
        var confirmBeforeSending: Boolean = true,
    )

    private var state = State()
    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    companion object {
        fun getInstance(): CodeAiSettings = ApplicationManager.getApplication().getService(CodeAiSettings::class.java)
    }
}

object SecureTokenStore {
    private val attributes = CredentialAttributes("CodeAI Reviewer", "api-token")
    fun get(): String? = PasswordSafe.instance.getPassword(attributes)
    fun set(value: String) = PasswordSafe.instance.setPassword(attributes, value.ifBlank { null })
}
