package com.codeai.reviewer.client

import com.codeai.reviewer.model.ReviewCategory
import com.codeai.reviewer.model.ReviewSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeAiApiClientTest {
    private val client = CodeAiApiClient()

    @Test
    fun `parses fenced finding array`() {
        val findings = client.parseFindingsContent("""
            ```json
            [{"file":"src/App.java","startLine":7,"severity":"HIGH","category":"BUG","title":"Failure","description":"Broken","suggestion":"Fix it","confidence":0.95}]
            ```
        """.trimIndent())

        assertEquals(1, findings.size)
        assertEquals("src/App.java", findings.single().file)
        assertEquals(ReviewSeverity.HIGH, findings.single().severity)
        assertEquals(ReviewCategory.BUG, findings.single().category)
    }

    @Test
    fun `parses findings object surrounded by prose`() {
        val findings = client.parseFindingsContent("Result: {\"findings\":[]} End.")
        assertEquals(emptyList<Any>(), findings)
    }
}
