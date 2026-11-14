package com.prism.music

import com.prism.music.data.model.AlbumRef
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.ui.screens.libraryHaystack
import com.prism.music.ui.screens.libraryMatches
import com.prism.music.ui.screens.libraryWords
import com.prism.music.ui.screens.sortedByKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Random

/**
 * Sorting a Library (1.8.5): the keys are made once per entry, not again on every comparison (see [sortedByKey]), and
 * nothing about the order changes. A song's artist line is kept with it rather than joined anew on every read.
 */
class SortingKeyTest {
    private val artists = listOf("The Killers", "Beyoncé", "AC/DC", "坂本龍一", "Radiohead", "A", "The A's")
    private val albums = listOf("Hot Fuss", "Lemonade", "Back in Black", "async", "OK Computer", "None", "a")
    private val titles = listOf("Mr. Brightside", "Crazy in Love", "Hells Bells", "andata", "Karma Police", "A Song", "a song")

    private fun songs(n: Int): List<Song> {
        val r = Random(7)
        return (0 until n).map { i ->
            Song(
                id = "id$i",
                title = titles[r.nextInt(titles.size)],
                artists = listOf(ArtistRef(artists[r.nextInt(artists.size)])),
                album = AlbumRef(albums[r.nextInt(albums.size)]),
                durationSec = r.nextInt(300),
            )
        }
    }

    @Test fun keysAreMadeOnceAndTheOrderIsTheSame() {
        val list = songs(400)
        var calls = 0
        val once = list.sortedByKey { calls++; it.artistText.lowercase() }
        assertEquals("one key per song", list.size, calls)
        assertEquals("same order as sortedBy", list.sortedBy { it.artistText.lowercase() }, once)

        calls = 0
        val twoOnce = list.sortedByKey({ calls++; it.primaryArtist.lowercase() }, { calls++; it.album?.title?.lowercase() ?: "" })
        assertEquals("two keys per song", list.size * 2, calls)
        assertEquals(
            list.sortedWith(compareBy<Song> { it.primaryArtist.lowercase() }.thenBy { it.album?.title?.lowercase() ?: "" }),
            twoOnce,
        )

        calls = 0
        val threeOnce = list.sortedByKey(
            { calls++; it.primaryArtist.lowercase() },
            { calls++; it.album?.title?.lowercase() ?: "" },
            { calls++; it.title.lowercase() },
        )
        assertEquals("three keys per song", list.size * 3, calls)
        assertEquals(
            list.sortedWith(
                compareBy<Song> { it.primaryArtist.lowercase() }
                    .thenBy { it.album?.title?.lowercase() ?: "" }
                    .thenBy { it.title.lowercase() },
            ),
            threeOnce,
        )
    }

    @Test fun shortListsAreLeftAlone() {
        val one = songs(1)
        assertSame(one, one.sortedByKey { it.title })
        assertSame(emptyList<Song>(), emptyList<Song>().sortedByKey { it.title })
    }

    @Test fun aSongKeepsItsArtistLineAndItsMainArtist() {
        val song = Song("id", "Title", listOf(ArtistRef("A"), ArtistRef("B")))
        assertSame("joined once, not on every read", song.artistText, song.artistText)
        assertEquals("A, B", song.artistText)
        assertEquals("A", song.primaryArtist)
        // A copy made with other artists has its own line.
        assertEquals("C", song.copy(artists = listOf(ArtistRef("C"))).artistText)
        assertEquals("Unknown artist", Song("x", "No one").artistText)
    }

    @Test fun aStoredArtistListReadsBackAsItWasStored() {
        val json = """[{"name":"A","id":"UC1"},{"name":"B"}]"""
        val entity = com.prism.music.data.db.SongEntity(
            id = "id", title = "Title", artistName = "A, B", artistId = "UC1",
            albumTitle = null, albumId = null, durationSec = 0, thumbnail = null, artistsJson = json,
        )
        val song = entity.toSong()
        assertEquals(listOf(ArtistRef("A", "UC1"), ArtistRef("B")), song.artists)
        assertEquals("A, B", song.artistText)
        // The next read (the list read again) gives the same artists, whatever the cache holds.
        assertEquals(song.artists, entity.toSong().artists)
    }

    @Test fun thePreparedHaystackFindsWhatTheDirectTestFinds() {
        val list = songs(120) + listOf(
            Song("p1", "AC/DC Live", listOf(ArtistRef("AC/DC"))),
            Song("p2", "Beyoncé at Home", listOf(ArtistRef("Beyoncé"))),
            Song("p3", "!!! (Chk Chk Chk)", listOf(ArtistRef("!!!")), AlbumRef("Myth Takes")),
        )
        val hay = list.sortedByKey { it.id }.associate { s ->
            s.id to libraryHaystack(listOf(s.title, s.artistText, s.album?.title.orEmpty()))
        }
        val queries = listOf("", " ", "bright", "BEYONCE", "beyonce at", "ac dc", "ac/dc", "!!!", "坂本", "a song", "no such thing", "karma police ok")
        for (q in queries) {
            val words = libraryWords(q)
            val prepared = if (q.isBlank()) list else list.filter { s ->
                if (words.isEmpty()) libraryMatches(q, s.title, s.artistText, s.album?.title.orEmpty())
                else words.all { it in hay[s.id].orEmpty() }
            }
            val direct = if (q.isBlank()) list else list.filter { s -> libraryMatches(q, s.title, s.artistText, s.album?.title.orEmpty()) }
            assertEquals("same songs for \"$q\"", direct.map { it.id }, prepared.map { it.id })
        }
    }
}
