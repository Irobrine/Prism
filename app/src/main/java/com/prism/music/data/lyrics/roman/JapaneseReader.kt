package com.prism.music.data.lyrics.roman

/**
 * A reader for Japanese with kanji: each character of a line spelled out in Latin letters, word by word.
 * [Japanese] reads it with Kuromoji's IPADIC, [SudachiJapanese] with Sudachi and the full SudachiDict; which one the
 * listener uses is their choice in Settings → Lyrics.
 */
interface JapaneseReader : AutoCloseable {
    /**
     * Each character of [text] spelled out: a word's whole reading on its first character (with a space before it when
     * it starts a new word), nothing on the rest.
     */
    fun pieces(text: String): List<String>

    fun romanize(text: String): String

    /** Lets go of what it holds open (Sudachi's dictionary is memory-mapped); Kuromoji's stays in memory. */
    override fun close() {}
}
