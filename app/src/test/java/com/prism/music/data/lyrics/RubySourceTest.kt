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
        // A reading's share of the word's time goes with it; the next word keeps its own start, so the line still ends on time.
        assertEquals(listOf(100L to 180L, 500L to 725L), clean.words.map { it.startMs to it.endMs })
        assertEquals(listOf(RubySpan(0, 1, "きみ"), RubySpan(2, 4, "なまえ")), clean.sourceRuby)
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
