package com.codeai.reviewer.service

import com.codeai.reviewer.client.CodeAiApiClient
import com.codeai.reviewer.model.ReviewResponse
import com.codeai.reviewer.model.ReviewMode
import com.codeai.reviewer.model.ReviewStatus
import com.codeai.reviewer.vcs.ChangeCollector
import com.codeai.reviewer.settings.CodeAiSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class ReviewService(private val project: Project) {
    data class State(
        val status: ReviewStatus = ReviewStatus.IDLE,
        val message: String = "Ready",
        val activeMode: ReviewMode? = null,
        val responses: Map<ReviewMode, ReviewResponse> = emptyMap(),
        val errors: Map<ReviewMode, String> = emptyMap(),
    )
    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    @Volatile private var current = State()

    fun state() = current
    fun subscribe(listener: (State) -> Unit): () -> Unit { listeners += listener; listener(current); return { listeners -= listener } }

    fun reviewChanges(mode: ReviewMode = ReviewMode.GENERAL) = runReviews(listOf(mode))
    fun reviewAll() = runReviews(ReviewMode.entries)

    private fun runReviews(modes: List<ReviewMode>) {
        if (current.status == ReviewStatus.COLLECTING_CHANGES || current.status == ReviewStatus.REVIEWING) return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reviewing changes with CodeAI", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    update(current.copy(status = ReviewStatus.COLLECTING_CHANGES, message = "Collecting and filtering changes…", activeMode = null))
                    val files = ChangeCollector().collect(project, indicator)
                    if (files.isEmpty()) error("No reviewable uncommitted changes found")
                    if (!confirmTransfer(files.map { it.path })) {
                        throw com.intellij.openapi.progress.ProcessCanceledException()
                    }
                    val client = CodeAiApiClient()
                    val responses = current.responses.toMutableMap().apply { modes.forEach(::remove) }
                    val errors = current.errors.toMutableMap().apply { modes.forEach(::remove) }
                    modes.forEachIndexed { index, mode ->
                        indicator.checkCanceled()
                        indicator.fraction = index.toDouble() / modes.size
                        indicator.text = "Running ${mode.displayName} review (${index + 1}/${modes.size})"
                        update(current.copy(
                            status = ReviewStatus.REVIEWING,
                            message = "${mode.displayName}: reviewing ${files.size} file(s)…",
                            activeMode = mode,
                            responses = responses.toMap(),
                            errors = errors.toMap(),
                        ))
                        runCatching { client.review(project.name, files, mode) }
                            .onSuccess { responses[mode] = it }
                            .onFailure { errors[mode] = it.message ?: "Review failed" }
                    }
                    indicator.fraction = 1.0
                    val finalStatus = if (responses.keys.any { it in modes }) ReviewStatus.SUCCESS else ReviewStatus.ERROR
                    update(State(finalStatus, if (modes.size == 1) "${modes.single().displayName} review complete" else "All reviews complete", null, responses, errors))
                } catch (_: com.intellij.openapi.progress.ProcessCanceledException) {
                    update(current.copy(status = ReviewStatus.CANCELLED, message = "Review cancelled", activeMode = null))
                } catch (e: Exception) {
                    update(current.copy(status = ReviewStatus.ERROR, message = e.message ?: "Review failed", activeMode = null))
                }
            }
        })
    }

    private fun update(state: State) {
        current = state
        listeners.forEach { it(state) }
    }

    private fun confirmTransfer(paths: List<String>): Boolean {
        if (!CodeAiSettings.getInstance().state.confirmBeforeSending) return true
        val approved = AtomicBoolean(false)
        ApplicationManager.getApplication().invokeAndWait {
            val preview = paths.take(15).joinToString("\n") { "• $it" } +
                if (paths.size > 15) "\n• …and ${paths.size - 15} more" else ""
            approved.set(Messages.showYesNoDialog(
                project,
                "CodeAI Reviewer will send bounded diffs and source context for these files to the configured AI service:\n\n$preview\n\nContinue?",
                "Send Code for AI Review",
                "Review Changes",
                "Cancel",
                Messages.getQuestionIcon(),
            ) == Messages.YES)
        }
        return approved.get()
    }
}
