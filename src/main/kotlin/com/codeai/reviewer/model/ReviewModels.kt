package com.codeai.reviewer.model

enum class ProviderMode { STRUCTURED_BACKEND, LEGACY_CODEAI, OPENAI_COMPATIBLE }
enum class ChangeType { ADDED, MODIFIED, DELETED }
enum class ReviewSeverity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
enum class ReviewCategory { BUG, SECURITY, PERFORMANCE, CONCURRENCY, DATABASE, API_DESIGN, MAINTAINABILITY, ERROR_HANDLING, TESTING }
enum class ReviewStatus { IDLE, COLLECTING_CHANGES, REVIEWING, SUCCESS, ERROR, CANCELLED }

data class ChangedFile(
    val path: String,
    val changeType: ChangeType,
    val oldContent: String,
    val newContent: String,
    val diff: String,
    val context: String,
)

data class ReviewFinding(
    val id: String = "",
    val file: String = "",
    val startLine: Int = 1,
    val endLine: Int = startLine,
    val severity: ReviewSeverity = ReviewSeverity.INFO,
    val category: ReviewCategory = ReviewCategory.MAINTAINABILITY,
    val title: String = "Finding",
    val description: String = "",
    val suggestion: String = "",
    val confidence: Double = 1.0,
)

data class ReviewSummary(
    val filesReviewed: Int = 0,
    val issues: Int = 0,
    val critical: Int = 0,
    val high: Int = 0,
    val medium: Int = 0,
    val low: Int = 0,
)

data class ReviewResponse(
    val reviewId: String = "",
    val summary: ReviewSummary = ReviewSummary(),
    val findings: List<ReviewFinding> = emptyList(),
)
