package com.codeai.reviewer.service

import com.codeai.reviewer.client.CodeAiApiClient
import com.codeai.reviewer.model.ReviewResponse
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
    data class State(val status: ReviewStatus = ReviewStatus.IDLE, val message: String = "Ready", val response: ReviewResponse? = null)
    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    @Volatile private var current = State()

    fun state() = current
    fun subscribe(listener: (State) -> Unit): () -> Unit { listeners += listener; listener(current); return { listeners -= listener } }

    fun reviewChanges() {
        if (current.status == ReviewStatus.COLLECTING_CHANGES || current.status == ReviewStatus.REVIEWING) return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reviewing changes with CodeAI", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    update(State(ReviewStatus.COLLECTING_CHANGES, "Collecting and filtering changes…"))
                    val files = ChangeCollector().collect(project, indicator)
                    if (files.isEmpty()) error("No reviewable uncommitted changes found")
                    if (!confirmTransfer(files.map { it.path })) {
                        throw com.intellij.openapi.progress.ProcessCanceledException()
                    }
                    indicator.text = "Sending ${files.size} file(s) to CodeAI"
                    update(State(ReviewStatus.REVIEWING, "Reviewing ${files.size} file(s)…"))
                    val response = CodeAiApiClient().review(project.name, files)
                    update(State(ReviewStatus.SUCCESS, "Review complete", response))
                } catch (_: com.intellij.openapi.progress.ProcessCanceledException) {
                    update(State(ReviewStatus.CANCELLED, "Review cancelled"))
                } catch (e: Exception) {
                    update(State(ReviewStatus.ERROR, e.message ?: "Review failed"))
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
