package com.prism.music.ui.components

import com.prism.music.R
import com.prism.music.tr
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prism.music.data.TagStore
import com.prism.music.data.model.Song
import com.prism.music.data.prefs.SongSwipeAction
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import com.prism.music.ui.theme.rememberHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which way a row was slid: to the right (its left edge uncovers) or the left. */
private enum class SwipeSide { RIGHT, LEFT }

/** What a swipe uncovers: the action's icon, name and color. */
private data class SwipeVisual(val icon: ImageVector, val label: String, val color: Color)

// Each action has its own color, and the ones that take something away are redder.
private val SwipeNext = Color(0xFF3F51B5)
private val SwipeQueue = Color(0xFF00695C)
private val SwipeLike = Color(0xFFC2185B)
private val SwipeDownload = Color(0xFF1565C0)
private val SwipeRadio = Color(0xFFE65100)
private val SwipeTag = Color(0xFF6A1B9A)
private val SwipeBestPart = Color(0xFFAD6800)
private val SwipeArtist = Color(0xFF00838F)
private val SwipeUnlike = Color(0xFFC62828)
private val SwipeRemoveDownload = Color(0xFFB71C1C)
private val SwipeNotForMe = Color(0xFFD32F2F)
private val SwipeRemoveNotForMe = Color(0xFF8E0000)

/**
 * A song's row that can be swiped sideways: each way does what Settings → Controls & gestures → Gestures has for songs
 * (by default, right plays next and left goes on the end of the queue). Letting go short of the mark does nothing.
 */
@Composable
fun SongSwipe(
    song: Song,
    liked: Boolean,
    downloaded: Boolean,
    disliked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // While songs are being picked (Select many), a sideways drag shouldn't act on one of them.
    if (!enabled || LocalSongSelection.current?.selecting == true) { Box(modifier) { content() }; return }
    val c = LocalContainer.current
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val settings = LocalAppSettings.current
    // #406 (1.8.4): "Swipe song rows to queue" off leaves the row as it is, wherever rows swipe.
    if (!settings.behavior.rowSwipes) { Box(modifier) { content() }; return }
    // In someone's Listen Together session, songs go into their queue. Read as it is, not collected: a row shouldn't
    // hold a collector of its own (the app keeps one for the rows' marks, not one each).
    val guest = (c.together.state.value as? com.prism.music.playback.together.TogetherState.Guest)?.takeIf { !song.isLocal && !song.isRss }
    var tagging by remember(song.id) { mutableStateOf(false) }
    fun toast(text: String) = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()

    fun run(action: SongSwipeAction) {
        when (action) {
            SongSwipeAction.PLAY_NEXT -> if (guest != null) {
                c.together.suggest(song, next = true)
                toast(tr(R.string.sent_to, guest.hostName))
            } else {
                c.player.playNext(song)
                toast(tr(R.string.playing_next))
            }
            SongSwipeAction.ADD_TO_QUEUE -> if (guest != null) {
                c.together.suggest(song, next = false)
                toast(tr(R.string.sent_to, guest.hostName))
            } else {
                c.player.addToQueue(song)
                toast(tr(R.string.added_to_queue))
            }
            SongSwipeAction.LIKE -> c.library.toggleLike(song)
            // A downloaded song (or one still coming) is taken back; anything else is saved for offline listening.
            SongSwipeAction.DOWNLOAD -> if (downloaded) c.downloads.remove(song.id) else c.downloads.download(song)
            SongSwipeAction.RADIO -> c.player.playRadio(song)
            SongSwipeAction.ADD_TAG -> tagging = true
            SongSwipeAction.BEST_PART -> c.player.playFromBestPart(song)
            SongSwipeAction.GO_TO_ARTIST -> openableArtists(song).firstOrNull()?.let { nav.go(Routes.artist(it)) }
            SongSwipeAction.NOT_FOR_ME -> if (disliked) c.library.setDisliked(song, false) else {
                c.library.setDisliked(song, true)
                toast(tr(R.string.got_it_prism_will_steer_clear))
            }
            SongSwipeAction.NONE -> Unit
        }
    }

    if (tagging) TagPicker(listOf(song), song.title) { tagging = false }
    SwipeFrame(
        right = songSwipeVisual(settings.behavior.songSwipeRight, song, liked, downloaded, disliked, guest != null),
        left = songSwipeVisual(settings.behavior.songSwipeLeft, song, liked, downloaded, disliked, guest != null),
        modifier = modifier,
        onAction = { side -> run(if (side == SwipeSide.RIGHT) settings.behavior.songSwipeRight else settings.behavior.songSwipeLeft) },
    ) { content() }
}

