package com.prism.music.data

import android.util.Log
import com.prism.music.data.innertube.InnerTube
import com.prism.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** A whole playlist as the phone read it: [songs] in the playlist's own order, and how many it had ([count]). */
data class KeptPlaylist(val songs: List<Song>, val count: Int?)

/**
 * Whole playlists for Library → Songs' "From" kept between starts (1.8.4, #473): a big playlist is many pages. Only a
 * whole read is kept ([PlaylistSongs] never writes a short one); at each start it's refreshed with the songs added
 * since ([refreshPlaylist]), so the rest doesn't have to be read again.
 */
class PlaylistSongsCache(dir: File) {
    private val cacheDir = File(dir, "songs_from")

    @Serializable
    private data class Kept(val songs: List<Song>, val count: Int? = null)

    /** The kept songs for [id], or null when none is kept. */
    suspend fun read(id: String): KeptPlaylist? = withContext(Dispatchers.IO) {
        val file = fileFor(id)
        if (!file.isFile) return@withContext null
        runCatching { InnerTube.json.decodeFromString(Kept.serializer(), file.readText()) }
            .onFailure { Log.w(TAG, "couldn't read the kept playlist", it) }
            .getOrNull()?.takeIf { it.songs.isNotEmpty() }?.let { KeptPlaylist(it.songs, it.count) }
    }

    suspend fun write(id: String, read: PlaylistRead) = withContext(Dispatchers.IO) {
        if (read.songs.isEmpty()) return@withContext
        runCatching {
            cacheDir.mkdirs()
            val file = fileFor(id)
            // Written beside and moved over, so a start never reads half a file.
            val next = File(file.parentFile, file.name + ".new")
            next.writeText(InnerTube.json.encodeToString(Kept.serializer(), Kept(read.songs, read.count)))
            if (!next.renameTo(file)) { file.delete(); next.renameTo(file) }
            cacheDir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_KEPT)?.forEach { it.delete() }
        }.onFailure { Log.w(TAG, "couldn't keep the playlist", it) }
    }

    /** Signing out or switching accounts: none of the old account's playlists stays. */
    fun clearAll() {
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
    }

    private fun fileFor(id: String) = File(cacheDir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    companion object {
        /** The newest playlists kept; the rest go (each is a few hundred KB at most). */
        private const val MAX_KEPT = 50
        private const val TAG = "PrismPlaylists"
    }
}
