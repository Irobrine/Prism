package com.prism.music.data.lyrics

import com.prism.music.data.ModelStore
import com.prism.music.data.db.RomanizedDao
import com.prism.music.data.db.RomanizedEntity
import com.prism.music.data.lyrics.roman.Japanese
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A source's hiragana in parentheses (KuGou's 君(きみ)) gives the readings where it has them, the dictionary where not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RubySourceTest {
    @Test
    fun findsSourceReadingsAndCleansThemOut() {
        val s = SourceFurigana.of("君(きみ)の名前（なまえ）")!!
        assertEquals("君の名前", s.clean)
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(6, 8, "なまえ")), s.spans)
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(2, 4, "なまえ")), s.cleanSpans)
        val merged = s.merge(listOf(RubySpan(0, 1, "きみ"), RubySpan(2, 4, "なまえ"), RubySpan(2, 3, "な")))
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(6, 8, "なまえ")), merged)
    }

    @Test
    fun shorteningTheTextKeepsWordsAndTimings() {
        val line = LyricLine(
            100, "君(きみ)の名前(なまえ)", 1_100,
            words = listOf(LyricWord(100, 500, "君(きみ)"), LyricWord(500, 1_100, "の名前(なまえ)")),
        )
        val clean = SourceFurigana.of(line.text)!!.cleanLine(line)
        assertEquals("君の名前", clean.text)
        assertEquals(listOf("君", "の名前"), clean.words.map { it.text })
        // A word keeps its own start and end: the reading takes no sung time, so the word is sung over its whole span
        // and the line still ends on time.
        assertEquals(listOf(100L to 500L, 500L to 1_100L), clean.words.map { it.startMs to it.endMs })
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(2, 4, "なまえ")), clean.sourceRuby)
    }

    @Test
    fun aReadingInsideAWordLeavesOneWordOverItsWholeTime() {
        val line = LyricLine(100, "君(きみ)の", 700, words = listOf(LyricWord(100, 700, "君(きみ)の")))
        val clean = SourceFurigana.of(line.text)!!.cleanLine(line)
        assertEquals("君の", clean.text)
        assertEquals(listOf("君の"), clean.words.map { it.text })
        assertEquals(listOf(100L to 700L), clean.words.map { it.startMs to it.endMs })
    }

    @Test
    fun aWordThatWasOnlyAReadingKeepsTheWordsAfterItOnTime() {
        val line = LyricLine(
            100, "君(きみ)の", 700,
            words = listOf(LyricWord(100, 300, "君"), LyricWord(300, 500, "(きみ)"), LyricWord(500, 700, "の")),
        )
        val clean = SourceFurigana.of(line.text)!!.cleanLine(line)
        assertEquals(listOf("君", "", "の"), clean.words.map { it.text })
        assertEquals(listOf(100L to 300L, 300L to 500L, 500L to 700L), clean.words.map { it.startMs to it.endMs })
    }

    @Test
    fun aReadingKeepsItsTimeOnTheKanjiAndTheRestOnItsOwn() {
        val line = WordFormat.parse(
            WordFormat.Kind.KRC,
            "[10000,3000]<0,500,0>向<500,200,0>（<700,100,0>む<800,100,0>）<900,200,0>か<1100,300,0>え",
        ).last()
        assertEquals("向（む）かえ", line.text)
        assertEquals(1, line.words.size)
        val clean = SourceFurigana.of(line.text)!!.cleanLine(line)
        assertEquals("向かえ", clean.text)
        assertEquals(listOf(10_000L to 11_400L), clean.words.map { it.startMs to it.endMs })
        val word = clean.words.single()
        assertEquals(listOf(1, 1, 1), word.timedParts.map { it.length })
        assertEquals(
            listOf(10_000L to 10_900L, 10_900L to 11_100L, 11_100L to 11_400L),
            word.timedParts.map { it.startMs to it.endMs },
        )
        assertEquals(0.5f, word.sungLength(10_450), 0.001f)
        assertEquals(1f, word.sungLength(10_900), 0.001f)
        assertEquals(1.5f, word.sungLength(11_000), 0.001f)
        assertEquals(2f, word.sungLength(11_100), 0.001f)
        assertEquals(3f, word.sungLength(11_400), 0.001f)
        assertEquals(10_900L, word.startAt(1))
    }

    @Test
    fun aReadingIsSharedByTheWholeKanjiRunItReads() {
        val line = WordFormat.parse(
            WordFormat.Kind.KRC,
            "[0,2000]<0,100,0>本<100,100,0>当<200,100,0>（<300,100,0>ほ<400,100,0>ん<500,100,0>と<600,100,0>う<700,100,0>）<800,100,0>の",
        ).last()
        val clean = SourceFurigana.of(line.text)!!.cleanLine(line)
        assertEquals("本当の", clean.text)
        val word = clean.words.single()
        assertEquals(listOf(1, 1, 1), word.timedParts.map { it.length })
        assertEquals(
            listOf(0L to 400L, 400L to 800L, 800L to 900L),
            word.timedParts.map { it.startMs to it.endMs },
        )
    }

    @Test
    fun leavesWordTimingsAloneWhenTheWordsDontCoverTheLine() {
        val line = LyricLine(100, "君(きみ)の", 700, words = listOf(LyricWord(100, 500, "君(きみ)")))
        assertEquals(line, SourceFurigana.of(line.text)!!.cleanLine(line))
    }

    @Test
    fun findsReadingsPrintedWithFullWidthParenthesesAndASpace() {
        val s = SourceFurigana.of("君 （きみ）の名前（なまえ）")!!
        assertEquals("君の名前", s.clean)
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(7, 9, "なまえ")), s.spans)
    }

    /** Only hiragana after kanji: after kana, or holding anything else, isn't a reading. */
    @Test
    fun doesNotTakeOtherParenthesesForReadings() {
        assertNull(SourceFurigana.of("食べる(たべる)"))
        assertNull(SourceFurigana.of("君  (笑)"))
        assertNull(SourceFurigana.of("君(笑)"))
        assertNull(SourceFurigana.of("君(ららららららら)"))
    }

    @Test
    fun theReaderUsesSourceReadingsAndFillsTheRest() = runBlocking {
        val dao = object : RomanizedDao {
            override suspend fun get(key: String): RomanizedEntity? = null
            override suspend fun put(e: RomanizedEntity) {}
        }
        val romanizer = Romanizer(
            OkHttpClient(), dao, ModelStore(RuntimeEnvironment.getApplication(), OkHttpClient()),
            CoroutineScope(Dispatchers.Default), japaneseForTests = Japanese.bundled(),
        )
        val source = romanizer.furigana(listOf(LyricLine(0, "君(きみ)の名前")))!!
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(6, 8, "なまえ")), source.single())
        val cleanedLine = SourceFurigana.of("君(きみ)の名前")!!.cleanLine(LyricLine(0, "君(きみ)の名前"))
        val cleaned = romanizer.furigana(listOf(cleanedLine))!!
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(2, 4, "なまえ")), cleaned.single())
        // 名 isn't annotated (its reading is in parentheses after the kana は): the dictionary gives it.
        val mixed = romanizer.furigana(listOf(LyricLine(0, "君(きみ)の名は(な)")))!!
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(6, 7, "な")), mixed.single())
    }
}