/** Lets a list that wraps its song rows itself (as the lists did when swiping only queued) keep the one swipe. */
internal val LocalSongSwipeWrapped = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Let a page whose song rows scroll sideways (Home) keep its rows' drags for that. */
internal val LocalSongSwipeAllowed = androidx.compose.runtime.staticCompositionLocalOf { true }

/**
 * A song's row swiped sideways, the way the lists that had this before call it: the same gestures as [SongSwipe], with
 * the row inside left to it.
 */
@Composable
fun SwipeToQueue(song: Song, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalContainer.current
    val marks = LocalRowMarks.current
    val liked = if (marks != null) song.id in marks.liked.value else song.id in c.library.likedIds.value
    val downloaded = com.prism.music.download.rowDownloaded(
        song,
        marks?.downloads?.value ?: c.downloadState.value,
        marks?.standIns?.value ?: c.downloadStandIns.value,
        marks?.fromPhone ?: { s -> c.downloads.playsFromPhone(s) },
    )
    val disliked = if (marks != null) song.id in marks.disliked.value else song.id in c.library.dislikedIds.value
    androidx.compose.runtime.CompositionLocalProvider(LocalSongSwipeWrapped provides true) {
        SongSwipe(song, liked, downloaded, disliked, modifier = modifier) { content() }
    }
}

/** An album's or playlist's row swiped the same way: all its songs play next or go on the end of the queue (#248). */
@Composable
fun SwipeToQueue(item: com.prism.music.data.model.BrowseItem, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val target = item.playable() ?: return content()
    SwipeToQueue({ next -> queuePlayable(c, context, target, item.title, next) }, modifier, content)
}

