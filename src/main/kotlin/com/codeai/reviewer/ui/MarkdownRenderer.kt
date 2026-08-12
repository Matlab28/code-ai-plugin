package com.codeai.reviewer.ui

internal object MarkdownRenderer {
    private val unorderedItem = Regex("^\\s*[-+*]\\s+(.+)$")
    private val orderedItem = Regex("^\\s*\\d+[.)]\\s+(.+)$")
    private val inlineCode = Regex("`([^`]+)`")
    private val boldAsterisks = Regex("\\*\\*(.+?)\\*\\*")
    private val boldUnderscores = Regex("__(.+?)__")
    private val italicAsterisks = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")
    private val italicUnderscores = Regex("(?<![\\w])_([^_]+)_(?![\\w])")

    fun toHtml(markdown: String): String {
        val html = StringBuilder()
        var inCodeBlock = false
        var openListTag: String? = null

        fun closeList() {
            openListTag?.let { html.append("</").append(it).append('>') }
            openListTag = null
        }

        fun openList(type: String) {
            if (openListTag != type) {
                closeList()
                html.append('<').append(type).append('>')
                openListTag = type
            }
        }

        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.trimStart().startsWith("```") -> {
                    closeList()
                    html.append(if (inCodeBlock) "</code></pre>" else "<pre><code>")
                    inCodeBlock = !inCodeBlock
                }
                inCodeBlock -> html.append(escape(line)).append('\n')
                unorderedItem.matches(line) -> {
                    openList("ul")
                    html.append("<li>").append(inline(unorderedItem.matchEntire(line)!!.groupValues[1])).append("</li>")
                }
                orderedItem.matches(line) -> {
                    openList("ol")
                    html.append("<li>").append(inline(orderedItem.matchEntire(line)!!.groupValues[1])).append("</li>")
                }
                line.startsWith("### ") -> {
                    closeList()
                    html.append("<h3>").append(inline(line.drop(4))).append("</h3>")
                }
                line.startsWith("## ") -> {
                    closeList()
                    html.append("<h2>").append(inline(line.drop(3))).append("</h2>")
                }
                line.startsWith("# ") -> {
                    closeList()
                    html.append("<h1>").append(inline(line.drop(2))).append("</h1>")
                }
                line.startsWith("> ") -> {
                    closeList()
                    html.append("<blockquote>").append(inline(line.drop(2))).append("</blockquote>")
                }
                line.isBlank() -> {
                    closeList()
                    html.append("<br>")
                }
                else -> {
                    closeList()
                    html.append("<div>").append(inline(line)).append("</div>")
                }
            }
        }
        closeList()
        if (inCodeBlock) html.append("</code></pre>")
        return html.toString()
    }

    fun inline(markdown: String): String {
        val code = mutableListOf<String>()
        var rendered = inlineCode.replace(markdown) { match ->
            val token = "@@CODE${code.size}@@"
            code += "<code>${escape(match.groupValues[1])}</code>"
            token
        }
        rendered = escape(rendered)
        rendered = boldAsterisks.replace(rendered, "<b>$1</b>")
        rendered = boldUnderscores.replace(rendered, "<b>$1</b>")
        rendered = italicAsterisks.replace(rendered, "<i>$1</i>")
        rendered = italicUnderscores.replace(rendered, "<i>$1</i>")
        code.forEachIndexed { index, value -> rendered = rendered.replace("@@CODE$index@@", value) }
        return rendered
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
