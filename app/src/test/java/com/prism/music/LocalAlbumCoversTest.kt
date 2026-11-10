package com.prism.music

import com.prism.music.data.PlaylistCovers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Covers picked for an album of files on the phone, kept by its album key (its MediaStore id or edited title). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalAlbumCoversTest {
    private fun covers(): PlaylistCovers {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("playlist_covers", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        return PlaylistCovers(app)
    }

    @Test fun aFileAlbumsPickedCoverShowsInsteadOfItsOwnOnlyForThatAlbum() {
        val c = covers()
        val own = "file:///data/user/0/com.prism.music/files/artwork/local/a1.jpg"
        // Nothing picked: the files' own picture.
        assertEquals(own, c.localAlbumArt("id:1", own))
        c.useImage(c.localAlbumKey("id:1"), "https://example.org/local-album.jpg")
        assertEquals("https://example.org/local-album.jpg", c.localAlbumArt("id:1", own))
        // Another album, and an online album whose id happens to be the same number, keep their own.
        assertEquals(own, c.localAlbumArt("id:2", own))
        assertEquals("https://example.org/online.jpg", c.albumArt("1", "https://example.org/online.jpg"))
        // A title the listener edited is an album key of its own.
        assertEquals(own, c.localAlbumArt("edited:my album", own))
        // "Use the original" puts it back, and the picked cover is kept on the phone for next time.
        c.useImage(c.localAlbumKey("id:3"), "https://example.org/kept.jpg")
        assertEquals("https://example.org/kept.jpg", PlaylistCovers(RuntimeEnvironment.getApplication()).localAlbumArt("id:3", own))
        c.reset(c.localAlbumKey("id:3"))
        assertEquals(own, c.localAlbumArt("id:3", own))
        c.reset(c.localAlbumKey("id:1"))
    }

    @Test fun aLocalSongsArtIsFoundWithoutALocalScan() {
        // Before the files are scanned (a restored look, a widget), a local song simply keeps its own picture.
        val c = covers()
        val song = com.prism.music.data.model.Song(
            "local:1", "Song", album = com.prism.music.data.model.AlbumRef("Album"), thumbnail = "file:///data/own.jpg",
        )
        assertEquals("file:///data/own.jpg", c.art(song))
    }
}
