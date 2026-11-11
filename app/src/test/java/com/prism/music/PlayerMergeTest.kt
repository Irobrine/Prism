package com.prism.music

import com.prism.music.data.prefs.LyricsAlign
import com.prism.music.ui.screens.settings.AnimatedCovers
import com.prism.music.ui.screens.settings.CoverAnimation
import com.prism.music.ui.screens.settings.EdgeToEdge
import com.prism.music.ui.screens.settings.LineOnPlayer
import com.prism.music.ui.screens.settings.PanelChip
import com.prism.music.ui.screens.settings.QueueChip
import com.prism.music.ui.screens.settings.VideoControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.8.4, Settings → Player's merged controls (plan §2.5): every combination of the stored values that was possible
 * before maps to the one control and back unchanged, and every value of the control can be picked from any of them.
 */
class PlayerMergeTest {
    private val bools = listOf(false, true)
    private val pairs = bools.flatMap { a -> bools.map { b -> a to b } }

    private fun flags(n: Int): List<List<Boolean>> = (0 until (1 shl n)).map { bits -> List(n) { i -> bits shr i and 1 == 1 } }

    @Test fun coverAnimationKeepsAllFourStoredCombinations() {
        assertEquals(4, pairs.size)
        for (stored in pairs) assertEquals("$stored", stored, CoverAnimation.toMerged(stored).toStored(stored))
        assertEquals(CoverAnimation.OFF, CoverAnimation.toMerged(false to false))
        assertEquals(CoverAnimation.OPENING, CoverAnimation.toMerged(true to false))
        assertEquals(CoverAnimation.OPENING_AND_CLOSING, CoverAnimation.toMerged(true to true))
        // "Back" stored without "flight" did nothing before, and shows as Off.
        assertEquals(CoverAnimation.OFF, CoverAnimation.toMerged(false to true))
        // Any value can be picked from any stored combination, and is then what the control shows.
        for (was in pairs) for (pick in CoverAnimation.entries) assertEquals("$was → $pick", pick, CoverAnimation.toMerged(pick.toStored(was)))
        assertEquals(true to false, CoverAnimation.OPENING.toStored(false to true))
    }

    @Test fun edgeToEdgeIsTheFourCombinations() {
        for (stored in pairs) assertEquals("$stored", stored, EdgeToEdge.toMerged(stored).toStored())
        assertEquals(EdgeToEdge.entries.toSet(), pairs.map { EdgeToEdge.toMerged(it) }.toSet())
        assertEquals(EdgeToEdge.OFF, EdgeToEdge.toMerged(false to false))
        assertEquals(EdgeToEdge.COVER, EdgeToEdge.toMerged(true to false))
        assertEquals(EdgeToEdge.VIDEO, EdgeToEdge.toMerged(false to true))
        assertEquals(EdgeToEdge.BOTH, EdgeToEdge.toMerged(true to true))
    }

    @Test fun animatedCoversKeepsAllFourStoredCombinations() {
        for (stored in pairs) assertEquals("$stored", stored, AnimatedCovers.toMerged(stored).toStored(stored))
        assertEquals(AnimatedCovers.OFF, AnimatedCovers.toMerged(false to false))
        assertEquals(AnimatedCovers.OFF, AnimatedCovers.toMerged(false to true))
        assertEquals(AnimatedCovers.ON_THE_COVER, AnimatedCovers.toMerged(true to false))
        assertEquals(AnimatedCovers.FILL_THE_PLAYER, AnimatedCovers.toMerged(true to true))
        for (was in pairs) for (pick in AnimatedCovers.entries) assertEquals("$was → $pick", pick, AnimatedCovers.toMerged(pick.toStored(was)))
    }

    @Test fun controlsOverVideosKeepsTheFourthCombination() {
        for (stored in pairs) assertEquals("$stored", stored, VideoControls.toMerged(stored).toStored())
        assertEquals(VideoControls.ALWAYS, VideoControls.toMerged(false to false))
        assertEquals(VideoControls.HIDE_CONTROLS, VideoControls.toMerged(true to false))
        assertEquals(VideoControls.HIDE_CONTROLS_AND_BUTTONS, VideoControls.toMerged(true to true))
        // The buttons wait, the controls stay: neither of the plan's three, kept as it is and said in its own words.
        val fourth = VideoControls.toMerged(false to true)
        assertEquals(VideoControls.BUTTONS_WAIT, fourth)
        assertEquals(false to true, fourth.toStored())
        // It is only offered while it is the one stored; the plan's three always are.
        val three = listOf(VideoControls.ALWAYS, VideoControls.HIDE_CONTROLS, VideoControls.HIDE_CONTROLS_AND_BUTTONS)
        for (now in three) assertEquals(three, VideoControls.choices(now))
        assertEquals(three + fourth, VideoControls.choices(fourth))
    }

    @Test fun theQueueChipsAreTheFiveSwitches() {
        val all = flags(5)
        assertEquals(32, all.size)
        for (stored in all) assertEquals("$stored", stored, QueueChip.toStored(QueueChip.toMerged(stored)))
        assertEquals(32, all.map { QueueChip.toMerged(it) }.toSet().size)
        assertEquals(setOf(QueueChip.HEADINGS, QueueChip.DJ), QueueChip.toMerged(listOf(true, true, false, false, false)))
    }

    @Test fun theLyricsAndQueueChipsAreTheThreeSwitches() {
        val all = flags(3)
        assertEquals(8, all.size)
        for (stored in all) assertEquals("$stored", stored, PanelChip.toStored(PanelChip.toMerged(stored)))
        assertEquals(8, all.map { PanelChip.toMerged(it) }.toSet().size)
    }

    @Test fun theLineOnThePlayerIsOnePickOfAlignmentAndLines() {
        val stored = LyricsAlign.entries.flatMap { a -> bools.map { a to it } }
        assertEquals(4, stored.size)
        for (s in stored) assertEquals("$s", s, LineOnPlayer.toMerged(s).toStored())
        assertEquals(LineOnPlayer.all.toSet(), stored.map { LineOnPlayer.toMerged(it) }.toSet())
        assertTrue(LineOnPlayer.all.all { it.lines in 1..2 })
    }
}
