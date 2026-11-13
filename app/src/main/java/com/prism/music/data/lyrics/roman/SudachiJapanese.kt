package com.prism.music.data.lyrics.roman

import com.prism.music.data.lyrics.RubySpan
import com.prism.music.data.lyrics.isKanji
import com.worksap.nlp.sudachi.Config
import com.worksap.nlp.sudachi.Dictionary
import com.worksap.nlp.sudachi.Morpheme
import com.worksap.nlp.sudachi.Tokenizer
import java.io.File

/**
 * Japanese with kanji, in Latin letters, read with Sudachi and the full SudachiDict (Apache-2.0; the dictionary is
 * built from UniDic and NEologd, whose notices are in [ModelStore.Model.SUDACHI]'s download and in the app's licenses).
 * Sudachi splits each line into words and gives every word its reading, which [Kana] then spells out in Hepburn, with
 * the particles said as they're said (は "wa", へ "e", を "o") and a space between words ("君の名は" → "kimi no na wa").
 *
 * Its words and readings are a little different from Kuromoji's IPADIC (the other engine, [Japanese]): the full
 * dictionary knows more names and slang, and "二人" is already read "フタリ" without a user dictionary.
 */
class SudachiJapanese private constructor(
    private val dictionary: Dictionary,
    private val tokenizer: Tokenizer,
) : JapaneseReader, RubyReader {
    private val lock = Any()
    private var closed = false

    override fun pieces(text: String, splitCounters: Boolean): List<String> {
        // Sudachi's tokenizer keeps one lattice while it works: one line at a time, so two songs never share it.
        val tokens = synchronized(lock) {
            runCatching { tokenizer.tokenize(Tokenizer.SplitMode.C, text) }.getOrElse {
                // A line it can't parse stays as it is; the caller then asks Google for it, as with an unknown kanji.
                android.util.Log.w("PrismRoman", "Sudachi couldn't read a line: $it")
                return text.map { c -> c.toString() }
            }
        }
        val out = MutableList(text.length) { text[it].toString() }
        val runs = JapaneseCounters.runs(text, tokens.map { JapaneseCounters.Token(it.begin(), it.surface()) })
        var first = true
        // A word whose reading ends in っ (だっ, 分かっ) doubles the consonant of the next one: "datta", "wakatta".
        var doubling = false
        for (t in tokens) {
            val at = t.begin()
            val surface = t.surface()
            val len = surface.length
            if (at < 0 || at + len > text.length) continue
            if (surface.isBlank()) { first = true; doubling = false; continue }
            var roman = romaji(t)
            // Not Japanese (Latin words, digits, punctuation) stays as written.
            if (roman == null) { first = surface.last().isWhitespace(); doubling = false; continue }
            if (doubling && roman.isNotEmpty()) roman = when {
                roman.startsWith("ch") -> "t$roman"
                roman.first() in "aeiou" -> roman
                else -> roman.first() + roman
            }
            doubling = reading(t).endsWith('ッ')
            val joins = !first && attaches(t)
            out[at] = (if (!first && !joins && !text[at - 1].isWhitespace()) " " else "") + roman
            for (k in 1 until len) out[at + k] = ""
            first = false
        }
        JapaneseCounters.apply(out, text, runs, splitCounters)
        return out
    }

    override fun romanize(text: String): String = pieces(text).joinToString("")

    /**
     * Each run of kanji in [text] with the hiragana it's read as: 君の名は → きみ above 君, な above 名. Okurigana and
     * other kana shared by the word and its reading are left out of the reading, so only the kanji is annotated.
     */
    override fun furigana(text: String, splitCounters: Boolean): List<RubySpan> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<RubySpan>()
        // As [pieces]: read the digits as Japanese numerals, so a counter keeps its reading (2人 is ふたり).
        val tokens = synchronized(lock) {
            runCatching { tokenizer.tokenize(Tokenizer.SplitMode.C, numbersAsKanji(text)) }.getOrElse {
                android.util.Log.w("PrismRoman", "Sudachi couldn't read a line for furigana: $it")
                return emptyList()
            }
        }
        val runs = JapaneseCounters.runs(text, tokens.map { JapaneseCounters.Token(it.begin(), it.surface()) })
        val runByStart = runs.associateBy { it.start }
        var coveredUntil = -1
        for (t in tokens) {
            val at = t.begin()
            if (at < coveredUntil) continue
            val run = runByStart[at]
            if (run != null) {
                if (splitCounters && run.reading.number.isNotEmpty() && run.counterStart in run.start until run.end) {
                    if (run.reading.number.isNotEmpty()) out += RubySpan(run.start, run.counterStart, toHiragana(run.reading.number))
                    if (run.reading.counter.isNotEmpty()) out += RubySpan(run.counterStart, run.end, toHiragana(run.reading.counter))
                } else {
                    val reading = toHiragana(run.reading.all)
                    if (reading.isNotEmpty()) out += RubySpan(run.start, run.end, reading)
                }
                coveredUntil = run.end
                continue
            }
            val surface = t.surface()
            if (at < 0 || at + surface.length > text.length) continue
            if (surface.none(::isKanji)) continue
            val reading = t.readingForm()
            if (reading.isBlank() || reading == "*") continue
            val surfaceKana = Kana.toKatakana(surface)
            var readingKana = Kana.toKatakana(reading)
            var start = 0
            var end = surface.length
            // Kana the word shares with its reading at either end (お茶 → オチャ, 食べる → タベル) belongs to those
            // characters, not to the kanji between them.
            while (start < end && surfaceKana[start].let(Kana::isKana) && readingKana.firstOrNull() == surfaceKana[start]) {
                start++
                readingKana = readingKana.drop(1)
            }
            while (end > start && surfaceKana[end - 1].let(Kana::isKana) && readingKana.lastOrNull() == surfaceKana[end - 1]) {
                end--
                readingKana = readingKana.dropLast(1)
            }
            if (readingKana.isBlank()) continue
            // A number outside a counter run stays a number: its reading goes above the kanji the line actually has,
            // not above the digit.
            var firstKanji = -1
            var lastKanji = -1
            for (i in at + start until at + end) {
                if (isKanji(text[i])) {
                    if (firstKanji < 0) firstKanji = i
                    lastKanji = i
                }
            }
            if (firstKanji < 0) continue
            out += RubySpan(firstKanji, lastKanji + 1, toHiragana(readingKana))
        }
        return out
    }

    /** The word's reading spelled out, or null when it isn't Japanese. */
    private fun romaji(t: Morpheme): String? {
        val surface = t.surface()
        if (surface.none { Kana.isKana(it) || Character.isIdeographic(it.code) || it == 'ー' || it == '々' }) return null
        // Particles are written as they're said.
        if (t.partOfSpeech().getOrNull(0) == "助詞") when (surface) {
            "は" -> return "wa"
            "へ" -> return "e"
            "を" -> return "o"
        }
        return Kana.romanize(reading(t)).lowercase()
    }

    private fun reading(t: Morpheme): String {
        val reading = t.readingForm()
        return Kana.toKatakana(if (reading.isNotBlank() && reading != "*") reading else t.surface())
    }

    private fun isNumber(c: Char) = c in '0'..'9' || c in '０'..'９'

    /** [text] with each Arabic or full-width digit as a kanji numeral: the same length, one character for one. */
    private fun numbersAsKanji(text: String): String {
        if (text.none(::isNumber)) return text
        return buildString(text.length) {
            for (c in text) append(
                when (c) {
                    '0', '０' -> '〇'
                    '1', '１' -> '一'
                    '2', '２' -> '二'
                    '3', '３' -> '三'
                    '4', '４' -> '四'
                    '5', '５' -> '五'
                    '6', '６' -> '六'
                    '7', '７' -> '七'
                    '8', '８' -> '八'
                    '9', '９' -> '九'
                    else -> c
                },
            )
        }
    }

    /** [s] in hiragana (the dictionary's readings are katakana). */
    private fun toHiragana(s: String): String = buildString {
        for (c in s) append(if (c.code in 0x30A1..0x30F6) (c.code - 0x60).toChar() else c)
    }

    /** Words that are said joined to the one before (verb endings, auxiliaries, suffixes): "mite", "aishiteru". */
    private fun attaches(t: Morpheme): Boolean {
        val pos = t.partOfSpeech()
        val level1 = pos.getOrNull(0)
        val level2 = pos.getOrNull(1)
        return level1 == "助動詞" || (level1 == "助詞" && level2 == "接続助詞") || level2 == "接尾"
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { dictionary.close() }
    }

    companion object {
        /** The dictionary's file name in the folder [com.prism.music.data.ModelStore] unpacks. */
        const val DICT_FILE = "system_full.dic"

        /**
         * A reader over the full SudachiDict in [dir] (unpacked by [com.prism.music.data.ModelStore]). Sudachi maps the
         * 330 MB dictionary from disk rather than reading it into the heap, and takes well under a second.
         */
        fun load(dir: File): SudachiJapanese {
            val dictionaryFile = File(dir, DICT_FILE).takeIf { it.isFile }
                ?: dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".dic") }
                ?: error("No Sudachi dictionary in $dir")
            val dictionary = Dictionary.load(Config.defaultConfig().systemDictionary(dictionaryFile.toPath()))
            return SudachiJapanese(dictionary, dictionary.tokenizer())
        }
    }
}
