package com.prism.music

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

/**
 * #363: a Latin search finds titles in other scripts by how they sound. Japanese kanji need Kuromoji's dictionary,
 * which unit tests have inside its jar ([com.prism.music.data.lyrics.roman.Japanese.bundled]).
 */
class SearchTagsTest {
    @Before fun clearIndexed() = SearchTags.clearIndex()
    @After fun clearIndexedAgain() = SearchTags.clearIndex()

    private fun song(id: String, title: String) =
        Song(id = "local:$id", title = title, artists = listOf(ArtistRef("Someone")))

    @Test fun japaneseKanjiGetTheirReadings() {
        val tags = SearchTags.forTitle("鳥の歌")
        assertTrue("$tags", tags.contains("tori no uta"))
        assertTrue("$tags", tags.contains("torinouta"))
    }

    @Test fun kanaTitlesGetTheirReadings() {
        val hiragana = SearchTags.forTitle("とりのうた")
        assertTrue("$hiragana", hiragana.contains("torinouta"))
        val katakana = SearchTags.forTitle("トリノウタ")
        assertTrue("$katakana", katakana.contains("torinouta"))
    }

    @Test fun koreanIsReadInRevisedRomanization() {
        assertEquals(listOf("saranghae"), SearchTags.forTitle("사랑해"))
    }

    @Test fun cyrillicAndGreekAreRead() {
        assertTrue(SearchTags.forTitle("Я тебя люблю").any { it.equals("ya tebya lyublyu", ignoreCase = true) })
        assertTrue(SearchTags.forTitle("Я тебя люблю").any { it.equals("yatebyalyublyu", ignoreCase = true) })
        assertTrue(SearchTags.forTitle("Σ' αγαπώ").any { it.equals("s' agapo", ignoreCase = true) })
    }

    @Test fun aLatinTitleNeedsNoTags() {
        assertTrue(SearchTags.forTitle("Tori no Uta").isEmpty())
    }

    @Test fun searchingBySoundFindsJapaneseTitles() {
        for (title in listOf("鳥の歌", "とりのうた", "トリノウタ")) {
            val s = song("1", title)
            SearchTags.index(s.id, SearchTags.forTitle(title), emptyList(), emptyList())
            for (query in listOf("tori no uta", "torinouta", "TORI NO UTA")) {
                assertEquals("[$query] on [$title]", listOf(s.id), phoneMatches(query, listOf(s)).map { it.id })
            }
            // A download has no indexed tags: they're worked out for it all the same.
            SearchTags.clearIndex()
            assertEquals("[$title] without the map", listOf(s.id), phoneMatches("tori no uta", listOf(s)).map { it.id })
        }
    }

    @Test fun aDifferentTitleDoesntMatch() {
        val s = song("2", "鼻の歌")
        SearchTags.index(s.id, SearchTags.forTitle(s.title), emptyList(), emptyList())
        assertTrue(phoneMatches("tori no uta", listOf(s)).isEmpty())
    }

    @Test fun searchingBySoundFindsArtistsAndAlbums() {
        val s = Song(
            id = "local:3", title = "Unrelated", artists = listOf(ArtistRef("鳥の歌")), album = AlbumRef("君の名は"),
        )
        SearchTags.index(s.id, emptyList(), SearchTags.forText(s.artistText), emptyList())
        assertEquals("artist", listOf(s.id), phoneMatches("tori no uta", listOf(s)).map { it.id })
        assertEquals("artist, no spaces", listOf(s.id), phoneMatches("torinouta", listOf(s)).map { it.id })
        SearchTags.index(s.id, emptyList(), emptyList(), SearchTags.forText(s.album?.title.orEmpty()))
        assertEquals("album", listOf(s.id), phoneMatches("kimi no na wa", listOf(s)).map { it.id })
        assertEquals("album, no spaces", listOf(s.id), phoneMatches("kiminonawa", listOf(s)).map { it.id })
        // Downloads have no indexed tags: artists and albums are read lazily for them too.
        SearchTags.clearIndex()
        assertEquals("without the maps", listOf(s.id), phoneMatches("tori no uta", listOf(s)).map { it.id })
        assertEquals("without the maps", listOf(s.id), phoneMatches("kimi no na wa", listOf(s)).map { it.id })
    }
}
