package com.shelf.launcher.data.guide

import java.text.Normalizer

/** Text helpers shared by the parser, locator and missables mapping. */
internal object GuideText {
    /** Lowercase, accents stripped, apostrophes dropped, everything else non-alphanumeric -> single space. */
    fun normalize(s: String): String {
        val d = Normalizer.normalize(s, Normalizer.Form.NFD)
        val sb = StringBuilder(d.length)
        var lastSpace = true
        for (ch in d) {
            val c = ch.lowercaseChar()
            when {
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> {}
                c == '\'' || c == '’' -> {}
                c.isLetterOrDigit() -> { sb.append(c); lastSpace = false }
                else -> if (!lastSpace) { sb.append(' '); lastSpace = true }
            }
        }
        return sb.toString().trim()
    }

    fun slug(s: String): String = normalize(s).replace(' ', '-').take(60).ifEmpty { "section" }

    /** Normalized text of a multi-line body: each line normalized on its own, joined by "\n" so that
     * word pairs never span lines. Padded with spaces so " word " lookups work at the edges. */
    fun normalizeLines(s: String): String =
        " " + s.lineSequence().map { normalize(it) }.filter { it.isNotEmpty() }.joinToString(" \n ") + " "
}

/** Turns markdown or plain-text guides into ordered heading sections. */
object GuideParser {
    private val ACH_LINK = Regex("retroachievements\\.org/achievement/(\\d+)", RegexOption.IGNORE_CASE)
    private val HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val IMG = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val REF_DEF = Regex("(?m)^\\s{0,3}\\[[^\\]]+]:\\s*\\S+.*$")
    private const val TAGS = "br|p|div|span|img|a|b|i|u|em|strong|center|table|tr|td|th|thead|tbody|ul|ol|li|h[1-6]|hr|details|summary|font|sup|sub|iframe|video|source|picture|blockquote|kbd"
    private val HTML_TAG = Regex("</?(?:$TAGS)(?:\\s[^>]*)?/?>", RegexOption.IGNORE_CASE)
    private val ATX = Regex("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$")
    private val SETEXT = Regex("^\\s{0,3}(=+|-+)\\s*$")
    private val UNDERLINE = Regex("^\\s*([=\\-~*#_+])\\1{2,}\\s*$")
    private val BANNER = Regex("^\\s*[=\\-*#~_+]{2,}\\s+(.+?)\\s+[=\\-*#~_+]{2,}\\s*$")
    private val NUMBERED = Regex("^\\s*(\\d{1,2}(?:\\.\\d{1,2})+)\\.?\\s+(\\S.{1,70})$")
    private val NUMBERED_CAPS = Regex("^\\s*(\\d{1,2})[.)]\\s+([A-Z][A-Z0-9 '&:,-]{2,60})$")

    /** Removes images, HTML and link targets; keeps list markers and link text. */
    fun cleanMarkdown(raw: String): String {
        var t = raw
        t = HTML_COMMENT.replace(t, "")
        t = IMG.replace(t, "")
        t = LINK.replace(t) { it.groupValues[1] }
        t = REF_DEF.replace(t, "")
        t = HTML_TAG.replace(t, " ")
        return tidy(t.lines().joinToString("\n") { it.trimEnd() })
    }

    private fun tidy(s: String) = s.replace(Regex("\n{3,}"), "\n\n").trim()

    private fun cleanTitle(t: String): String =
        cleanMarkdown(t).replace(Regex("[*`]"), "").replace(Regex("\\s+"), " ").trim()

    /** Collects lines into sections, tracking the heading path. */
    private class Builder(private val fallbackTitle: String) {
        class Raw(val level: Int, val title: String, val path: List<String>) { val body = StringBuilder() }

        private val done = ArrayList<Raw>()
        private val stack = ArrayList<Raw>()
        private var pre: Raw? = null
        private var cur: Raw? = null

        fun heading(level: Int, title: String) {
            while (stack.isNotEmpty() && stack.last().level >= level) stack.removeAt(stack.size - 1)
            val path = stack.map { it.title } + title
            val r = Raw(level, title, path)
            stack.add(r); done.add(r); cur = r
        }

        fun line(l: String) {
            val c = cur ?: run {
                if (l.isBlank()) return
                val p = pre ?: Raw(0, fallbackTitle, listOf(fallbackTitle)).also { pre = it }
                p.body.append(l).append('\n'); return
            }
            c.body.append(l).append('\n')
        }

