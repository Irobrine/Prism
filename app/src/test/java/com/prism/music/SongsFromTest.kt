package com.prism.music

import com.prism.music.data.PlaylistRead
import com.prism.music.data.PlaylistSongs
import com.prism.music.data.SongsFrom
import com.prism.music.data.innertube.YouTubeMusic
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.CollectionKind
import com.prism.music.data.model.CollectionPage
import com.prism.music.data.model.Song
import com.prism.music.data.readPlaylist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Library → Songs' "From" (1.8.3, #382): liked songs plus chosen playlists, once each; the choice kept as text; whole
 * playlists read once and kept.
 */
class SongsFromTest {
    private fun s(id: String) = Song(id, "Song $id", listOf(ArtistRef("Artist")))

    @Test fun likedFirstThenEachPlaylistOnceEach() {
        val liked = listOf(s("a"), s("b"))
        val gym = listOf(s("c"), s("a"), s("d"))
        val road = listOf(s("d"), s("e"))
        val from = SongsFrom(true, listOf("gym", "road"))
        assertEquals(listOf("a", "b", "c", "d", "e"), SongsFrom.merge(liked, listOf(gym, road), from).map { it.id })
        assertEquals("without liked songs", listOf("c", "a", "d", "e"), SongsFrom.merge(liked, listOf(gym, road), from.copy(liked = false)).map { it.id })
        assertEquals("a playlist with repeats", listOf("c"), SongsFrom.merge(emptyList(), listOf(listOf(s("c"), s("c"))), SongsFrom(false, listOf("x"))).map { it.id })
    }

    @Test fun theChoiceIsKeptAsText() {
        val from = SongsFrom(false, listOf("PLabc", "prism_123"))
        assertEquals(from, SongsFrom.decode(from.encode()))
        assertEquals(SongsFrom(true, listOf("PLabc")), SongsFrom.decode(SongsFrom(true, listOf("PLabc")).encode()))
        assertTrue("nothing kept: liked songs, as before", SongsFrom.decode(null).isDefault)
        assertTrue(SongsFrom.decode("").isDefault)
        assertTrue("nothing at all chosen falls back to liked songs", SongsFrom.decode("|").isDefault)
        assertEquals("repeats and blanks dropped", listOf("a", "b"), SongsFrom.decode("liked|a,,b,a").playlists)
        assertFalse(SongsFrom(true, listOf("a")).isDefault)
    }

    @Test fun aPlaylistIsReadOnceUntilRefreshed() = runBlocking {
        var reads = 0
        val lists = PlaylistSongs { id -> reads++; if (id == "bad") error("offline") else PlaylistRead(listOf(s("$id-1")), complete = true) }
        assertEquals("x-1", lists.songs("x")!!.single().id)
        lists.songs("x")
        assertEquals(1, reads)
        lists.refresh("x")
        lists.songs("x")
        assertEquals(2, reads)
        assertNull("a playlist that won't load shows nothing, no crash", lists.songs("bad"))
    }

    @Test fun aReadThatStoppedShortIsReadAgainNextTime() = runBlocking {
        var reads = 0
        val lists = PlaylistSongs { id ->
            reads++
            if (reads == 1) PlaylistRead(listOf(s("$id-1")), complete = false)
            else PlaylistRead(listOf(s("$id-1"), s("$id-2"), s("$id-3")), complete = true)
        }
        assertEquals("the page that came shows meanwhile", listOf("x-1"), lists.songs("x")!!.map { it.id })
        assertFalse("but it isn't kept as the whole playlist", lists.isComplete("x"))
        assertEquals("the rest comes next time", listOf("x-1", "x-2", "x-3"), lists.songs("x")!!.map { it.id })
        assertTrue(lists.isComplete("x"))
        lists.songs("x")
        assertEquals("then it's kept whole", 2, reads)
    }

    @Test fun everyPageOfAPlaylistIsRead() = runBlocking {
        val first = page(listOf("a", "b"), continuation = "next")
        val read = readPlaylist(first, page = { token ->
            assertEquals("next", token)
            YouTubeMusic.PlaylistChunk(listOf(s("c"), s("d")), next = null, setVideoIds = emptyMap())
        }, retryMs = 0)
        assertEquals(listOf("a", "b", "c", "d"), read.songs.map { it.id })
        assertTrue(read.complete)
    }

    @Test fun aPageThatFailsIsAskedAgain() = runBlocking {
        var tries = 0
        val read = readPlaylist(page(listOf("a"), continuation = "next"), page = {
            if (++tries < 3) throw java.io.IOException("busy")
            YouTubeMusic.PlaylistChunk(listOf(s("b")), next = null, setVideoIds = emptyMap())
        }, retryMs = 0)
        assertEquals("a page lost once doesn't leave the rest out", listOf("a", "b"), read.songs.map { it.id })
        assertTrue(read.complete)
        assertEquals(3, tries)
    }

    @Test fun aPageThatNeverComesLeavesTheReadShortAndUnkept() = runBlocking {
        val read = readPlaylist(page(listOf("a"), continuation = "next"), page = { throw java.io.IOException("offline") }, retryMs = 0)
        assertEquals(listOf("a"), read.songs.map { it.id })
        assertFalse("shown, but not as the whole playlist", read.complete)
    }

    @Test fun leavingTheScreenDoesNotLeaveAShortListKept() = runBlocking {
        var reads = 0
        val lists = PlaylistSongs { id ->
            reads++
            // The first read is cancelled midway (the screen was left): it mustn't be kept as if it were whole.
            if (reads == 1) throw kotlinx.coroutines.CancellationException("the screen was left")
            PlaylistRead(listOf(s("$id-1"), s("$id-2")), complete = true)
        }
        try {
            lists.songs("x")
            throw AssertionError("the read should have been cancelled")
        } catch (_: kotlinx.coroutines.CancellationException) {
        }
        assertEquals("the cancelled read kept nothing", listOf("x-1", "x-2"), lists.songs("x")!!.map { it.id })
        assertEquals(2, reads)
    }

    @Test fun leavingTheScreenStopsTheRead() = runBlocking {
        try {
            readPlaylist(page(listOf("a"), continuation = "next"), page = {
                throw kotlinx.coroutines.CancellationException("the screen was left")
            }, retryMs = 0)
            throw AssertionError("the read should have been cancelled, not returned short")
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Passed on: the caller keeps nothing, so the next visit reads it all again.
        }
    }

    private fun page(songs: List<String>, continuation: String?) = CollectionPage(
        id = "playlist", kind = CollectionKind.PLAYLIST, title = "Playlist", subtitle = "", thumbnail = null,
        songs = songs.map(::s), continuation = continuation,
    )
}
