package com.prism.music

import android.content.Context
import com.prism.music.data.SongMetadata
import com.prism.music.data.local.SearchTags
import com.prism.music.data.model.AlbumRef
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.ui.screens.phoneMatches
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * #363 + 1.8.3: multilingual search reads what Prism shows, so a title, artist or album edited on this phone is found
 * by its new words and their Latin spellings (the indexed spellings of the file's own fields are no longer used).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MetadataSearchTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences("song_metadata", Context.MODE_PRIVATE)
    private lateinit var store: SongMetadata

    private val song = Song(
        "local:1", "Original Title", listOf(ArtistRef("Old Artist")), AlbumRef("Old Album"),
    )

    @Before fun setUp() {
        prefs.edit().clear().commit()
        SearchTags.clearIndex()
        store = SongMetadata(context)
    }

    @After fun reset() {
        prefs.edit().clear().commit()
        SearchTags.clearIndex()
        SongMetadata.shared = null
    }

    @Test fun anEditedTitleIsFoundInsteadOfTheOldOne() {
        store.set(listOf(song.id), title = "New Song")
        SearchTags.index(song.id, SearchTags.forTitle(song.title), emptyList(), emptyList())
        assertEquals(listOf(song.id), phoneMatches("new song", listOf(song)).map { it.id })
        assertTrue("the file's old title isn't what Prism shows", phoneMatches("original title", listOf(song)).isEmpty())
    }

    @Test fun anEditedTitleInAnotherScriptIsFoundByItsSound() {
        store.set(listOf(song.id), title = "\u9ce5\u306e\u6b4c") // 鳥の歌
        // The file was indexed with a Latin title (no tags), so the edited title's readings are made now.
        SearchTags.index(song.id, emptyList(), emptyList(), emptyList())
        assertEquals(listOf(song.id), phoneMatches("tori no uta", listOf(song)).map { it.id })
        assertEquals(listOf(song.id), phoneMatches("torinouta", listOf(song)).map { it.id })
    }

    @Test fun editedArtistsAndAlbumsAreFoundByTheirSoundToo() {
        store.set(listOf(song.id), artist = "\u9ce5\u306e\u6b4c", album = "\u541b\u306e\u540d\u306f") // 鳥の歌, 君の名は
        SearchTags.index(song.id, emptyList(), emptyList(), emptyList())
        assertEquals(listOf(song.id), phoneMatches("tori no uta", listOf(song)).map { it.id })
        assertEquals(listOf(song.id), phoneMatches("kimi no na wa", listOf(song)).map { it.id })
    }

    @Test fun aSongWithoutAnEditKeepsItsIndexedTags() {
        val text = SearchTags.textFor(song, listOf("old title"), listOf("old artist"), emptyList())
        assertEquals("Original Title", text.title)
        assertEquals(listOf("old title"), text.titleTags)
        assertEquals(listOf("old artist"), text.artistTags)
    }
}
