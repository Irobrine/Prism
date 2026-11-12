package com.prism.music

import com.prism.music.data.innertube.YouTubeMusic
import com.prism.music.data.model.AlbumRef
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #383 (1.8.4.1): with "Songs everywhere", four songs of the reporter's two playlists still played their videos' sound
 * though "Play a different version" found their songs. Prism's search match wanted the video's title exactly, brackets
 * and all, and the same artist: a film song's soundtrack tag ('(From "The Paradise")'), its dub's language ("(Telugu)")
 * and a soundtrack credited to "Various Artist" made all four miss. The rows and search results are YouTube Music's
 * (signed out, 2026-11-11: the playlist rows from PLtLEpc9CWsdfxbsiuKYw02kpbCF1mnNcS / PLEwlFF5xcXHzubwrqTqBzexAGjhl_1LoA,
 * the songs from the search Prism makes for each).
 */
class SongsEverywhere383MatchTest {
    private fun v(id: String, title: String, sec: Int, vararg artists: String) =
        Song(id, title, artists.map { ArtistRef(it) }, durationSec = sec, isVideo = true)
    private fun s(id: String, title: String, sec: Int, album: String, vararg artists: String) =
        Song(id, title, artists.map { ArtistRef(it) }, AlbumRef(album), durationSec = sec)

    @Test fun yeshanagulaPlaysAsTheSongFromTheParadise() {
        val video = v("JqFzhcWo3EU", "Yeshanagula", 192, "Jangi Reddy", "Anirudh Ravichander", "Kasarla Shyam")
        val results = listOf(
            s("IcLdJ93eP18", "Yeshanagula (From \"The Paradise\") (Telugu)", 189, "Yeshanagula (From \"The Paradise\") (Telugu)", "Anirudh Ravichander", "Jangi Reddy", "Kasarla Shyam"),
            s("AFrAY49Ny_o", "Yesha Nagula Kattameda", 249, "Yesha Nagula Kattameda", "Veeresha Lingam"),
            s("lbEtG0ZjyJA", "Aaya Sher (From \"The Paradise\") (Telugu)", 287, "Aaya Sher (From \"The Paradise\") (Telugu)", "Anirudh Ravichander", "Jangi Reddy", "Arjun Chandy", "Kasarla Shyam"),
            s("iUT05tdJvDg", "Yeshnagula Kattameda Yesena Uyyalla", 146, "Yeshnagula Kattameda Yesena Uyyalla", "Jangi Reddy Sunitha"),
        )
        assertEquals("IcLdJ93eP18", YouTubeMusic.pickSongForVideo(video, results)?.id)
    }

    @Test fun dheemaPlaysAsItsTeluguSong() {
        val video = v("2DpqaSDuOxo", "Dheema", 229, "Anirudh Ravichander", "Sri Sai Kiran")
        val results = listOf(
            s("2ZedAROncPM", "Dheema (From \"Love Insurance Kompany\")", 236, "Dheema (From \"Love Insurance Kompany\")", "Anirudh Ravichander"),
            s("J-PLqiF8fX8", "Kalalo Kooda", 247, "Liger (Telugu) (Original Motion Picture Soundtrack)", "Tanishk Bagchi", "Sid Sriram"),
            s("5Lco4Y5fbY4", "Dheema (From \"Love Insurance Kompany (Telugu)\")", 236, "Dheema (From \"Love Insurance Kompany (Telugu)\")", "Anirudh Ravichander", "Sri Sai Kiran"),
            s("7m264AP6H7c", "Dheeme Dheeme", 259, "Kandahar", "Shamir Tandon", "Sunidhi Chauhan"),
        )
        // The Telugu playlist's video credits the Telugu lyricist: the Telugu dub, not the Tamil original of the same length.
        assertEquals("5Lco4Y5fbY4", YouTubeMusic.pickSongForVideo(video, results)?.id)
    }

    @Test fun privatePartyPlaysAsTheSarrainoduSong() {
        val video = v("_WCQkPLiNro", "Private Party (From \"Sarrainodu\")", 276, "MC Vickey")
        val results = listOf(s("hCXTPCm4O0I", "Private Party", 264, "Sarrainodu", "M.C. Vickey", "Manasi Mm"))
        assertEquals("hCXTPCm4O0I", YouTubeMusic.pickSongForVideo(video, results)?.id)
        assertEquals("Private Party MC Vickey", YouTubeMusic.songForVideoQuery(video))
    }

    @Test fun kajaluChellivaaPlaysAsTheBalupuSong() {
        val video = v("IHlrVeIIt3Y", "Kajalu Chellivaa", 245, "Junglee Music Telugu")
        val results = listOf(
            s("GzmufcvPN1g", "Kajalu Chellivaa", 235, "Balupu", "Various Artist"),
            s("LQc-wcqxfWA", "Kaatuka Kallu", 239, "Sarocharu", "Kushi Murali", "Swetha Mohan"),
            s("Kb60voO3iEc", "Yaevaindho", 244, "Balupu", "Various Artist"),
        )
        assertEquals("GzmufcvPN1g", YouTubeMusic.pickSongForVideo(video, results)?.id)
    }

    // What still keeps recordings apart.

    @Test fun anotherDubIsNotTheSong() {
        val video = v("x", "Dheema (Tamil)", 236, "Anirudh Ravichander")
        val results = listOf(s("t", "Dheema (From \"Love Insurance Kompany (Telugu)\")", 236, "a", "Anirudh Ravichander"))
        assertNull(YouTubeMusic.pickSongForVideo(video, results))
    }

    @Test fun otherBracketsStillTellRecordingsApart() {
        // #113: a live album's track isn't the studio song.
        val video = v("x", "Nos siguen pegando abajo", 250, "Charly García")
        val results = listOf(s("y", "Nos siguen pegando abajo (Pecado mortal)", 250, "a", "Charly García"))
        assertNull(YouTubeMusic.pickSongForVideo(video, results))
    }

    @Test fun variousArtistsOnlyWhenItsTheOneSongOfThatTitle() {
        val video = v("x", "Yesterday", 125, "Some Label")
        val results = listOf(
            s("a", "Yesterday", 123, "Hits 1", "Various Artists"),
            s("b", "Yesterday", 126, "Hits 2", "Various Artists"),
        )
        assertNull(YouTubeMusic.pickSongForVideo(video, results))
        assertNull("nor of another length", YouTubeMusic.pickSongForVideo(video, listOf(s("a", "Yesterday", 300, "Hits", "Various Artists"))))
    }

    @Test fun anotherArtistsSongStillDoesNotCount() {
        val video = v("x", "Kajalu Chellivaa", 245, "Junglee Music Telugu")
        assertNull(YouTubeMusic.pickSongForVideo(video, listOf(s("a", "Kajalu Chellivaa", 240, "b", "Someone Else"))))
    }
}
