package com.prism.music.data.lyrics

/**
 * A line whose source writes furigana in parentheses right after the kanji it belongs to, as KuGou does:
 * 君(きみ)の名前(なまえ). [clean] is the line with those readings taken out, so the dictionary reads the words without
 * them; [spans] are the source's readings at the line's own offsets. [merge] keeps those readings and fills the rest of
 * the line with the dictionary's.
 *
 * Only hiragana directly after a run of kanji is taken for a reading: parentheses elsewhere (after kana, after a space,
 * or holding anything but hiragana, like (笑) or (PV)) are not furigana and stay in the line for the dictionary.
 */
internal class SourceFurigana private constructor(
    val clean: String,
    val spans: List<RubySpan>,
    /** Each kept character's offset in the original line; the two ends map a reading of [clean] back to it. */
    private val starts: IntArray,
    private val ends: IntArray,
) {
    fun map(span: RubySpan): RubySpan = RubySpan(starts[span.start], ends[span.end], span.reading)

    /** The source's readings, and the dictionary's for the kanji the source didn't annotate (or overlap). */
    fun merge(dictionary: List<RubySpan>): List<RubySpan> {
        val fromDictionary = dictionary.map(::map).filter { reading ->
            spans.none { source -> source.start < reading.end && reading.start < source.end }
        }
        return (spans + fromDictionary).sortedBy { it.start }
    }

    private class Found(val start: Int, val end: Int, val open: Int, val close: Int, val reading: String)

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
                val close = when (text.getOrNull(i)) {
                    '(' -> ')'
                    '（' -> '）'
                    else -> null
                } ?: continue
                var j = i + 1
                while (j < text.length && isReading(text[j])) j++
                if (j == i + 1 || j >= text.length || text[j] != close) continue
                val reading = text.substring(i + 1, j)
                // A reading can be longer than its kanji (今日 → きょう, 志 → こころざし), but not a whole phrase.
                if (reading.length > (end - start) * 6) continue
                found += Found(start, end, i, j + 1, reading)
                i = j + 1
            }
            if (found.isEmpty()) return null
            val clean = StringBuilder(text.length)
            val kept = ArrayList<Int>(text.length)
            var at = 0
            var next = 0
            while (at < text.length) {
                val annotation = found.getOrNull(next)
                if (annotation != null && at == annotation.open) {
                    at = annotation.close
                    next++
                    continue
                }
                kept += at
                clean.append(text[at])
                at++
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
                starts = starts,
                ends = ends,
            )
        }

        /** Furigana is written in hiragana (ー, ゝ and ゞ included); anything else in the parentheses isn't a reading. */
        private fun isReading(c: Char) = c.code in 0x3041..0x3096 || c == 'ー' || c == 'ゝ' || c == 'ゞ'
    }
}
