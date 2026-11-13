package com.prism.music

import com.prism.music.download.DownloadPriority
import com.prism.music.download.inDownloadPriorityOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Always download" puts the song streaming now first, then the songs after it in the order they'll play, then a
 * playlist's rest: a download already coming down is left where it is and finishes first.
 */
class DownloadPriorityTest {
    private fun order(vararg ids: String): List<String> = ids.toList()

    private fun sorted(ids: List<String>, ranks: Map<String, Int>) =
        inDownloadPriorityOrder(ids, { it }) { ranks[it] ?: DownloadPriority.REST }.toList()

    @Test fun thePlayingSongComesBeforeTheSongsAheadAndTheRest() {
        val ranks = mapOf("now" to DownloadPriority.PLAYING, "next" to DownloadPriority.ahead(0), "then" to DownloadPriority.ahead(1))
        assertEquals(listOf("now", "next", "then", "a", "b"), sorted(order("a", "b", "then", "now", "next"), ranks))
    }

    @Test fun theSongsAheadKeepTheOrderTheyWillPlayIn() {
        val ranks = mapOf(
            "third" to DownloadPriority.ahead(2),
            "first" to DownloadPriority.ahead(0),
            "second" to DownloadPriority.ahead(1),
        )
        assertEquals(listOf("first", "second", "third"), sorted(order("third", "first", "second"), ranks))
    }

    @Test fun theRestKeepsItsOwnOrder() {
        assertEquals(listOf("a", "b", "c", "d"), sorted(order("a", "b", "c", "d"), emptyMap()))
        // A song that becomes the one playing moves up; the rest around it doesn't otherwise change.
        assertEquals(listOf("c", "a", "b", "d"), sorted(order("a", "b", "c", "d"), mapOf("c" to DownloadPriority.PLAYING)))
    }

    @Test fun aheadRanksAreInTurnAndNeverNegative() {
        assertEquals(DownloadPriority.AHEAD, DownloadPriority.ahead(0))
        assertEquals(DownloadPriority.AHEAD + 1, DownloadPriority.ahead(1))
        assertEquals(DownloadPriority.AHEAD, DownloadPriority.ahead(-4))
        assert(DownloadPriority.PLAYING < DownloadPriority.ahead(0))
        assert(DownloadPriority.ahead(5) < DownloadPriority.REST)
    }
}
