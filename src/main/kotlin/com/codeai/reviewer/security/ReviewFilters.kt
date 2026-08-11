package com.codeai.reviewer.security

import com.intellij.openapi.project.Project
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

object ReviewFilters {
    private val sensitiveNames = listOf(
        Regex("(^|/)\\.env($|\\.)", RegexOption.IGNORE_CASE),
        Regex("\\.(pem|key|p12|pfx)$", RegexOption.IGNORE_CASE),
        Regex("(^|/)(credentials\\.json|secrets?\\.(yml|yaml)|id_rsa|id_ed25519)$", RegexOption.IGNORE_CASE),
        Regex("(^|/)(\\.aws|\\.ssh)/", RegexOption.IGNORE_CASE),
    )
    private val secretContent = listOf(
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
        Regex("AKIA[0-9A-Z]{16}"),
        Regex("(?i)(password|secret|api[_-]?key)\\s*[:=]\\s*[^\\s]{8,}"),
        Regex("(?i)bearer\\s+[a-z0-9._~+/-]{12,}"),
    )

    fun shouldExclude(project: Project, path: String, content: String, scanSecrets: Boolean): Boolean {
        if (isSensitive(path, content, scanSecrets)) return true
        if (matchesIgnoreFile(project, path)) return true
        return false
    }

    fun isSensitive(path: String, content: String, scanSecrets: Boolean = true): Boolean =
        sensitiveNames.any { it.containsMatchIn(path) } ||
            path.contains("/build/") || path.contains("/target/") || path.endsWith(".class") || path.endsWith(".jar") ||
            (scanSecrets && secretContent.any { it.containsMatchIn(content) })

    private fun matchesIgnoreFile(project: Project, relativePath: String): Boolean {
        val base = project.basePath ?: return false
        val ignore = Path.of(base, ".codeaiignore")
        if (!Files.isRegularFile(ignore)) return false
        return runCatching {
            Files.readAllLines(ignore).asSequence().map(String::trim)
                .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("!") }
                .any { pattern ->
                    val clean = pattern.removePrefix("/")
                    val glob = if (clean.endsWith("/")) "${clean}**" else clean
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
                    matcher.matches(Path.of(relativePath)) || (!glob.startsWith("**/") &&
                        FileSystems.getDefault().getPathMatcher("glob:**/$glob").matches(Path.of(relativePath)))
                }
        }.getOrDefault(false)
    }
}
