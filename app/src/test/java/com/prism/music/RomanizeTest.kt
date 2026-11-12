package com.prism.music

import com.prism.music.data.lyrics.roman.Cyrillic
import com.prism.music.data.lyrics.roman.Greek
import com.prism.music.data.lyrics.roman.Hangul
import com.prism.music.data.lyrics.roman.JapaneseCounters
import com.prism.music.data.lyrics.roman.Kana
import org.junit.Assert.assertEquals
import org.junit.Test

/** The on-device romanizers: Korean (Revised Romanization), kana (Hepburn), Cyrillic, Greek. */
class RomanizeTest {
    @Test
    fun korean() {
        assertEquals("saranghae", Hangul.romanize("사랑해"))
        assertEquals("saranghaeyo gachi gayo", Hangul.romanize("사랑해요 같이 가요"))
        assertEquals("hamnida", Hangul.romanize("합니다"))
        assertEquals("silla", Hangul.romanize("신라"))
        assertEquals("joko", Hangul.romanize("좋고"))
        assertEquals("joa", Hangul.romanize("좋아"))
        assertEquals("bom", Hangul.romanize("봄"))
        // One piece per syllable, so karaoke timings stay with their syllables.
        assertEquals(listOf("ga", "chi"), Hangul.pieces("같이"))
    }

    @Test
    fun kana() {
        assertEquals("kitte", Kana.romanize("きって"))
        assertEquals("matcha", Kana.romanize("まっちゃ"))
        assertEquals("kyou", Kana.romanize("きょう"))
        assertEquals("koohii", Kana.romanize("コーヒー"))
        assertEquals("shashin", Kana.romanize("しゃしん"))
        assertEquals("fan", Kana.romanize("ファン"))
    }

    @Test
    fun japaneseWithKanji() {
        val ja = com.prism.music.data.lyrics.roman.Japanese.bundled()
        assertEquals("kimi no na wa", ja.romanize("君の名は"))
        assertEquals("yume o mite ita", ja.romanize("夢を見ていた"))
        assertEquals("aishiteru", ja.romanize("愛してる").replace(" ", ""))
        // A small っ at a word's end doubles the next word's consonant; 二人 is "futari", as sung.
        assertEquals("sayonara dakedatta", ja.romanize("さよならだけだった"))
        assertEquals("sono hitokoto de subete ga wakatta", ja.romanize("その一言で全てが分かった"))
        assertEquals("futari dake no sora ga hirogaru yoru ni", ja.romanize("二人だけの空が広がる夜に"))
        assertEquals("kasanatte ita", ja.romanize("重なっていた"))
        // 々 counts as a kanji, so the line stays with the reader rather than going to Google.
        assertEquals(setOf(com.prism.music.data.lyrics.roman.Script.HAN, com.prism.music.data.lyrics.roman.Script.KANA),
            com.prism.music.data.lyrics.roman.Script.foreignIn("騒がしい日々に"))
        assertEquals("sawagashii hibi ni", ja.romanize("騒がしい日々に"))
        // Latin words in a Japanese line stay as written.
        assertEquals("I love you to iu", ja.romanize("I love youという").replace("toiu", "to iu"))
        // One piece per character: a word's reading on its first character.
        val p = ja.pieces("君の")
        assertEquals(listOf("kimi", " no"), p)
    }

    @Test
    fun countersFollowTheNumberBeforeThem() {
        val ja = com.prism.music.data.lyrics.roman.Japanese.bundled()
        assertEquals("nijuppun", ja.romanize("20分"))
        assertEquals("ippun", ja.romanize("1分"))
        assertEquals("sanbon", ja.romanize("3本"))
        assertEquals("futari", ja.romanize("2人"))
        assertEquals("hatachi", ja.romanize("20歳"))
        assertEquals("shigatsu", ja.romanize("4月"))
        assertEquals("futsuka", ja.romanize("2日"))
        assertEquals("nisennijuuyonen", ja.romanize("2024年"))
        assertEquals("nijuppun", ja.romanize("二十分"))
    }

    @Test
    fun counterRunsReadSplitAndFusedTokens() {
        fun read(text: String, vararg tokens: JapaneseCounters.Token): String {
            val runs = JapaneseCounters.runs(text, tokens.toList())
            val out = MutableList(text.length) { text[it].toString() }
            JapaneseCounters.apply(out, text, runs)
            return out.joinToString("")
        }
        // Sudachi may give a number and its counter as separate words, or as one word.
        assertEquals("nijuppun", read("20分", JapaneseCounters.Token(0, "20"), JapaneseCounters.Token(2, "分")))
        assertEquals("nijuppun", read("20分", JapaneseCounters.Token(0, "20分")))
        assertEquals("futari", read("2人", JapaneseCounters.Token(0, "2"), JapaneseCounters.Token(1, "人")))
        assertEquals("hatachi", read("二十歳", JapaneseCounters.Token(0, "二十歳")))
    }

    @Test
    fun japaneseFromDownloadedFiles() {
        // The phone reads the dictionary from the unpacked download, not the jar: same answers.
        val dir = kotlin.io.path.createTempDirectory("ipadic").toFile()
        for (name in listOf("characterDefinitions", "connectionCosts", "doubleArrayTrie", "tokenInfoDictionary", "tokenInfoFeaturesMap",
            "tokenInfoPartOfSpeechMap", "tokenInfoTargetMap", "unknownDictionary")) {
            val res = com.atilika.kuromoji.ipadic.Tokenizer::class.java.getResourceAsStream("$name.bin")!!
            java.io.File(dir, "$name.bin").outputStream().use { res.copyTo(it) }
        }
        val ja = com.prism.music.data.lyrics.roman.Japanese.load(dir)
        assertEquals("kimi no na wa", ja.romanize("君の名は"))
        dir.deleteRecursively()
    }

    @Test
    fun cyrillicAndGreek() {
        assertEquals("Ya tebya lyublyu", Cyrillic.romanize("Я тебя люблю"))
        assertEquals("S' agapo", Greek.romanize("Σ' αγαπώ"))
    }
}
