package com.codeai.reviewer.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun `renders rich markdown elements`() {
        val html = MarkdownRenderer.toHtml("""
            ## Review
            - **Bold** item
            - *Italic* item with `code`
            1. First test
            2. Second test
        """.trimIndent())

        assertTrue(html.contains("<h2>Review</h2>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<b>Bold</b>"))
        assertTrue(html.contains("<i>Italic</i>"))
        assertTrue(html.contains("<code>code</code>"))
        assertTrue(html.contains("<ol>"))
    }

    @Test
    fun `escapes html while preserving code blocks`() {
        val html = MarkdownRenderer.toHtml("""
            ```java
            if (value < 10) return;
            ```
        """.trimIndent())

        assertTrue(html.contains("<pre><code>"))
        assertTrue(html.contains("value &lt; 10"))
        assertTrue(html.contains("</code></pre>"))
    }
}
