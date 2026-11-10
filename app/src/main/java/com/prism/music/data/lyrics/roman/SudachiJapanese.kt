package com.prism.music.data.lyrics.roman

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
) : JapaneseReader {
    private val lock = Any()
    private var closed = false

    override fun pieces(text: String): List<String> {
        // Sudachi's tokenizer keeps one lattice while it works: one line at a time, so two songs never share it.
        val tokens = synchronized(lock) {
            runCatching { tokenizer.tokenize(Tokenizer.SplitMode.C, text) }.getOrElse {
                // A line it can't parse stays as it is; the caller then asks Google for it, as with an unknown kanji.
                android.util.Log.w("PrismRoman", "Sudachi couldn't read a line: $it")
                return text.map { c -> c.toString() }
            }
        }
        val out = MutableList(text.length) { text[it].toString() }
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
        return out
    }

    override fun romanize(text: String): String = pieces(text).joinToString("")

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
