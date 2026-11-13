package com.prism.music.data.lyrics.roman

import com.prism.music.data.lyrics.RubySpan

/**
 * A reader for Japanese with kanji: each character of a line spelled out in Latin letters, word by word, and the
 * hiragana above its kanji. [Japanese] reads it with Kuromoji's IPADIC, [SudachiJapanese] with Sudachi and the full
 * SudachiDict; the [com.prism.music.data.lyrics.Romanizer] uses Sudachi whenever its dictionary is on the phone,
 * otherwise IPADIC.
 */
interface JapaneseReader : AutoCloseable {
    /**
     * Each character of [text] spelled out: a word's whole reading on its first character (with a space before it when
     * it starts a new word), nothing on the rest. [splitCounters] puts a number and its counter on their own halves,
     * the same way [furigana] does.
     */
    fun pieces(text: String, splitCounters: Boolean = false): List<String>

    fun romanize(text: String): String

    /** Each run of kanji with the hiragana above it; [splitCounters] reads 20分 as にじゅっ + ぷん. */
    fun furigana(text: String, splitCounters: Boolean = false): List<RubySpan>

    /** Lets go of what it holds open (Sudachi's dictionary is memory-mapped); Kuromoji's stays in memory. */
    override fun close() {}
}
