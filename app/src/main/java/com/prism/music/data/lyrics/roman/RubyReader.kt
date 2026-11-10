package com.prism.music.data.lyrics.roman

import com.prism.music.data.lyrics.RubySpan

/**
 * A Japanese reader that gives the hiragana above a line's kanji, as well as spelling the line out in Latin letters.
 * Sudachi's reader implements this directly; [com.prism.music.data.lyrics.Romanizer] wraps Kuromoji's [Japanese], which
 * has the same method, so furigana asks for this rather than one particular reader.
 */
internal fun interface RubyReader {
    fun furigana(text: String): List<RubySpan>
}
