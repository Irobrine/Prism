package com.prism.music

import com.prism.music.data.lyrics.RubySpan
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
        // Furigana: hiragana above the kanji, with the okurigana and kana beside it left out.
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(2, 3, "な")), ja.furigana("君の名は"))
        assertEquals(listOf(RubySpan(0, 1, "た")), ja.furigana("食べる"))
        assertEquals(listOf(RubySpan(0, 2, "きょう")), ja.furigana("今日"))
        assertEquals("きみ", Kana.toHiragana("キミ"))
        assertEquals(emptyList<RubySpan>(), ja.furigana("ひらがなだけ"))
    }

    @Test
    fun furiganaUnderstandsCountersNextToDigits() {
        val ja = com.prism.music.data.lyrics.roman.Japanese.bundled()
        // The number and its counter are read as one phrase, so the reading goes above both.
        assertEquals(listOf(RubySpan(0, 2, "ふたり")), ja.furigana("2人"))
        assertEquals(listOf(RubySpan(0, 2, "ひとり")), ja.furigana("１人"))
        assertEquals(listOf(RubySpan(0, 2, "ふたり")), ja.furigana("2人で"))
        assertEquals(listOf(RubySpan(0, 2, "ふたり"), RubySpan(2, 3, "ぐみ")), ja.furigana("2人組"))
        assertEquals(listOf(RubySpan(0, 2, "さんにん")), ja.furigana("3人"))
        assertEquals(listOf(RubySpan(0, 2, "よにん")), ja.furigana("4人"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうにん")), ja.furigana("10人"))
        assertEquals(listOf(RubySpan(0, 3, "じゅういちにん")), ja.furigana("11人"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうににん")), ja.furigana("12人"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうよにん")), ja.furigana("14人"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうしちにん")), ja.furigana("17人"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうくにん")), ja.furigana("19人"))
        assertEquals(listOf(RubySpan(0, 3, "にじゅうにん")), ja.furigana("20人"))
        assertEquals(listOf(RubySpan(0, 3, "にじゅういちにん")), ja.furigana("21人"))
        assertEquals(listOf(RubySpan(0, 3, "よんじゅうにん")), ja.furigana("40人"))
        assertEquals(listOf(RubySpan(0, 2, "ふたり")), ja.furigana("二人"))
        // 人 is にん in 一人称 and 一人前, not ひとり.
        assertEquals(listOf(RubySpan(0, 2, "いちにん"), RubySpan(2, 3, "しょう")), ja.furigana("一人称"))
        assertEquals(listOf(RubySpan(0, 2, "ににん"), RubySpan(2, 3, "まえ")), ja.furigana("二人前"))
        assertEquals(listOf(RubySpan(0, 2, "ひとり"), RubySpan(2, 3, "ぶん")), ja.furigana("一人分"))
        // Time: the counter changes with the number before it (1分 いっぷん, 3分 さんぷん, 20分 にじゅっぷん).
        assertEquals(listOf(RubySpan(0, 3, "にじゅっぷん")), ja.furigana("20分"))
        assertEquals(listOf(RubySpan(0, 2, "いっぷん")), ja.furigana("1分"))
        assertEquals(listOf(RubySpan(0, 2, "にふん")), ja.furigana("2分"))
        assertEquals(listOf(RubySpan(0, 2, "さんぷん")), ja.furigana("3分"))
        assertEquals(listOf(RubySpan(0, 2, "よんぷん")), ja.furigana("4分"))
        assertEquals(listOf(RubySpan(0, 2, "ろっぷん")), ja.furigana("6分"))
        assertEquals(listOf(RubySpan(0, 2, "はっぷん")), ja.furigana("8分"))
        assertEquals(listOf(RubySpan(0, 2, "きゅうふん")), ja.furigana("9分"))
        assertEquals(listOf(RubySpan(0, 3, "じゅっぷん")), ja.furigana("10分"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうごふん")), ja.furigana("15分"))
        assertEquals(listOf(RubySpan(0, 3, "さんじゅっぷん")), ja.furigana("30分"))
        assertEquals(listOf(RubySpan(0, 4, "ひゃっぷん")), ja.furigana("100分"))
        assertEquals(listOf(RubySpan(0, 4, "にじゅっぷんかん")), ja.furigana("20分間"))
        // Other counters with the same sound changes (本 ほん/ぼん/ぽん, 階 かい/がい, 歳 さい, ヶ月 かげつ…).
        assertEquals(listOf(RubySpan(0, 2, "いっぽん")), ja.furigana("1本"))
        assertEquals(listOf(RubySpan(0, 2, "さんぼん")), ja.furigana("3本"))
        assertEquals(listOf(RubySpan(0, 2, "ろっぽん")), ja.furigana("6本"))
        assertEquals(listOf(RubySpan(0, 2, "はっぽん")), ja.furigana("8本"))
        assertEquals(listOf(RubySpan(0, 3, "じゅっぽん")), ja.furigana("10本"))
        assertEquals(listOf(RubySpan(0, 4, "ひゃっぽん")), ja.furigana("100本"))
        assertEquals(listOf(RubySpan(0, 2, "いっぱい")), ja.furigana("1杯"))
        assertEquals(listOf(RubySpan(0, 2, "さんばい")), ja.furigana("3杯"))
        assertEquals(listOf(RubySpan(0, 2, "いっぴき")), ja.furigana("1匹"))
        assertEquals(listOf(RubySpan(0, 2, "さんびき")), ja.furigana("3匹"))
        assertEquals(listOf(RubySpan(0, 2, "いっかい")), ja.furigana("1回"))
        assertEquals(listOf(RubySpan(0, 2, "さんがい")), ja.furigana("3階"))
        assertEquals(listOf(RubySpan(0, 2, "いっこ")), ja.furigana("1個"))
        assertEquals(listOf(RubySpan(0, 2, "いっさい")), ja.furigana("1歳"))
        assertEquals(listOf(RubySpan(0, 2, "ろくさい")), ja.furigana("6歳"))
        assertEquals(listOf(RubySpan(0, 2, "はっさい")), ja.furigana("8歳"))
        assertEquals(listOf(RubySpan(0, 3, "はたち")), ja.furigana("20歳"))
        assertEquals(listOf(RubySpan(0, 3, "いっかげつ")), ja.furigana("1ヶ月"))
        assertEquals(listOf(RubySpan(0, 3, "ろっかげつ")), ja.furigana("6ヶ月"))
        assertEquals(listOf(RubySpan(0, 3, "いっしゅうかん")), ja.furigana("1週間"))
        // Dates, months and hours have readings of their own.
        assertEquals(listOf(RubySpan(0, 2, "いちにち")), ja.furigana("1日"))
        assertEquals(listOf(RubySpan(0, 2, "ふつか")), ja.furigana("2日"))
        assertEquals(listOf(RubySpan(0, 3, "とおか")), ja.furigana("10日"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうににち")), ja.furigana("12日"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうよっか")), ja.furigana("14日"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうしちにち")), ja.furigana("17日"))
        assertEquals(listOf(RubySpan(0, 3, "じゅうくにち")), ja.furigana("19日"))
        assertEquals(listOf(RubySpan(0, 3, "はつか")), ja.furigana("20日"))
        assertEquals(listOf(RubySpan(0, 3, "にじゅうよっか")), ja.furigana("24日"))
        assertEquals(listOf(RubySpan(0, 2, "しがつ")), ja.furigana("4月"))
        assertEquals(listOf(RubySpan(0, 2, "しちがつ")), ja.furigana("7月"))
        assertEquals(listOf(RubySpan(0, 2, "くがつ")), ja.furigana("9月"))
        assertEquals(listOf(RubySpan(0, 2, "よじ")), ja.furigana("4時"))
        assertEquals(listOf(RubySpan(0, 2, "しちじ")), ja.furigana("7時"))
        assertEquals(listOf(RubySpan(0, 2, "くじ")), ja.furigana("9時"))
        assertEquals(listOf(RubySpan(0, 2, "よねん")), ja.furigana("4年"))
        assertEquals(listOf(RubySpan(0, 5, "にせんにじゅうよねん")), ja.furigana("2024年"))
        assertEquals(listOf(RubySpan(0, 5, "にせんじゅうしちねん")), ja.furigana("2017年"))
        assertEquals(listOf(RubySpan(0, 5, "にせんじゅうくねん")), ja.furigana("2019年"))
        assertEquals(listOf(RubySpan(0, 2, "よえん")), ja.furigana("4円"))
        // The kanji numerals a source writes are read the same way.
        assertEquals(listOf(RubySpan(0, 3, "にじゅっぷん")), ja.furigana("二十分"))
        assertEquals(listOf(RubySpan(0, 3, "にじゅうにん")), ja.furigana("二十人"))
        assertEquals(listOf(RubySpan(0, 3, "はたち")), ja.furigana("二十歳"))
        assertEquals(listOf(RubySpan(0, 3, "はつか")), ja.furigana("二十日"))
        assertEquals(listOf(RubySpan(0, 4, "にじゅうよっか")), ja.furigana("二十四日"))
        // The romaji reading follows the furigana: the whole number and counter, with no digit left in front.
        assertEquals("nijuppun", ja.romanize("20分"))
        assertEquals("ippun", ja.romanize("1分"))
        assertEquals("sanpun", ja.romanize("3分"))
        assertEquals("juppun", ja.romanize("10分"))
        assertEquals("ippon", ja.romanize("1本"))
        assertEquals("sanbon", ja.romanize("3本"))
        assertEquals("roppon", ja.romanize("6本"))
        assertEquals("yonin", ja.romanize("4人"))
        assertEquals("juushichinin", ja.romanize("17人"))
        assertEquals("juukunin", ja.romanize("19人"))
        assertEquals("nijuunin", ja.romanize("20人"))
        assertEquals("futari", ja.romanize("2人"))
        assertEquals("shigatsu", ja.romanize("4月"))
        assertEquals("juuninin", ja.romanize("12人"))
        assertEquals("juuichinin", ja.romanize("11人"))
        assertEquals("ichinichi", ja.romanize("1日"))
        assertEquals("futsuka", ja.romanize("2日"))
        assertEquals("juuyokka", ja.romanize("14日"))
        assertEquals("juushichinichi", ja.romanize("17日"))
        assertEquals("juukunichi", ja.romanize("19日"))
        assertEquals("hatsuka", ja.romanize("20日"))
        assertEquals("hatachi", ja.romanize("20歳"))
        assertEquals("ikkagetsu", ja.romanize("1ヶ月"))
        assertEquals("isshuukan", ja.romanize("1週間"))
        assertEquals("nisennijuuyonen", ja.romanize("2024年"))
        assertEquals("nisenjuushichinen", ja.romanize("2017年"))
        assertEquals("nisenjuukunen", ja.romanize("2019年"))
        // A kanji numeral keeps its reading, with the small っ joining it to the counter.
        assertEquals("nijuppun", ja.romanize("二十分"))
        assertEquals("nijuunin", ja.romanize("二十人"))
        assertEquals("juuninin", ja.romanize("十二人"))
        assertEquals("hatachi", ja.romanize("二十歳"))
        assertEquals("hatsuka", ja.romanize("二十日"))
        assertEquals("nijuuyokka", ja.romanize("二十四日"))
        // Counters that read the number into the word itself.
        assertEquals("hitori", ja.romanize("一人"))
        assertEquals("futari", ja.romanize("二人"))
        assertEquals("futsuka", ja.romanize("二日"))
        assertEquals("yokka", ja.romanize("四日"))
        assertEquals("hitotsu", ja.romanize("一つ"))
        // A number in the middle of another one is left alone.
        assertEquals("1,000nin", ja.romanize("1,000人"))
        assertEquals("3.5fun", ja.romanize("3.5分"))
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
