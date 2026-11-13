package com.prism.music.data.lyrics.roman

import com.prism.music.data.lyrics.RubySpan

/**
 * A reader that gives the hiragana above a line's kanji. [Japanese] and [SudachiJapanese] both implement it; the
 * romanizer asks for this so furigana and the romanization use the same words and readings.
 */
internal fun interface RubyReader {
    fun furigana(text: String, splitCounters: Boolean): List<RubySpan>
}
