package com.quern.app.data.update

internal enum class ReleaseNoteKind { HEADING, BULLET, PARAGRAPH }

internal data class ReleaseNoteSpan(val text: String, val bold: Boolean = false)

internal data class ReleaseNoteBlock(
    val kind: ReleaseNoteKind,
    val spans: List<ReleaseNoteSpan>
) {
    val text: String get() = spans.joinToString("") { it.text }
}

internal object ReleaseNotesParser {
    const val MAX_DISPLAY_CHARS = 4_000
    private const val MAX_INPUT_CHARS = 32_000

    private val languageHeading = Regex("""^##[ \t]+(English|EN|العربية|AR)[ \t]*#*$""", RegexOption.IGNORE_CASE)
    private val heading = Regex("""^#{1,3}[ \t]+(.+)$""")
    private val bullet = Regex("""^[-*][ \t]+(.+)$""")
    private val bold = Regex("""\*\*([^*]+)\*\*""")
    private val hiddenHtml = Regex("""<!--.*?(?:-->|$)|<(script|style)\b[^>]*>.*?(?:</\1\s*>|$)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val html = Regex("""</?[A-Za-z][^<>]*>""")
    private val image = Regex("""!\[[^\]\n]*]\s*(?:\((?:[^()\n]|\([^()\n]*\))*\)|\[[^\]\n]*])""")
    private val link = Regex("""\[([^\]\n]+)](?:\((?:[^()\n]|\([^()\n]*\))*\)|\[[^\]\n]*])""")
    private val reference = Regex("""(?m)^[ \t]*\[[^\]\n]+]:[^\n]*$""")
    private val autolink = Regex("""<(https?://[^<>\s]+)>""", RegexOption.IGNORE_CASE)

    fun parse(body: String, arabic: Boolean, maxChars: Int = MAX_DISPLAY_CHARS): List<ReleaseNoteBlock> {
        if (maxChars <= 0 || body.isBlank()) return emptyList()
        val cleaned = body.take(MAX_INPUT_CHARS)
            .let { hiddenHtml.replace(it, "") }
            .let { image.replace(it, "") }
            .let { reference.replace(it, "") }
            .let { link.replace(it) { match -> match.groupValues[1] } }
            .let { autolink.replace(it) { match -> match.groupValues[1] } }
            .let { html.replace(it, "") }
        val lines = cleaned.lineSequence().map(String::trim).toList()
        val english = mutableListOf<String>()
        val arabicLines = mutableListOf<String>()
        var current: MutableList<String>? = null
        for (line in lines) {
            val language = languageHeading.matchEntire(line)?.groupValues?.get(1)
            if (language != null) {
                current = if (language.equals("AR", true) || language == "العربية") arabicLines else english
            } else {
                current?.add(line)
            }
        }
        val preferred = if (arabic) arabicLines else english
        val fallback = if (arabic) english else arabicLines
        return if (current == null) {
            parseBlocks(lines, maxChars)
        } else {
            parseBlocks(preferred, maxChars).ifEmpty { parseBlocks(fallback, maxChars) }
        }
    }

    private fun parseBlocks(lines: List<String>, maxChars: Int): List<ReleaseNoteBlock> {
        val result = mutableListOf<ReleaseNoteBlock>()
        val paragraph = StringBuilder()
        var remaining = maxChars.coerceAtMost(MAX_DISPLAY_CHARS)

        fun add(kind: ReleaseNoteKind, content: String) {
            if (remaining <= 0 || content.isBlank()) return
            val spans = mutableListOf<ReleaseNoteSpan>()
            fun append(text: String, emphasized: Boolean = false) {
                val part = text.take(remaining)
                if (part.isNotEmpty()) spans += ReleaseNoteSpan(part, emphasized)
                remaining -= part.length
            }
            var start = 0
            for (match in bold.findAll(content)) {
                append(content.substring(start, match.range.first))
                append(match.groupValues[1], emphasized = true)
                start = match.range.last + 1
                if (remaining == 0) break
            }
            append(content.substring(start))
            if (spans.isNotEmpty()) {
                result += ReleaseNoteBlock(kind, spans)
                if (remaining > 0) remaining--
            }
        }

        fun flushParagraph() {
            add(ReleaseNoteKind.PARAGRAPH, paragraph.toString())
            paragraph.clear()
        }

        for (line in lines) {
            if (remaining == 0) break
            val headingText = heading.matchEntire(line)?.groupValues?.get(1)
            val bulletText = bullet.matchEntire(line)?.groupValues?.get(1)
            when {
                line.isBlank() || line == "-" || line == "*" || line == "#" || line == "##" || line == "###" -> flushParagraph()
                headingText != null -> {
                    flushParagraph()
                    add(ReleaseNoteKind.HEADING, headingText)
                }
                bulletText != null -> {
                    flushParagraph()
                    add(ReleaseNoteKind.BULLET, bulletText)
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
        }
        flushParagraph()
        return result
    }
}
