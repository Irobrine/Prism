package com.prism.music.data.lyrics

/**
 * A line whose source writes furigana in parentheses right after the kanji it belongs to, as KuGou does:
 * 君(きみ)の名前(なまえ) — with either kind of parentheses, and spaces before them allowed. [clean] is the line with
 * those readings taken out, so the dictionary and the lyrics show the words without them; [spans] are the source's
 * readings at the line's own offsets.
 *
 * Only hiragana after a run of kanji is taken for a reading: parentheses elsewhere (after kana, after a line break, or
 * holding anything but hiragana, like (笑) or (PV)) are not furigana and stay in the line for the dictionary.
 */
internal class SourceFurigana private constructor(
    val clean: String,
    /** The source's readings at the original line's offsets. */
    val spans: List<RubySpan>,
    /** The same readings at [clean]'s offsets, for a line whose text is [clean]. */
    val cleanSpans: List<RubySpan>,
    /** Each kept character's offset in the original line; the two ends map a reading of [clean] back to it. */
    private val starts: IntArray,
    private val ends: IntArray,
    /** The original line's characters that are the annotations (parentheses, reading and the space before them). */
    private val removed: BooleanArray,
) {
    fun map(span: RubySpan): RubySpan = RubySpan(starts[span.start], ends[span.end], span.reading)

    /** The source's readings, and the dictionary's for the kanji the source didn't annotate (or overlap). */
    fun merge(dictionary: List<RubySpan>): List<RubySpan> = mergeFurigana(spans, dictionary.map(::map))

    /**
     * [line] with its annotation text taken out and its word texts shortened the same way, so its timings (and every
     * animation drawn from them) still line up. [cleanSpans] ride along as its source furigana.
     */
    fun cleanLine(line: LyricLine): LyricLine =
        line.copy(text = clean, words = line.words.clean(removed), sourceRuby = cleanSpans)

    private fun List<LyricWord>.clean(removed: BooleanArray): List<LyricWord> {
        var at = 0
        return map { word ->
            val text = StringBuilder(word.text.length)
            for (c in word.text) {
                if (at >= removed.size || !removed[at]) text.append(c)
                at++
            }
            word.copy(text = text.toString())
        }
    }

    private class Found(val start: Int, val end: Int, val from: Int, val close: Int, val reading: String)

    companion object {
        /** The line's source furigana and its text without it; null when the line has none. */
        fun of(text: String): SourceFurigana? {
            val found = ArrayList<Found>()
            var i = 0
            while (i < text.length) {
                if (!isKanji(text[i])) { i++; continue }
                val start = i
                while (i < text.length && isKanji(text[i])) i++
                val end = i
                var open = i
                while (open < text.length && text[open].isWhitespace()) open++
                val close = when (text.getOrNull(open)) {
                    '(' -> ')'
                    '（' -> '）'
                    else -> null
                } ?: continue
                var j = open + 1
                while (j < text.length && isReading(text[j])) j++
                if (j == open + 1 || j >= text.length || text[j] != close) continue
                val reading = text.substring(open + 1, j)
                // A reading can be longer than its kanji (今日 → きょう, 志 → こころざし), but not a whole phrase.
                if (reading.length > (end - start) * 6) continue
                found += Found(start, end, end, j + 1, reading)
                i = j + 1
            }
            if (found.isEmpty()) return null
            val removed = BooleanArray(text.length)
            for (annotation in found) for (k in annotation.from until annotation.close) removed[k] = true
            val clean = StringBuilder(text.length)
            val kept = ArrayList<Int>(text.length)
            val cleanOf = IntArray(text.length) { -1 }
            for (at in text.indices) {
                if (removed[at]) continue
                cleanOf[at] = clean.length
                kept += at
                clean.append(text[at])
            }
            val starts = IntArray(kept.size + 1)
            val ends = IntArray(kept.size + 1)
            for (k in kept.indices) {
                starts[k] = kept[k]
                ends[k + 1] = kept[k] + 1
            }
            starts[kept.size] = text.length
            return SourceFurigana(
                clean = clean.toString(),
                spans = found.map { RubySpan(it.start, it.end, it.reading) },
                cleanSpans = found.map { RubySpan(cleanOf[it.start], cleanOf[it.end - 1] + 1, it.reading) },
                starts = starts,
                ends = ends,
                removed = removed,
            )
        }

        /** Furigana is written in hiragana (ー, ゝ and ゞ included); anything else in the parentheses isn't a reading. */
        private fun isReading(c: Char) = c.code in 0x3041..0x3096 || c == 'ー' || c == 'ゝ' || c == 'ゞ'
    }
}

/** [source]'s readings where it has them, the dictionary's for the kanji it didn't annotate (or overlap). */
internal fun mergeFurigana(source: List<RubySpan>, dictionary: List<RubySpan>): List<RubySpan> {
    val rest = dictionary.filter { reading -> source.none { it.start < reading.end && reading.start < it.end } }
    return (source + rest).sortedBy { it.start }
}