        fun build(clean: (String) -> String): List<GuideSection> {
            val all = ArrayList<Raw>()
            pre?.let { all.add(it) }
            all.addAll(done)
            val seen = HashMap<String, Int>()
            // Update 144: every id handed out so far, so a heading whose own slug is already "x-2" ("Part", "Part",
            // "Part 2") gets one of its own instead of the second "Part"'s.
            val used = HashSet<String>()
            return all.mapIndexed { i, r ->
                val base = r.path.joinToString("/") { GuideText.slug(it) }
                var n = (seen[base] ?: 0) + 1
                var id = if (n == 1) base else "$base-$n"
                while (!used.add(id)) { n++; id = "$base-$n" }
                seen[base] = n
                val raw = r.body.toString()
                val ids = ACH_LINK.findAll(raw).mapNotNull { it.groupValues[1].toIntOrNull() }.distinct().toList()
                GuideSection(
                    id = id, level = r.level, title = r.title, path = r.path,
                    text = clean(raw), order = i, mentionedAchievementIds = ids
                )
            }
        }
    }

    fun parseMarkdown(text: String, fallbackTitle: String): List<GuideSection> {
        var lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        if (lines.firstOrNull()?.trim() == "---") {
            val end = lines.drop(1).take(40).indexOfFirst { it.trim() == "---" }
            if (end >= 0) lines = lines.drop(end + 2)
        }
        val b = Builder(fallbackTitle)
        var fence: String? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            if (fence != null) {
                if (t.startsWith(fence)) fence = null
                b.line(line); i++; continue
            }
            if (t.startsWith("```") || t.startsWith("~~~")) { fence = t.take(3); b.line(line); i++; continue }
            val atx = ATX.find(line)
            if (atx != null) {
                val title = cleanTitle(atx.groupValues[2])
                if (title.isNotEmpty()) { b.heading(atx.groupValues[1].length, title); i++; continue }
            }
            // Setext heading: a single text line after a blank, followed by ==== or ----.
            val next = lines.getOrNull(i + 1)
            if (next != null && t.isNotEmpty() && SETEXT.matches(next) &&
                (i == 0 || lines[i - 1].isBlank()) &&
                !t.startsWith("-") && !t.startsWith("*") && !t.startsWith(">") && !t.startsWith("|") && !SETEXT.matches(line)
            ) {
                val title = cleanTitle(t)
                if (title.isNotEmpty()) { b.heading(if (next.trim().startsWith("=")) 1 else 2, title); i += 2; continue }
            }
            b.line(line); i++
        }
        return b.build { cleanMarkdown(it) }
    }

    private class TxtHead(val level: Int, val title: String, val consumed: Int)

    /** Deterministic heading heuristics for plain-text FAQs. */
    private fun txtHeading(lines: List<String>, i: Int): TxtHead? {
        val t = lines[i].trim()
        if (t.isEmpty() || UNDERLINE.matches(lines[i])) return null
        val prevBlank = i == 0 || lines[i - 1].isBlank()
        val next = lines.getOrNull(i + 1)
        if (next != null && t.length <= 80 && UNDERLINE.matches(next) && t.any { it.isLetterOrDigit() }) {
            val ch = next.trim()[0]
            return TxtHead(if (ch == '=' || ch == '#' || ch == '*') 1 else 2, t, 1)
        }
        BANNER.find(lines[i])?.let { m ->
            val title = m.groupValues[1].trim()
            if (title.any { it.isLetter() }) return TxtHead(2, title, 0)
        }
        if (!prevBlank || t.length > 80) return null
        NUMBERED.find(lines[i])?.let { m ->
            val title = m.groupValues[2].trim()
            if (title[0].isUpperCase() || title[0].isDigit()) {
                return TxtHead(m.groupValues[1].count { it == '.' } + 1, title, 0)
            }
        }
        NUMBERED_CAPS.find(lines[i])?.let { m -> return TxtHead(1, m.groupValues[2].trim(), 0) }
        val nextBlank = next == null || next.isBlank()
        val letters = t.count { it.isLetter() }
        if (nextBlank && t.length in 3..60 && letters >= 3 && letters * 2 >= t.length &&
            t.none { it.isLowerCase() } && !t.endsWith(".") && !t.endsWith(",")
        ) return TxtHead(2, t, 0)
        return null
    }

    fun parsePlainText(text: String, fallbackTitle: String): List<GuideSection> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val b = Builder(fallbackTitle)
        var i = 0
        while (i < lines.size) {
            val h = txtHeading(lines, i)
            if (h != null) { b.heading(h.level, h.title); i += 1 + h.consumed } else { b.line(lines[i]); i++ }
        }
        return b.build { tidy(it.lines().joinToString("\n") { l -> l.trimEnd() }) }
    }

    /** First level-1 heading, if any, as a guide title. */
    fun titleOf(sections: List<GuideSection>): String? = sections.firstOrNull { it.level == 1 }?.title
}