/** The swipe itself: [onQueue] with true to the right (play next), false to the left (end of the queue). */
@Composable
fun SwipeToQueue(onQueue: (next: Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // #406 (1.8.4): "Swipe song rows to queue" off leaves the row as it is, wherever rows swipe (songs, and the albums
    // and playlists that do), so a sideways drag is the page's again.
    if (!LocalAppSettings.current.behavior.rowSwipes) { Box(modifier) { content() }; return }
    SwipeFrame(
        right = SwipeVisual(PrismIcons.PlaylistPlay, tr(R.string.play_next), SwipeNext),
        left = SwipeVisual(PrismIcons.QueueMusic, tr(R.string.add_to_queue), SwipeQueue),
        modifier = modifier,
        onAction = { side -> onQueue(side == SwipeSide.RIGHT) },
    ) { content() }
}

/** What a song's [action] looks and says now (hollow or filled heart, downloaded or not…), or null when it can't act on it. */
private fun songSwipeVisual(
    action: SongSwipeAction,
    song: Song,
    liked: Boolean,
    downloaded: Boolean,
    disliked: Boolean,
    guest: Boolean,
): SwipeVisual? {
    val available = when (action) {
        SongSwipeAction.NONE -> false
        SongSwipeAction.PLAY_NEXT, SongSwipeAction.ADD_TO_QUEUE -> true
        SongSwipeAction.LIKE -> !song.isRss
        SongSwipeAction.DOWNLOAD, SongSwipeAction.RADIO, SongSwipeAction.NOT_FOR_ME ->
            !song.isLocal && !song.isRss && !song.isEpisode
        SongSwipeAction.ADD_TAG -> TagStore.taggable(song)
        SongSwipeAction.BEST_PART -> !song.isEpisode && !song.isLive && !guest
        SongSwipeAction.GO_TO_ARTIST -> openableArtists(song).isNotEmpty()
    }
    if (!available) return null
    return when (action) {
        SongSwipeAction.PLAY_NEXT -> SwipeVisual(PrismIcons.PlaylistPlay, tr(R.string.play_next), SwipeNext)
        SongSwipeAction.ADD_TO_QUEUE -> SwipeVisual(PrismIcons.QueueMusic, tr(R.string.add_to_queue), SwipeQueue)
        // The hollow heart to like, the filled one to take it back.
        SongSwipeAction.LIKE -> if (liked) SwipeVisual(PrismIcons.HeartFilled, tr(R.string.unlike), SwipeUnlike)
        else SwipeVisual(PrismIcons.Heart, tr(R.string.like), SwipeLike)
        SongSwipeAction.DOWNLOAD -> if (downloaded) SwipeVisual(PrismIcons.DownloadDone, tr(R.string.remove) + " " + tr(R.string.download), SwipeRemoveDownload)
        else SwipeVisual(PrismIcons.Download, tr(R.string.download), SwipeDownload)
        SongSwipeAction.RADIO -> SwipeVisual(PrismIcons.Radio, tr(R.string.radio_2), SwipeRadio)
        SongSwipeAction.ADD_TAG -> SwipeVisual(PrismIcons.Tag, tr(R.string.add_a_tag), SwipeTag)
        SongSwipeAction.BEST_PART -> SwipeVisual(PrismIcons.FlameFilled, tr(R.string.play_from_the_best_part), SwipeBestPart)
        SongSwipeAction.GO_TO_ARTIST -> {
            val artist = openableArtists(song).firstOrNull()?.name
            SwipeVisual(PrismIcons.Person, if (artist != null) tr(R.string.go_to, artist) else tr(R.string.go_to_artist), SwipeArtist)
        }
        // The filled thumb to say not for me, the hollow one to take it back.
        SongSwipeAction.NOT_FOR_ME -> if (disliked) SwipeVisual(PrismIcons.ThumbDown, tr(R.string.remove) + " " + tr(R.string.not_for_me), SwipeRemoveNotForMe)
        else SwipeVisual(PrismIcons.ThumbDownFilled, tr(R.string.not_for_me), SwipeNotForMe)
        SongSwipeAction.NONE -> null
    }
}

/** The sliding row and the rectangle its swipe uncovers, with the action's icon over it. */
@Composable
private fun SwipeFrame(
    right: SwipeVisual?,
    left: SwipeVisual?,
    modifier: Modifier = Modifier,
    onAction: (SwipeSide) -> Unit,
    content: @Composable () -> Unit,
) {
    if (right == null && left == null) { Box(modifier) { content() }; return }
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val mark = with(density) { 84.dp.toPx() }
    val reach = with(density) { 150.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxWidth().clipToBounds()) {
        // What the swipe uncovers: a rectangle in the action's color, the icon over it. The row slides over it, so the
        // icon comes out from under the row as far as the finger goes.
        val x = offset
        val visual = if (x > 0) right else if (x < 0) left else null
        if (visual != null && x != 0f) {
            val armed = abs(x) >= mark
            val leading = x > 0
            val fill = if (armed) visual.color else visual.color.copy(alpha = 0.88f)
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .align(if (leading) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(with(density) { abs(x).toDp() })
                        .background(fill)
                        .clipToBounds(),
                ) {
                    Row(
                        Modifier
                            .align(if (leading) Alignment.CenterStart else Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = if (leading) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!leading) {
                            Text(
                                visual.label, color = Color.White, style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Clip,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(visual.icon, null, Modifier.size(24.dp), tint = Color.White)
                        if (leading) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                visual.label, color = Color.White, style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .draggable(
                    rememberDraggableState { d ->
                        val before = offset
                        // Past the mark it follows the finger more slowly.
                        val after = (before + d * if (abs(before) > mark) 0.45f else 1f).coerceIn(-reach, reach)
                        offset = when {
                            after > 0 && right == null -> 0f
                            after < 0 && left == null -> 0f
                            else -> after
                        }
                        if ((abs(before) >= mark) != (abs(offset) >= mark)) haptics.threshold()
                    },
                    Orientation.Horizontal,
                    onDragStopped = {
                        val from = offset
                        if (abs(from) >= mark) {
                            haptics.gestureEnd()
                            onAction(if (from > 0) SwipeSide.RIGHT else SwipeSide.LEFT)
                        }
                        animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
                    },
                ),
        ) { content() }
    }
}
