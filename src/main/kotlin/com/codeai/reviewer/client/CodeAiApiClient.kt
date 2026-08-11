package com.codeai.reviewer.client

import com.codeai.reviewer.model.*
import com.codeai.reviewer.settings.CodeAiSettings
import com.codeai.reviewer.settings.SecureTokenStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

class CodeAiApiClient {
    private val mapper = ObjectMapper()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun review(projectName: String, files: List<ChangedFile>): ReviewResponse {
        val s = CodeAiSettings.getInstance().state
        return when (s.providerMode) {
            ProviderMode.STRUCTURED_BACKEND -> structured(projectName, files)
            ProviderMode.LEGACY_CODEAI -> legacy(files)
            ProviderMode.OPENAI_COMPATIBLE -> openAi(files)
        }.let(::normalize)
    }

    fun testConnection(): String {
        val base = CodeAiSettings.getInstance().state.serverUrl.trimEnd('/')
        val request = requestBuilder(URI.create(base + if (CodeAiSettings.getInstance().state.providerMode == ProviderMode.OPENAI_COMPATIBLE) "/v1/models" else "/actuator/health"))
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()}")
        return "Connection successful (HTTP ${response.statusCode()})"
    }

    private fun structured(projectName: String, files: List<ChangedFile>): ReviewResponse {
        val body = mapOf(
            "project" to mapOf("name" to projectName, "language" to "JAVA"),
            "reviewScope" to "UNCOMMITTED_CHANGES",
            "files" to files.map { mapOf("path" to it.path, "changeType" to it.changeType.name, "diff" to it.diff, "context" to it.context) },
        )
        val root = post("/api/v1/reviews", mapper.writeValueAsString(body))
        val findings = root.path("findings").mapIndexed { index, node -> finding(node, index) }
        return response(root.path("summary").path("filesReviewed").asInt(files.size), findings, root.path("reviewId").asText())
    }

    private fun legacy(files: List<ChangedFile>): ReviewResponse {
        val findings = mutableListOf<ReviewFinding>()
        files.forEach { file ->
            val root = post("/api/review", mapper.writeValueAsString(mapOf("code" to file.context, "reviewType" to "GENERAL")))
            root.path("issues").forEachIndexed { index, issue -> findings += legacyFinding(issue, file.path, index) }
        }
        return response(files.size, findings)
    }

    private fun openAi(files: List<ChangedFile>): ReviewResponse {
        val s = CodeAiSettings.getInstance().state
        if (s.serverUrl.contains("openrouter.ai") && SecureTokenStore.get().isNullOrBlank()) {
            error("OpenRouter API token is missing. Add it in Settings → Tools → CodeAI Reviewer.")
        }
        val prompt = """
            Review these uncommitted code changes. Return ONLY a JSON array. Each item must contain:
            file, startLine, endLine, severity (CRITICAL|HIGH|MEDIUM|LOW|INFO), category
            (BUG|SECURITY|PERFORMANCE|CONCURRENCY|DATABASE|API_DESIGN|MAINTAINABILITY|ERROR_HANDLING|TESTING),
            title, description, suggestion, confidence (0..1). Report only actionable issues in changed code.

            ${files.joinToString("\n\n") { "FILE: ${it.path}\n${it.diff}" }}
        """.trimIndent()
        val body = mapOf(
            "model" to s.model,
            "temperature" to 0.1,
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You are an expert Java, Spring Boot, Kotlin, and secure-code reviewer."),
                mapOf("role" to "user", "content" to prompt),
            ),
        )
        val root = post("/v1/chat/completions", mapper.writeValueAsString(body))
        val content = root.path("choices").path(0).path("message").path("content").asText()
        val findings = parseFindingsContent(content)
        return response(files.size, findings)
    }

    internal fun parseFindingsContent(content: String): List<ReviewFinding> {
        val cleaned = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arrayStart = cleaned.indexOf('[')
        val arrayEnd = cleaned.lastIndexOf(']')
        val objectStart = cleaned.indexOf('{')
        val objectEnd = cleaned.lastIndexOf('}')
        val json = when {
            arrayStart >= 0 && arrayEnd > arrayStart -> cleaned.substring(arrayStart, arrayEnd + 1)
            objectStart >= 0 && objectEnd > objectStart -> cleaned.substring(objectStart, objectEnd + 1)
            else -> error("AI response did not contain a JSON review result")
        }
        val root = mapper.readTree(json)
        val nodes = if (root.isArray) root else root.path("findings")
        if (!nodes.isArray) error("AI response JSON did not contain a findings array")
        return nodes.mapIndexed { index, node -> finding(node, index) }
    }

    private fun post(path: String, json: String): JsonNode {
        val base = CodeAiSettings.getInstance().state.serverUrl.trimEnd('/')
        val request = requestBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("CodeAI returned HTTP ${response.statusCode()}: ${response.body().take(300)}")
        return mapper.readTree(response.body())
    }

    private fun requestBuilder(uri: URI): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3)).header("Accept", "application/json")
        SecureTokenStore.get()?.takeIf(String::isNotBlank)?.let { builder.header("Authorization", "Bearer $it") }
        if (uri.host.equals("openrouter.ai", ignoreCase = true)) {
            builder.header("HTTP-Referer", "https://github.com/Matlab28/code-ai-plugin")
            builder.header("X-Title", "CodeAI Reviewer")
        }
        return builder
    }

    private fun legacyFinding(n: JsonNode, path: String, index: Int) = ReviewFinding(
        id = "legacy-$index", file = n.path("filePath").asText(path), startLine = n.path("lineNumber").asInt(1),
        severity = when (n.path("severity").asText().uppercase()) { "ERROR" -> ReviewSeverity.HIGH; "WARNING" -> ReviewSeverity.MEDIUM; else -> ReviewSeverity.INFO },
        title = n.path("issueDescription").asText("Code review finding").take(100),
        description = n.path("issueDescription").asText(), suggestion = n.path("suggestion").asText(),
    )

    private fun finding(n: JsonNode, index: Int) = ReviewFinding(
        id = n.path("id").asText("local-$index"), file = n.path("file").asText(),
        startLine = n.path("startLine").asInt(1), endLine = n.path("endLine").asInt(n.path("startLine").asInt(1)),
        severity = enumValue(n.path("severity").asText(), ReviewSeverity.INFO),
        category = enumValue(n.path("category").asText(), ReviewCategory.MAINTAINABILITY),
        title = n.path("title").asText("Finding"), description = n.path("description").asText(),
        suggestion = n.path("suggestion").asText(), confidence = n.path("confidence").asDouble(1.0),
    )

    private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == value.uppercase() } ?: fallback

    private fun normalize(response: ReviewResponse): ReviewResponse {
        val minimum = CodeAiSettings.getInstance().state.minimumSeverity.ordinal
        val findings = response.findings.filter { it.severity.ordinal <= minimum && it.confidence >= 0.8 }
        return response(response.summary.filesReviewed, findings, response.reviewId)
    }

    private fun response(files: Int, findings: List<ReviewFinding>, id: String = UUID.randomUUID().toString()): ReviewResponse = ReviewResponse(
        id, ReviewSummary(files, findings.size, findings.count { it.severity == ReviewSeverity.CRITICAL },
            findings.count { it.severity == ReviewSeverity.HIGH }, findings.count { it.severity == ReviewSeverity.MEDIUM },
            findings.count { it.severity == ReviewSeverity.LOW }), findings,
    )
}
