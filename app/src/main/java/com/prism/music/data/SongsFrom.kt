package com.prism.music.data

import com.prism.music.data.innertube.YouTubeMusic
import com.prism.music.data.model.CollectionPage
import com.prism.music.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Library → Songs' "From" (1.8.3, G142, #382): liked songs, as always, and the songs of playlists the listener picks,
 * together and once each. Only a view: nothing is liked and nothing is written to the account.
 */
data class SongsFrom(val liked: Boolean = true, val playlists: List<String> = emptyList()) {
    /** Liked songs only: Songs as it was before 1.8.3. */
    val isDefault get() = liked && playlists.isEmpty()

    fun encode(): String = (if (liked) LIKED else "") + "|" + playlists.joinToString(",")

    companion object {
        private const val LIKED = "liked"
        const val KEY = "library:songs:from"

        fun decode(s: String?): SongsFrom {
            if (s.isNullOrBlank() || '|' !in s) return SongsFrom()
            val (a, b) = s.split('|', limit = 2)
            val lists = b.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            // Nothing chosen at all would be an empty tab with no way to tell why: liked songs then.
            return if (a != LIKED && lists.isEmpty()) SongsFrom() else SongsFrom(a == LIKED, lists)
        }

        /**
         * Liked songs first (newest first, as today), then each playlist's songs in the order they were picked and
         * in the playlist's own order; a song in several shows once, where it first comes.
         */
        fun merge(liked: List<Song>, lists: List<List<Song>>, from: SongsFrom): List<Song> {
            val seen = HashSet<String>()
            val out = ArrayList<Song>()
            if (from.liked) liked.forEach { if (seen.add(it.id)) out += it }
            lists.forEach { l -> l.forEach { if (seen.add(it.id)) out += it } }
            return out
        }
    }
}

/**
 * A playlist's songs, whether they're all of them, and how many the playlist said it had ([count], so the next read can
 * see what changed). A read that stopped at a page that didn't come is shown as it is, but isn't kept.
 */
data class PlaylistRead(val songs: List<Song>, val complete: Boolean, val count: Int? = null)

/**
 * A playlist's songs, all of them (up to [MAX]), for views that put several playlists together. Kept for the session
 * ([cache] keeps a whole read between starts too, refreshed with what was added since): [refresh] forgets the session's
 * copy when it's picked again, so the kept one is refreshed rather than read whole.
 */
