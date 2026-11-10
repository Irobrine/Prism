package com.prism.music

import android.content.Context
import com.prism.music.data.SongMetadata
import com.prism.music.data.SongMetadataEdit
import com.prism.music.data.SongVersions
import com.prism.music.data.model.AlbumRef
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Artist, album and genre edits (1.8.3): kept on the phone by song, shown in place of the song's own, on one song or
 * many at once, and taken back; an edit follows a picked version too, and the file itself is never changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SongMetadataTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences("song_metadata", Context.MODE_PRIVATE)
    private val versionsPrefs = context.getSharedPreferences("song_metadata_versions_test", Context.MODE_PRIVATE)

    private val song = Song(
        "a", "Night Drive", listOf(ArtistRef("The Band", "UCband")), AlbumRef("Roads", "MPREb_roads"),
        212, "https://a.jpg",
    )

    @After fun reset() {
        prefs.edit().clear().commit()
        versionsPrefs.edit().clear().commit()
        SongMetadata.shared = null
        SongVersions.shared = null
    }

    @Test fun anEditShowsInPlaceOfTheSongsOwnAndStaysOnThePhone() {
        val store = SongMetadata(context)
        store.set(listOf("a"), artist = "The Band (live)", album = "Roads (2026)", genre = "Rock")
        val shown = store.shown(song)
        assertEquals("a", shown.id)
        assertEquals(listOf(ArtistRef("The Band (live)", "UCband")), shown.artists)
        assertEquals(AlbumRef("Roads (2026)", "MPREb_roads"), shown.album)
        assertEquals("Rock", store.genreOf("a"))
        // Read in at the next start, before anything shows it.
        val again = SongMetadata(context)
        assertEquals("The Band (live)", again.shown(song).artistText)
        assertEquals("Roads (2026)", again.shown(song).album?.title)
        assertEquals("Rock", again.genreOf("a"))
    }

    @Test fun oneChangeLeavesTheOthersAsTheyWere() {
        val store = SongMetadata(context)
        store.set(listOf("a"), artist = "Ana", genre = "Jazz")
        store.set(listOf("a"), album = "Blue")
        assertEquals(SongMetadataEdit("Ana", "Blue", "Jazz"), store.of("a"))
    }

    @Test fun severalSongsTakeTheTypedFieldsAtOnce() {
        val b = Song("b", "Bravo", listOf(ArtistRef("Bo")), AlbumRef("Other"))
        val store = SongMetadata(context)
        store.set(listOf("a", "b", "a"), artist = "  The   Band  ", album = "Roads")
        assertEquals("The Band", store.of("a")?.artist)
        assertEquals("The Band", store.of("b")?.artist)
        assertEquals("Roads", store.shown(b).album?.title)
        // Nothing typed is no edit (not a blank name).
        store.set(listOf("a"), artist = "   ")
        assertEquals("The Band", store.of("a")?.artist)
    }

    @Test fun resetPutsTheSongBack() {
        val store = SongMetadata(context)
        store.set(listOf("a"), artist = "Mine", album = "Mine", genre = "Mine")
        store.clear(listOf("a"))
        assertNull(store.of("a"))
        assertEquals(song, store.shown(song))
        assertNull(store.genreOf("a"))
        assertNull(SongMetadata(context).of("a"))
    }

    @Test fun aPickedVersionStillShowsWithTheEditOnTop() {
        val official = Song(
            "off", "Night Drive", listOf(ArtistRef("The Band", "UCband")), AlbumRef("Roads", "MPREb_roads"),
            212, "https://official.jpg",
        )
        val video = Song("vid", "Night Drive (Official Video)", listOf(ArtistRef("TheBandVEVO")), null, 250, isVideo = true)
        val versions = SongVersions(versionsPrefs)
        versions.set("vid", SongVersions.Pick("off", "The Band · Roads · 3:32", official))
        val store = SongMetadata(context)
        store.set(listOf("vid"), artist = "My Band", album = "My Roads")
        val shown = SongVersions.shownOf(video)
        assertEquals("vid", shown.id)
        assertEquals("Night Drive", shown.title)
        assertEquals(listOf(ArtistRef("My Band", "UCband")), shown.artists)
        assertEquals(AlbumRef("My Roads", "MPREb_roads"), shown.album)
    }

    @Test fun namesAreTidiedAndLimited() {
        assertEquals("The Band", SongMetadataEdit.clean("  The \t Band "))
        assertNull(SongMetadataEdit.clean("   "))
        assertEquals(SongMetadataEdit.MAX_LENGTH, SongMetadataEdit.clean("x".repeat(SongMetadataEdit.MAX_LENGTH + 10))!!.length)
    }
}
