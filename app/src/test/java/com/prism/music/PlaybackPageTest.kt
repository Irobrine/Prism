package com.prism.music

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import com.prism.music.data.AutoplayLength
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.AutoplaySource
import com.prism.music.data.prefs.CrossfadeCurve
import com.prism.music.data.prefs.ExplicitContent
import com.prism.music.data.prefs.ShuffleButton
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.ui.screens.Crossfade
import com.prism.music.ui.screens.QueueEnd
import com.prism.music.ui.screens.SettingsIndex
import com.prism.music.ui.screens.VideoPolicy
import com.prism.music.ui.screens.settings.SettingsMap
import com.prism.music.ui.screens.settings.SettingsPageId
import com.prism.music.ui.screens.settings.SettingsTags
import com.prism.music.ui.screens.settings.SettingsTarget
import com.prism.music.ui.screens.settings.oldGroups
import com.prism.music.ui.screens.settings.value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 1.8.4, Settings → Playback rebuilt with the kit (plan §2.3, the ledger's Playback table): five groups of the five
 * rows within the budgets, a control for every stored setting the ledger gives the page (worked here, and the store
 * follows), the new Shuffle group, Reset this page, and the page drawn in light, dark, Performance mode and at large
 * text. The window is tall so that the page's lazy list holds every group at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h4000dp-xhdpi", application = android.app.Application::class)
class PlaybackPageTest : ListeningPageHarness(SettingsPageId.PLAYBACK) {
    private fun t(id: Int) = tr(id)

    private val queueEnds = t(R.string.when_the_queue_ends)
    private val noRepeats = t(R.string.no_repeats_title)
    private val longest = t(R.string.longest_song_for_autoplay)
    private val videos = t(R.string.music_videos_label)
    private val keepVideos = t(R.string.keep_playing_videos)
    private val explicit = t(R.string.explicit_songs)
    private val shuffleButton = t(R.string.playback_shuffle_button)
    private val keepShuffle = t(R.string.playback_keep_shuffle)
    private val crossfade = t(R.string.crossfade)
    private val length = t(R.string.length)
    private val beatMatch = t(R.string.match_tempo_and_beats_dj_style)
    private val keepAlbums = t(R.string.keep_an_album_s_songs_whole)
    private val curve = t(R.string.how_they_cross)
    private val speed = t(R.string.speed_when_prism_starts)
    private val pitch = t(R.string.pitch_when_prism_starts)
    private val sleepFade = t(R.string.sleep_timer_fades_out)
    private val pauseFade = t(R.string.fade_on_pause_and_play)
    private val stopOnSwipe = t(R.string.stop_when_swiped_away)
    private val pauseOnMute = t(R.string.pause_when_muted)
    private val faceDown = t(R.string.playback_face_down_pause)

    private fun groupTitles() = compose.onAllNodes(hasTestTag(SettingsTags.GROUP)).fetchSemanticsNodes()
        .map { g -> g.children.first().config[SemanticsProperties.Text].first().text }

    @Test fun fiveGroupsWithinTheBudgets() {
        show()
        assertBudgets("defaults")
        // The ledger's layout with a fresh install's settings.
        assertEquals(
            listOf(t(R.string.what_plays_next), t(R.string.shuffle), t(R.string.playback_song_to_song), t(R.string.playback_speed_and_pitch), t(R.string.stopping_and_pausing)),
            groupTitles(),
        )
        assertEquals(listOf(6, 2, 2, 2, 5), groupSizes())
        assertEquals("nothing differs from how Prism comes", 0, changedRows())
        // No group is named as the page, and none as one of its own rows (§2.7: "Crossfade" was both).
        assertTrue(groupTitles().none { it == SettingsPageId.PLAYBACK.title })
        for (g in groupTitles()) assertFalse("a group and a row are both \"$g\"", hasRow(g))
        // Four or more groups: the jump chips are there.
        assertEquals(1, compose.onAllNodes(hasTestTag(SettingsTags.JUMP_CHIPS)).fetchSemanticsNodes().size)
        // Each row is the type the ledger gives it.
        assertEquals(SettingsTags.ROW_PICK, typeOf(queueEnds))
        assertEquals(QueueEnd.MIXED.label, valueOf(queueEnds))
        assertEquals(SettingsTags.ROW_SWITCH, typeOf(noRepeats))
        assertEquals(SettingsTags.ROW_SLIDER, typeOf(longest))
        assertEquals(SettingsTags.ROW_CHOICE, typeOf(crossfade))
        assertEquals(SettingsTags.ROW_PICK, typeOf(curve))
        assertEquals(CrossfadeCurve.EQUAL_POWER.label, valueOf(curve))
        assertEquals(SettingsTags.ROW_SLIDER, typeOf(speed))
        assertEquals("1×", valueOf(speed))
        assertEquals(t(R.string.as_recorded), valueOf(pitch))
        assertEquals(t(R.string.off), valueOf(sleepFade))
    }

    @Test fun withEverythingOnItStillFits() {
        container.settings.setMix(true)
        container.settings.setMixAuto(false)
        until("crossfade on") { it.mix && !it.mixAuto }
        show()
        assertBudgets("crossfade on, fixed length")
        assertEquals(listOf(6, 2, 5, 2, 5), groupSizes())
        assertEquals("6 s", valueOf(length))
    }

    /** Principle 8: every stored setting the ledger gives Playback has its control here, and the control stores it. */
    @Test fun everyStoredSettingHasItsControl() {
        show()
        val d = AppSettings()

        // autoplay, autoplay_source: "When the queue ends".
        pick(queueEnds, QueueEnd.STOP.label)
        until("autoplay") { !it.autoplay }
        assertFalse("its length limit only shows with Autoplay on", hasRow(longest))
        pick(queueEnds, QueueEnd.YOURS.label)
        until("autoplay_source") { it.autoplay && it.autoplaySource == AutoplaySource.YOURS }
        assertEquals(QueueEnd.YOURS.label, valueOf(queueEnds))
        // autoplay_no_repeats
        tap(noRepeats)
        until("autoplay_no_repeats") { it.behavior.autoplayNoRepeats }
        // autoplay_max_minutes
        slide(longest, AutoplayLength.STEP_MINUTES * 2f)
        until("autoplay_max_minutes") { it.behavior.autoplayMaxMinutes == AutoplayLength.STEP_MINUTES * 2 }
        // radio_songs_only, songs_everywhere: "Music videos".
        choose(videos, VideoPolicy.PLAY.label)
        until("radio_songs_only") { !it.radioSongsOnly && !it.songsEverywhere }
        choose(videos, VideoPolicy.EVERYWHERE.label)
        until("songs_everywhere") { it.radioSongsOnly && it.songsEverywhere }
        // video_continues
        tap(keepVideos)
        until("video_continues") { it.videoContinues != d.videoContinues }
        // explicit_content
        choose(explicit, ExplicitContent.SKIP.label)
        until("explicit_content") { it.behavior.explicitContent == ExplicitContent.SKIP }

        // shuffle_button, keep_shuffle (new in 1.8.4).
        choose(shuffleButton, ShuffleButton.SHUFFLE_ONLY.label)
        until("shuffle_button") { it.behavior.shuffleButton == ShuffleButton.SHUFFLE_ONLY }
        tap(keepShuffle)
        until("keep_shuffle") { it.behavior.keepShuffle }

        // mix, mix_auto: "Crossfade"; its three rows only show with it on (the length with a fixed one).
        assertFalse(hasRow(length) || hasRow(beatMatch) || hasRow(keepAlbums))
        choose(crossfade, Crossfade.AUTO.label)
        until("mix") { it.mix && it.mixAuto }
        assertFalse(hasRow(length))
        choose(crossfade, Crossfade.FIXED.label)
        until("mix_auto") { it.mix && !it.mixAuto }
        // mix_seconds
        slide(length, 9f)
        until("mix_seconds") { it.mixSeconds == 9 }
        assertEquals("9 s", valueOf(length))
        // mix_beat_match, mix_keep_albums
        tap(beatMatch)
        until("mix_beat_match") { it.mixBeatMatch }
        tap(keepAlbums)
        until("mix_keep_albums") { it.mixKeepAlbums }
        // crossfade_curve: in its pick sheet, one card a curve.
        tap(curve)
        assertEquals(CrossfadeCurve.entries.size, compose.onAllNodes(hasTestTag(SettingsTags.SHEET_OPTION)).fetchSemanticsNodes().size)
        swipeSheetAway()
        assertEquals("a sheet swiped away changes nothing", d.sound.crossfadeCurve, stored.sound.crossfadeCurve)
        pick(curve, CrossfadeCurve.LINEAR.label)
        until("crossfade_curve") { it.sound.crossfadeCurve == CrossfadeCurve.LINEAR }
        assertEquals(CrossfadeCurve.LINEAR.label, valueOf(curve))

        // music_speed, music_pitch
        slide(speed, 1.25f)
        until("music_speed") { abs(it.sound.musicSpeed - 1.25f) < 0.01f }
        slide(pitch, 3f)
        until("music_pitch") { it.sound.musicPitch == 3 && abs(it.sound.musicSpeed - 1.25f) < 0.01f }
        assertEquals("+3", valueOf(pitch))
        // sleep_fade, pause_fade, stop_on_swipe_away, pause_on_mute
        slide(sleepFade, 10f)
        until("sleep_fade") { it.sound.sleepFadeSeconds == 10 }
        tap(pauseFade)
        until("pause_fade") { it.sound.pauseFade }
        tap(stopOnSwipe)
        until("stop_on_swipe_away") { it.stopOnSwipeAway }
        tap(pauseOnMute)
        until("pause_on_mute") { it.behavior.pauseOnMute }
        // face_down_pause (new in 1.8.5)
        tap(faceDown)
        until("face_down_pause") { it.behavior.faceDownPause }

        // Every row now differs from how Prism comes, says so, and the page still fits.
        assertBudgets("everything changed")
        assertEquals(listOf(6, 2, 5, 2, 5), groupSizes())
        assertEquals(20, changedRows())
        // The root's line is live.
        val line = SettingsPageId.PLAYBACK.value(stored)
        assertTrue(line, tr(R.string.root_crossfade_seconds, 9) in line && noRepeats in line)

        // Reset this page lists each of them and puts them all back.
        val lines = resetLines(confirm = true)
        assertEquals(lines.joinToString("\n"), 20, lines.size)
        assertTrue(lines.toString(), lines.any { it.startsWith("$shuffleButton: ${ShuffleButton.SHUFFLE_ONLY.label} → ${ShuffleButton.SHUFFLE_AND_SMART.label}") })
        until("the reset") { it.behavior == d.behavior && it.sound == d.sound }
        until("the reset") {
            it.autoplay == d.autoplay && it.autoplaySource == d.autoplaySource && it.radioSongsOnly == d.radioSongsOnly && it.songsEverywhere == d.songsEverywhere &&
                it.videoContinues == d.videoContinues && it.mix == d.mix && it.mixAuto == d.mixAuto && it.mixSeconds == d.mixSeconds &&
                it.mixBeatMatch == d.mixBeatMatch && it.mixKeepAlbums == d.mixKeepAlbums && it.stopOnSwipeAway == d.stopOnSwipeAway
        }
        assertEquals(0, changedRows())
        assertEquals(listOf(6, 2, 2, 2, 5), groupSizes())
    }

    /** D57 and #416: the two new rows, off (as Prism was) until picked. */
    @Test fun theShuffleGroupIsNew() {
        show()
        assertEquals(SettingsTags.ROW_CHOICE, typeOf(shuffleButton))
        assertEquals(ShuffleButton.SHUFFLE_AND_SMART.label, valueOf(shuffleButton))
        assertEquals(SettingsTags.ROW_SWITCH, typeOf(keepShuffle))
        assertFalse(stored.behavior.keepShuffle)
        assertEquals(ShuffleButton.SHUFFLE_AND_SMART, stored.behavior.shuffleButton)
        // Found by search, in their group, on this page.
        for (title in listOf(shuffleButton, keepShuffle)) {
            val e = SettingsIndex.entries.single { it.title == title }
            assertEquals(SettingsPageId.PLAYBACK, e.page)
            assertEquals(t(R.string.shuffle), e.group)
        }
        assertTrue(SettingsIndex.search("smart shuffle").any { it.title == shuffleButton })
        // The ⓘ's words, as the owner gave them.
        assertEquals(
            "When the last queue was shuffled, a song you tap in a list plays first and the rest of that list is shuffled. Radios, single songs and videos are left alone.",
            t(R.string.playback_keep_shuffle_info),
        )
    }

    /** 1.8.5: "Pause when face down", a switch in Stopping and pausing, off until switched on, its words behind the ⓘ. */
    @Test fun pauseWhenFaceDownIsASwitchOffByDefault() {
        show()
        assertEquals(SettingsTags.ROW_SWITCH, typeOf(faceDown))
        assertFalse(stored.behavior.faceDownPause)
        val e = SettingsIndex.entries.single { it.title == faceDown }
        assertEquals(SettingsPageId.PLAYBACK, e.page)
        assertEquals(t(R.string.stopping_and_pausing), e.group)
        assertTrue(SettingsIndex.search("face down").any { it.title == faceDown })
        assertTrue(SettingsIndex.search("flip").any { it.title == faceDown })
        assertEquals(
            "Lay your phone screen-down to pause. Pick it up within five minutes and the music plays on.",
            t(R.string.playback_face_down_pause_info),
        )
        // The explanation is behind the ⓘ only: never drawn on the page.
        assertFalse(hasRow(t(R.string.playback_face_down_pause_info)))
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasText(t(R.string.playback_face_down_pause_info))).fetchSemanticsNodes().size)
        tap(faceDown)
        until("face_down_pause") { it.behavior.faceDownPause }
        assertEquals(1, changedRows())
        tap(faceDown)
        until("face_down_pause off") { !it.behavior.faceDownPause }
    }

    /** Links written before the rework still land: the old page's groups by their old names. */
    @Test fun oldGroupNamesOpenTheGroupThatHoldsThemNow() {
        val old = SettingsPageId.PLAYBACK.oldGroups
        val between = t(R.string.playback_song_to_song)
        assertEquals(between, old[t(R.string.crossfade)])
        assertEquals(between, old[t(R.string.how_they_cross)])
        assertEquals(SettingsTarget(SettingsPageId.PLAYBACK, between), SettingsMap.resolve("sound", t(R.string.crossfade)))
        assertEquals(SettingsTarget(SettingsPageId.PLAYBACK, t(R.string.playback_speed_and_pitch)), SettingsMap.resolve("sound", t(R.string.speed_pitch_and_fades)))
        assertEquals(SettingsTarget(SettingsPageId.PLAYBACK, t(R.string.stopping_and_pausing)), SettingsMap.resolve("sound", t(R.string.stopping_and_pausing)))
        assertEquals(SettingsTarget(SettingsPageId.PLAYBACK, t(R.string.what_plays_next)), SettingsMap.resolve("sound", t(R.string.what_plays_next)))
        // The fades went to Stopping and pausing (ledger (c)5); search opens them there.
        for (id in listOf(R.string.sleep_timer_fades_out, R.string.fade_on_pause_and_play)) {
            assertEquals(t(R.string.stopping_and_pausing), SettingsIndex.entries.single { it.title == t(id) }.group)
        }
        // Every line of the page's search names a group the page draws.
        show()
        val drawn = groupTitles().toSet()
        val lost = SettingsIndex.entries.filter { it.page == SettingsPageId.PLAYBACK && it.group.isNotBlank() && it.group !in drawn }
        assertTrue("search lines in groups the page doesn't have: ${lost.map { it.group + " › " + it.title }}", lost.isEmpty())
    }

    @Test fun atLargeTextNothingIsCutOff() {
        container.settings.setMix(true)
        container.settings.setMixAuto(false)
        until("crossfade on") { it.mix && !it.mixAuto }
        show(fontScale = 1.5f)
        assertBudgets("large text")
        assertNoTextCutOff("font scale 1.5")
    }

    private fun drawn(name: String, glass: Boolean, over: (AppSettings) -> AppSettings) {
        show(glass = glass, over = over)
        val file = save("playback", name)
        assertTrue(file.path, file.length() > 10_000)
        assertBudgets(name)
        assertNoTextCutOff(name)
    }

    @Test @Config(qualifiers = "w360dp-h1900dp-xxhdpi") fun drawnLight() = drawn("playback-light", true) { it.copy(themeMode = ThemeMode.LIGHT) }
    @Test @Config(qualifiers = "w360dp-h1900dp-xxhdpi") fun drawnDark() = drawn("playback-dark", true) { it.copy(themeMode = ThemeMode.DARK) }
    @Test @Config(qualifiers = "w360dp-h1900dp-xxhdpi") fun drawnPerformance() = drawn("playback-performance", false) { it.copy(themeMode = ThemeMode.DARK, performanceMode = true) }

    /** With crossfade on at a fixed length (the page's longest state). */
    @Test @Config(qualifiers = "w360dp-h2200dp-xxhdpi") fun drawnWithCrossfadeOn() =
        drawn("playback-dark-crossfade-on", true) { it.copy(themeMode = ThemeMode.DARK, mix = true, mixAuto = false, mixSeconds = 8) }
}