class PlaylistSongs(
    private val load: suspend (id: String, cached: KeptPlaylist?) -> PlaylistRead,
    private val cache: PlaylistSongsCache? = null,
) {
    private val kept = ConcurrentHashMap<String, List<Song>>()

    /**
     * The playlist's songs: from the session's kept copy, or read (a whole playlist the first time, then only the songs
     * added since [cache]'s last read). Only a whole read is kept, in memory and on the phone: one that stopped short
     * shows what it has and is read again next time, so neither a lost page nor a read the screen's leaving cancelled
     * leaves it short for ever.
     */
    suspend fun songs(id: String): List<Song>? {
        kept[id]?.let { return it }
        // Prism's own lists are read from the phone as they are now: never kept as if they were YouTube's.
        val disk = cache?.takeUnless { PrismLibrary.isPrismList(id) }
        val cached = disk?.read(id)
        return try {
            load(id, cached).let { read ->
                if (read.complete) {
                    kept[id] = read.songs
                    disk?.write(id, read)
                }
                read.songs
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline: a whole copy from an earlier start still beats nothing.
            cached?.songs
        }
    }

    /**
     * The playlist's songs as the phone already has them (the session's copy, or the one kept between starts), without
     * asking YouTube. Null when none is kept.
     */
    suspend fun cached(id: String): List<Song>? {
        kept[id]?.let { return it }
        val disk = cache?.takeUnless { PrismLibrary.isPrismList(id) } ?: return null
        return disk.read(id)?.songs?.takeIf { it.isNotEmpty() }
    }

    /** Whether [id]'s songs are kept whole (a read that stopped short isn't). */
    fun isComplete(id: String): Boolean = kept.containsKey(id)

    fun refresh(id: String) {
        kept.remove(id)
    }

    /** Signing out or switching accounts: no kept playlist of the old one stays. */
    fun clear() {
        kept.clear()
        cache?.clearAll()
    }

    companion object {
        /** The most songs read of one playlist (as a queue takes). */
        const val MAX = 2000
    }
}

/**
 * A whole playlist for Library → Songs' "From": [first] and then each page after it, up to [PlaylistSongs.MAX] songs.
 * A page that doesn't come is asked again ([PAGE_TRIES] times, [retryMs] more apart each time). Cancelling the read
 * (the screen was left) is passed on, never turned into a short list: the caller keeps nothing then, and reads it again.
 */
suspend fun readPlaylist(
    first: CollectionPage,
    page: suspend (String) -> YouTubeMusic.PlaylistChunk,
    retryMs: Long = PAGE_RETRY_MS,
): PlaylistRead {
    val songs = first.songs.toMutableList()
    var token = first.continuation
    while (token != null && songs.size < PlaylistSongs.MAX) {
        val chunk = pageWithRetries(page, token, retryMs)
            ?: return PlaylistRead(songs.distinctBy { it.id }, complete = false, count = first.trackCount)
        if (chunk.songs.isEmpty()) break
        songs += chunk.songs
        token = chunk.next
    }
    return PlaylistRead(songs.distinctBy { it.id }, complete = true, count = first.trackCount)
}

/**
 * A playlist page as YouTube lists it now ([first]) and what the phone read last time, [cached]. Only the songs added
 * since are read: its "Newest first" page, until a song that's already kept comes; the rest comes from the phone. A big
 * playlist isn't read page after page again. [readAll] reads the whole playlist when the page can't tell what changed:
 * songs removed or reordered, a "Newest first" page that doesn't come, or new songs not at either end.
 */
suspend fun refreshPlaylist(
    first: CollectionPage,
    cached: KeptPlaylist?,
    newestPage: suspend (String) -> YouTubeMusic.PlaylistChunk,
    readAll: suspend () -> PlaylistRead,
): PlaylistRead {
    if (cached == null || cached.songs.isEmpty()) return readAll()
    val count = first.trackCount
    // As many songs as last time: nothing new to read. Fewer means songs went: read them all.
    if (count != null && cached.count != null) {
        if (count == cached.count) return PlaylistRead(cached.songs, complete = true, count = count)
        if (count < cached.count) return readAll()
    }
    val ids = cached.songs.mapTo(HashSet()) { it.id }
    val start = first.newestContinuation ?: return readAll()
    val newest = ArrayList<Song>()
    var token: String? = start
    var anchor: Song? = null
    while (token != null) {
        val chunk = try {
            newestPage(token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return readAll()
        }
        for (song in chunk.songs) {
            if (song.id in ids) { anchor = song; break }
            newest += song
        }
        if (anchor != null || chunk.next == null || newest.size > cached.songs.size) break
        token = chunk.next
    }
    if (anchor == null) return readAll()
    val atTop = cached.songs.first().id == anchor.id
    val atBottom = cached.songs.last().id == anchor.id
    if (!atTop && !atBottom) return readAll()
    val merged = if (atTop) {
        // Added to the top: YouTube's newest first is this playlist's own order for them.
        (newest + cached.songs).distinctBy { it.id }
    } else {
        // Added to the bottom: the playlist's own order has the oldest of them first.
        (cached.songs + newest.reversed()).distinctBy { it.id }
    }
    // A count that doesn't match means songs went too, or the order isn't the one new songs join: read them all.
    if (count != null && merged.size != count) return readAll()
    return PlaylistRead(merged, complete = true, count = count ?: merged.size)
}

private suspend fun pageWithRetries(
    page: suspend (String) -> YouTubeMusic.PlaylistChunk,
    token: String,
    retryMs: Long,
): YouTubeMusic.PlaylistChunk? {
    for (attempt in 0 until PAGE_TRIES) {
        try {
            return page(token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (attempt == PAGE_TRIES - 1) return null
            delay(retryMs * (attempt + 1))
        }
    }
    return null
}

private const val PAGE_TRIES = 3
private const val PAGE_RETRY_MS = 500L
