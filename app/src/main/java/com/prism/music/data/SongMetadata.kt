package com.prism.music.data

import android.content.Context
import android.content.SharedPreferences
import com.prism.music.data.model.AlbumRef
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The listener's own artist, album and genre for a song (1.8.3): from its menu or Select many. Kept on this phone under
 * the song's id, so it follows the song everywhere it shows without changing the file or YouTube Music.
 */
@Serializable
data class SongMetadataEdit(
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
) {
    val isEmpty: Boolean get() = artist.isNullOrBlank() && album.isNullOrBlank() && genre.isNullOrBlank()

    companion object {
        /** Room for a long credit or album name (and a genre list), while keeping a typo from filling the phone. */
        const val MAX_LENGTH = 160

        /** [value] tidied for keeping: trimmed, spaces run together, at most [MAX_LENGTH]; null when blank. */
        fun clean(value: String?): String? =
            value?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_LENGTH)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Artist, album and genre edits on this phone, by song id. An edit changes what Prism shows (lists, the player, the
 * notification) and the genre its mixes and smart playlists go by; the file and YouTube Music are left as they are.
 * Kept in SharedPreferences, read in at start like [SongVersions]' picks, and shown in Compose at once.
 */
class SongMetadata(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("song_metadata", Context.MODE_PRIVATE)

    private val _edits = MutableStateFlow(read())
    val edits: StateFlow<Map<String, SongMetadataEdit>> = _edits

    /** The same edits as Compose state, so a row, header or player on screen changes the moment one is saved. */
    private val shownEdits = androidx.compose.runtime.mutableStateOf(_edits.value)

    init { shared = this }

    fun of(songId: String): SongMetadataEdit? = _edits.value[songId]

    /** The listener's genre for [songId], if they set one. */
    fun genreOf(songId: String): String? = _edits.value[songId]?.genre

    /**
     * Sets the fields given (null keeps the one a song has) on [ids]. A typo's spaces are tidied; an empty field is no
     * edit, not a blank name. [clear] takes every edit off the songs.
     */
    fun set(ids: Collection<String>, artist: String? = null, album: String? = null, genre: String? = null) {
        val a = SongMetadataEdit.clean(artist)
        val al = SongMetadataEdit.clean(album)
        val g = SongMetadataEdit.clean(genre)
        if (a == null && al == null && g == null) return
        val next = _edits.value.toMutableMap()
        ids.distinct().forEach { id ->
            val old = next[id] ?: SongMetadataEdit()
            val merged = SongMetadataEdit(
                artist = a ?: old.artist,
                album = al ?: old.album,
                genre = g ?: old.genre,
            )
            if (merged.isEmpty) next.remove(id) else next[id] = merged
        }
        save(ids, next)
    }

    /** Puts the songs back to their own artist, album and genre. */
    fun clear(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val next = _edits.value.toMutableMap()
        ids.distinct().forEach { next.remove(it) }
        save(ids, next)
    }

    /** [song] with the listener's artist and album in place of its own (a picked version's, if any, shows as it is). */
    fun shown(song: Song): Song {
        val e = shownEdits.value[song.id] ?: return song
        return song.copy(
            // The song's first artist keeps its id, so its page still opens from the edited name.
            artists = e.artist?.let { listOf(ArtistRef(it, song.artists.firstOrNull()?.id)) } ?: song.artists,
            album = e.album?.let { AlbumRef(it, song.album?.id) } ?: song.album,
        )
    }

    private fun save(ids: Collection<String>, next: Map<String, SongMetadataEdit>) {
        val ed = prefs.edit()
        ids.distinct().forEach { id ->
            val e = next[id]
            if (e == null) ed.remove(id) else ed.putString(id, json.encodeToString(SongMetadataEdit.serializer(), e))
        }
        ed.apply()
        _edits.value = next
        shownEdits.value = next
    }

    private fun read(): Map<String, SongMetadataEdit> = prefs.all.mapNotNull { (id, value) ->
        val e = (value as? String)?.let { runCatching { json.decodeFromString(SongMetadataEdit.serializer(), it) }.getOrNull() }
        e?.takeUnless { it.isEmpty }?.let { id to it }
    }.toMap()

    companion object {
        /** The phone's edits, for what draws a song without the app's container at hand (the media session, a row). */
        @Volatile var shared: SongMetadata? = null
            internal set

        /** [shown] for [song], or [song] itself before the edits have been read. */
        fun shownOf(song: Song): Song = shared?.shown(song) ?: song

        /** The listener's genre for [songId], or null when they left it as it was. */
        fun genreOf(songId: String): String? = shared?.genreOf(songId)

        private val json = Json { ignoreUnknownKeys = true }
    }
}
