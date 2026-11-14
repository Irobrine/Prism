package com.prism.music

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.prism.music.data.db.PlayEventEntity
import com.prism.music.data.db.PrismDatabase
import com.prism.music.data.db.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * The library's lists read through their indices (1.8.5, schema 12): liked songs and their ids, recently played songs,
 * a song's play count, an album's or an artist's songs. The plan is checked, not the clock, so this says the same thing
 * on any machine; the table is filled so the planner has something to weigh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DatabaseIndexTest {
    private fun plan(db: SupportSQLiteDatabase, sql: String, vararg args: Any): String {
        val out = StringBuilder()
        db.query("EXPLAIN QUERY PLAN $sql", args).use { c ->
            while (c.moveToNext()) out.append(c.getString(c.getColumnIndexOrThrow("detail"))).append('\n')
        }
        return out.toString()
    }

    @Test fun libraryQueriesUseTheirIndices() {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PrismDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val songs = (0 until 5_000).map { i ->
                SongEntity(
                    id = "id$i",
                    title = "Song $i",
                    artistName = "Artist ${i % 400}",
                    artistId = "UC${i % 400}",
                    albumTitle = "Album ${i % 900}",
                    albumId = "MP${i % 900}",
                    durationSec = 180,
                    thumbnail = null,
                    liked = i % 100 == 0,
                    likedAt = if (i % 100 == 0) i.toLong() else 0,
                    lastPlayed = if (i % 10 == 0) i.toLong() else 0,
                    smartDownload = i % 250 == 0,
                )
            }
            runBlocking { db.songs().upsertAll(songs) }
            runBlocking {
                repeat(2_000) { i ->
                    db.plays().insert(PlayEventEntity(songId = "id${i % 500}", timestamp = 1_000L + i, playedMs = 60_000))
                }
            }
            // What SQLite weighs a plan by; without it the numbers below would only be a guess.
            db.query("ANALYZE", null).close()

            val liked = plan(db.openHelper.writableDatabase, "SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
            assertTrue("liked songs: $liked", liked.contains("index_songs_liked_likedAt"))

            val recent = plan(db.openHelper.writableDatabase, "SELECT * FROM songs WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT 30")
            assertTrue("recent: $recent", recent.contains("index_songs_lastPlayed"))

            val album = plan(db.openHelper.writableDatabase, "SELECT * FROM songs WHERE (liked = 1 OR lastPlayed > 0) AND albumId = 'MP7' ORDER BY liked DESC, lastPlayed DESC")
            assertTrue("an album's songs: $album", album.contains("index_songs"))

            val plays = plan(db.openHelper.writableDatabase, "SELECT COUNT(*) FROM play_events WHERE songId = ? AND timestamp <= ?", "id1", 5_000L)
            assertTrue("a song's plays: $plays", plays.contains("index_play_events_songId_timestamp"))

            // And the answers are still the right ones.
            assertEquals(50, runBlocking { db.songs().liked() }.size)
            assertEquals(listOf("id4990", "id4980"), runBlocking { db.songs().recentFlow(2).first() }.map { it.id }.take(2))
            assertEquals(4, runBlocking { db.plays().playsOf("id1", Long.MAX_VALUE) })
        } finally {
            db.close()
        }
    }
}
