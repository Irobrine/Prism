package com.prism.music

import com.prism.music.data.PlaylistMembership
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Add to playlist (multiple playlists): taking songs out here is remembered like adding them is, so the marks on the
 * next opening don't still say a kept copy of a YouTube playlist holds songs that were just removed.
 */
class PlaylistMembershipRemovalTest {
    @After fun cleanUp() = PlaylistMembership.forget()

    @Test fun songsTakenOutHereNoLongerCount() {
        PlaylistMembership.forget()
        val contents = mapOf("VLPL1" to listOf("a", "b"))
        assertEquals(mapOf("VLPL1" to 2), PlaylistMembership.counts(listOf("a", "b"), contents))
        // However the playlist's id is written.
        PlaylistMembership.noteRemoved("PL1", listOf("a"))
        assertEquals(mapOf("VLPL1" to 1), PlaylistMembership.counts(listOf("a", "b"), contents))
        assertEquals(emptyMap<String, Int>(), PlaylistMembership.counts(listOf("a"), contents))
        // Added back: it counts again.
        PlaylistMembership.noteAdded("PL1", listOf("a"))
        assertEquals(mapOf("VLPL1" to 2), PlaylistMembership.counts(listOf("a", "b"), contents))
    }

    /** Done adds only what's missing, so the songs each playlist holds are needed, not just how many. */
    @Test fun theSongsEachPlaylistHoldsAreKnown() {
        PlaylistMembership.forget()
        val contents = mapOf("VLPL1" to listOf("a", "b", "c"), "prism:x" to listOf("c"))
        assertEquals(
            mapOf("VLPL1" to setOf("a", "c"), "prism:x" to setOf("c")),
            PlaylistMembership.held(listOf("a", "c", "z"), contents),
        )
        PlaylistMembership.noteRemoved("PL1", listOf("a"))
        assertEquals(mapOf("VLPL1" to setOf("c"), "prism:x" to setOf("c")), PlaylistMembership.held(listOf("a", "c"), contents))
    }
}
