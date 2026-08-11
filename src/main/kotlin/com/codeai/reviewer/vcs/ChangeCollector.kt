package com.codeai.reviewer.vcs

import com.codeai.reviewer.model.ChangeType
import com.codeai.reviewer.model.ChangedFile
import com.codeai.reviewer.security.ReviewFilters
import com.codeai.reviewer.settings.CodeAiSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangeListManager
import java.nio.file.Path

class ChangeCollector {
    fun collect(project: Project, indicator: ProgressIndicator): List<ChangedFile> {
        val base = project.basePath ?: error("Project has no base path")
        val settings = CodeAiSettings.getInstance().state
        return ChangeListManager.getInstance(project).allChanges.asSequence()
            .onEach { indicator.checkCanceled() }
            .mapNotNull { change ->
                val revision = change.afterRevision ?: change.beforeRevision ?: return@mapNotNull null
                val absolute = revision.file.path
                val path = runCatching { Path.of(base).relativize(Path.of(absolute)).toString() }.getOrDefault(absolute)
                    .replace('\\', '/')
                val old = change.beforeRevision?.content.orEmpty()
                val new = change.afterRevision?.content.orEmpty()
                if (ReviewFilters.shouldExclude(project, path, new.ifBlank { old }, settings.detectSecrets)) return@mapNotNull null
                val type = when (change.fileStatus) {
                    FileStatus.ADDED -> ChangeType.ADDED
                    FileStatus.DELETED -> ChangeType.DELETED
                    else -> ChangeType.MODIFIED
                }
                if (type == ChangeType.DELETED) return@mapNotNull null
                ChangedFile(path, type, old, new, unifiedDiff(path, old, new), context(new))
            }
            .take(settings.maximumFiles)
            .toList()
    }

    private fun unifiedDiff(path: String, old: String, new: String): String = buildString {
        appendLine("--- a/$path")
        appendLine("+++ b/$path")
        appendLine("@@ -1,${old.lineSequence().count()} +1,${new.lineSequence().count()} @@")
        old.lineSequence().take(400).forEach { appendLine("-$it") }
        new.lineSequence().take(400).forEach { appendLine("+$it") }
    }.take(60_000)

    private fun context(source: String): String = source.lineSequence().take(500).joinToString("\n").take(30_000)
}
