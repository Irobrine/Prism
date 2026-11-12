package com.prism.music.data.lyrics.roman

import com.prism.music.data.ModelStore

/**
 * Which on-phone Japanese dictionary reads lyrics' kanji (Settings → Lyrics). Both read a song the same way, through
 * [JapaneseReader]; the full SudachiDict knows more words (names, lyrics' own vocabulary), at a much bigger download.
 */
enum class JapaneseEngine(val model: ModelStore.Model) {
    /** Kuromoji's IPADIC: a 13 MB download. */
    IPADIC(ModelStore.Model.JAPANESE),

    /** Sudachi with the full SudachiDict: more words, a 137 MB download and about 330 MB on the phone. */
    SUDACHI(ModelStore.Model.SUDACHI),
}
