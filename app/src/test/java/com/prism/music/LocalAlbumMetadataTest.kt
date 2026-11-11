package com.prism.music

import android.content.Context
import android.net.Uri
import com.prism.music.data.SongMetadata
import com.prism.music.data.local.LocalMusic
import com.prism.music.data.model.AlbumRef
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.ui.screens.albumsOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Library → Local files' albums (1.8.3): a song whose album the listener edited belongs to an album named that, joins
 * one of the files' albums when its new name names just that one, and songs edited to one name share an album.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalAlbumMetadataTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences("song_metadata", Context.MODE_PRIVATE)
    private lateinit var store: SongMetadata

    @Before fun setUp() {
        prefs.edit().clear().commit()
        store = SongMetadata(context)
    }

    @After fun reset() {
        prefs.edit().clear().commit()
        SongMetadata.shared = null
    }

    private fun track(id: String, albumId: Long, album: String?) = LocalMusic.Track(
        Song("local:$id", "Song $id", listOf(ArtistRef("The Band")), album?.let { AlbumRef(it) }),
        Uri.EMPTY, albumId, 0, 0, 0, "audio/mpeg", 0,
    )

    @Test fun anEditedAlbumStandsUnderItsName() {
        val a = track("a", 1, "Old Album")
        store.set(listOf(a.song.id), album = "New Album")
        val albums = albumsOf(listOf(a))
        assertEquals(1, albums.size)
        assertEquals("New Album", albums.single().title)
        assertEquals(listOf(a.song.id), albums.single().tracks.map { it.song.id })
    }

    @Test fun songsEditedToTheSameAlbumAreOneAlbum() {
        val a = track("a", 1, "First")
        val b = track("b", 2, "Second")
        store.set(listOf(a.song.id, b.song.id), album = "Together")
        val albums = albumsOf(listOf(a, b))
        assertEquals(1, albums.size)
        assertEquals("Together", albums.single().title)
        assertEquals(setOf(a.song.id, b.song.id), albums.single().tracks.map { it.song.id }.toSet())
    }

    @Test fun anEditedSongJoinsTheFilesOwnAlbumOfThatName() {
        val home = track("home", 10, "Shared Album")
        val moved = track("moved", 20, "Somewhere Else")
        store.set(listOf(moved.song.id), album = "Shared Album")
        val albums = albumsOf(listOf(home, moved))
        assertEquals(1, albums.size)
        assertEquals("id:10", albums.single().id)
        assertEquals("Shared Album", albums.single().title)
        assertEquals(setOf(home.song.id, moved.song.id), albums.single().tracks.map { it.song.id }.toSet())
    }

    @Test fun aSongLeftAsItWasKeepsItsFilesAlbum() {
        val a = track("a", 7, "Own Album")
        val albums = albumsOf(listOf(a))
        assertEquals("id:7", albums.single().id)
        assertEquals("Own Album", albums.single().title)
    }
}
