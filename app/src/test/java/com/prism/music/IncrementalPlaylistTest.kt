package com.prism.music

import com.prism.music.data.KeptPlaylist
import com.prism.music.data.PlaylistRead
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.CollectionKind
import com.prism.music.data.model.CollectionPage
import com.prism.music.data.model.Song
import com.prism.music.data.innertube.YouTubeMusic
import com.prism.music.data.readPlaylist
import com.prism.music.data.refreshPlaylist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Library → Songs' "From" (1.8.4, #473): each start reads the playlist page and only the songs added since the kept
 * copy, from its "Newest first" page. Songs removed or moved in the middle are read in full.
 */
class IncrementalPlaylistTest {
    private fun s(id: String) = Song(id, "Song $id", listOf(ArtistRef("Artist")))
    private fun ids(songs: List<Song>) = songs.joinToString(",") { it.id }
    private fun kept(vararg ids: String, count: Int? = ids.size) = KeptPlaylist(ids.map(::s), count)
    private fun chunk(vararg ids: String, next: String? = null) = YouTubeMusic.PlaylistChunk(ids.map(::s), next, emptyMap())
    private fun page(vararg ids: String, count: Int?, newest: String? = "newest") = CollectionPage(
        id = "playlist", kind = CollectionKind.PLAYLIST, title = "Playlist", subtitle = "", thumbnail = null,
        songs = ids.map(::s), continuation = "more", newestContinuation = newest, trackCount = count,
    )

    @Test fun nothingNewUsesTheKeptList() = runBlocking {
        val read = refreshPlaylist(
            first = page("a", count = 2),
            cached = kept("a", "b"),
            newestPage = { error("no newest page should be read") },
            readAll = { error("no full read should happen") },
        )
        assertEquals("a,b", ids(read.songs))
        assertTrue(read.complete)
        assertEquals(2, read.count)
    }

    @Test fun songsAddedAtTheEndAreReadAndAppended() = runBlocking {
        var asked: String? = null
        val read = refreshPlaylist(
            first = page("a", count = 3),
            cached = kept("a", "b"),
            newestPage = { token -> asked = token; chunk("c", "b") },
            readAll = { error("no full read should happen") },
        )
        assertEquals("newest", asked)
        // Newest first: c (new), then b (the kept copy's last). The playlist itself has the oldest of them first.
        assertEquals("a,b,c", ids(read.songs))
        assertTrue(read.complete)
        assertEquals(3, read.count)
    }

    @Test fun songsAddedAtTheTopGoFirst() = runBlocking {
        val read = refreshPlaylist(
            first = page("c", count = 3),
            cached = kept("a", "b"),
            newestPage = { chunk("c", "a") },
            readAll = { error("no full read should happen") },
        )
        assertEquals("c,a,b", ids(read.songs))
    }

    @Test fun severalNewPagesAreReadUntilTheKeptCopyComes() = runBlocking {
        var pages = 0
        val read = refreshPlaylist(
            first = page("d", count = 4),
            cached = kept("a", "b"),
            newestPage = { token ->
                pages++
                if (token == "newest") chunk("d", "c", next = "older") else chunk("b")
            },
            readAll = { error("no full read should happen") },
        )
        assertEquals(2, pages)
        assertEquals("a,b,c,d", ids(read.songs))
    }

    @Test fun songsRemovedAreReadInFull() = runBlocking {
        var full = 0
        val read = refreshPlaylist(
            first = page("a", count = 2),
            cached = kept("a", "b", "c"),
            newestPage = { error("no newest page should be read") },
            readAll = { full++; PlaylistRead(listOf(s("a"), s("b")), complete = true, count = 2) },
        )
        assertEquals(1, full)
        assertEquals("a,b", ids(read.songs))
    }

    @Test fun aCountThatStillDoesNotFitIsReadInFull() = runBlocking {
        var full = 0
        val read = refreshPlaylist(
            first = page("c", count = 5),
            cached = kept("a", "b"),
            newestPage = { chunk("c", "b") },
            readAll = { full++; PlaylistRead(listOf(s("a")), complete = true, count = 5) },
        )
        assertEquals(1, full)
        assertEquals("a", ids(read.songs))
    }

    @Test fun aNewSongInTheMiddleIsReadInFull() = runBlocking {
        var full = 0
        val read = refreshPlaylist(
            first = page("d", count = 4),
            cached = kept("a", "b", "c"),
            newestPage = { chunk("d", "b") },
            readAll = { full++; PlaylistRead(listOf(s("a"), s("d"), s("b"), s("c")), complete = true, count = 4) },
        )
        assertEquals(1, full)
        assertEquals("a,d,b,c", ids(read.songs))
    }

    @Test fun withoutANewestPageTheWholePlaylistIsRead() = runBlocking {
        var full = 0
        refreshPlaylist(
            first = page("a", count = 3, newest = null),
            cached = kept("a", "b"),
            newestPage = { error("no newest page should be read") },
            readAll = { full++; PlaylistRead(listOf(s("a"), s("b"), s("c")), complete = true, count = 3) },
        )
        assertEquals(1, full)
    }

    @Test fun aNewestPageThatDoesNotComeReadsTheWholePlaylist() = runBlocking {
        var full = 0
        refreshPlaylist(
            first = page("a", count = 3),
            cached = kept("a", "b"),
            newestPage = { throw IOException("offline") },
            readAll = { full++; PlaylistRead(listOf(s("a"), s("b"), s("c")), complete = true, count = 3) },
        )
        assertEquals(1, full)
    }

    @Test fun nothingKeptReadsTheWholePlaylist() = runBlocking {
        var full = 0
        val read = refreshPlaylist(
            first = page("a", count = 2),
            cached = null,
            newestPage = { error("no newest page should be read") },
            readAll = { full++; PlaylistRead(listOf(s("a"), s("b")), complete = true, count = 2) },
        )
        assertEquals(1, full)
        assertEquals("a,b", ids(read.songs))
    }
}
