package com.prism.music

import com.prism.music.data.KeptPlaylist
import com.prism.music.data.PlaylistRead
import com.prism.music.data.PlaylistSongs
import com.prism.music.data.PlaylistSongsCache
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Library → Songs' "From" (1.8.4, #473): a whole playlist is kept on the phone between starts, and the next start is
 * given it to refresh (only its new songs), rather than read page after page again.
 */
class PlaylistSongsCacheTest {
    @get:Rule val dir = TemporaryFolder()

    private fun s(id: String) = Song(id, "Song $id", listOf(ArtistRef("Artist")))
    private fun read(vararg ids: String, count: Int? = ids.size) = PlaylistRead(ids.map(::s), complete = true, count = count)

    @Test fun theNextStartGetsTheKeptPlaylistToRefresh() = runBlocking {
        var given: KeptPlaylist? = null
        var loads = 0
        val load: suspend (String, KeptPlaylist?) -> PlaylistRead = { _, cached ->
            loads++
            given = cached
            if (cached == null) read("a", "b") else PlaylistRead(cached.songs + s("c"), complete = true, count = 3)
        }
        assertEquals("a,b", PlaylistSongs(load, PlaylistSongsCache(dir.root)).songs("PLx")!!.joinToString(",") { it.id })
        assertNull("the first start has nothing to start from", given)
        // A new run of the app: another PlaylistSongs and cache, the same folder.
        assertEquals("a,b,c", PlaylistSongs(load, PlaylistSongsCache(dir.root)).songs("PLx")!!.joinToString(",") { it.id })
        assertEquals("a,b", given!!.songs.joinToString(",") { it.id })
        assertEquals(2, given!!.count)
        assertEquals(2, loads)
    }

    @Test fun aShortReadIsNeverKept() = runBlocking {
        val cache = PlaylistSongsCache(dir.root)
        PlaylistSongs({ _, _ -> PlaylistRead(listOf(s("a")), complete = false, count = null) }, cache).songs("PLx")
        assertNull(cache.read("PLx"))
    }

    @Test fun pickingItAgainRefreshesOnlyItsNewSongs() = runBlocking {
        var given: KeptPlaylist? = null
        var loads = 0
        val load: suspend (String, KeptPlaylist?) -> PlaylistRead = { _, cached ->
            loads++
            given = cached
            if (cached == null) read("a") else PlaylistRead(cached.songs + s("b"), complete = true, count = 2)
        }
        val lists = PlaylistSongs(load, PlaylistSongsCache(dir.root))
        lists.songs("PLx")
        lists.refresh("PLx")
        assertEquals("a,b", lists.songs("PLx")!!.joinToString(",") { it.id })
        assertEquals("the kept copy is the starting point", "a", given!!.songs.joinToString(",") { it.id })
        assertEquals(2, loads)
    }

    @Test fun prismListsAreNeverKept() = runBlocking {
        var loads = 0
        var given: KeptPlaylist? = null
        val load: suspend (String, KeptPlaylist?) -> PlaylistRead = { _, cached -> loads++; given = cached; read("a") }
        PlaylistSongs(load, PlaylistSongsCache(dir.root)).songs("prism:123")
        // A new run reads Prism's own lists from the phone as they are now, not from a kept copy.
        PlaylistSongs(load, PlaylistSongsCache(dir.root)).songs("prism:123")
        assertNull(given)
        assertEquals(2, loads)
    }

    @Test fun signingOutForgetsThem() = runBlocking {
        val cache = PlaylistSongsCache(dir.root)
        val lists = PlaylistSongs({ _, _ -> read("a") }, cache)
        lists.songs("PLx")
        lists.clear()
        assertNull(cache.read("PLx"))
    }
}
