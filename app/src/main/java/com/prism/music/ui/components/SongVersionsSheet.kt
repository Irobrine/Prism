package com.prism.music.ui.components

import com.prism.music.R
import com.prism.music.tr
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.music.data.ExplicitFilter
import com.prism.music.data.SongVersions
import com.prism.music.data.innertube.SearchFilter
import com.prism.music.data.innertube.SearchRank
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import com.prism.music.data.model.formatDuration
import com.prism.music.ui.screens.phoneMatchesForVersions
import com.prism.music.ui.theme.PrismIcons
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Play a different version" (1.7.9, Reddit: a song from an album played as someone's guitar cover): the other
 * recordings YouTube's search finds for the song's title, with their album and length, as Search shows them: Top
 * results, only songs, or only videos. The search box runs the listener's own query when the title's own search misses
 * the recording (a live take, a different title), and finds the phone's own music files with the same words. The list
 * opens at 440 dp and grows as its grabber is pulled up; "Choose a file on this phone" starts its search with the same
 * words as "Search with another title". The one picked plays in the song's place from then on and is kept on the phone
 * ([com.prism.music.AppContainer.pickVersion]); the song's own row goes back to it.
 */
@Composable
fun SongVersionsSheet(song: Song, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val picks by c.versions.picks.collectAsState()
    val picked = picks[song.id]?.id
    val localTracks by c.localMusic.tracks.collectAsState()
    val focus = LocalFocusManager.current
    // The search for versions of the title: what the box says, and the manual search it last ran (null: the title's own).
    val defaultQuery = "${song.title} ${song.primaryArtist}".trim()
    var query by remember(song.id) { mutableStateOf(defaultQuery) }
    var asked by remember(song.id) { mutableStateOf<String?>(null) }
    var searchId by remember(song.id) { mutableIntStateOf(0) }
    // Top results at first; Songs or Videos filter the same search, as Search's chips do.
    var filter by remember(song.id) { mutableStateOf(SearchFilter.TOP) }
    // What the results are for: the title's own search, or the box's last manual search.
    val effective = asked ?: defaultQuery
    // null while looking; a failure when the search didn't answer.
    val results by produceState<Result<List<Song>>?>(null, song.id, effective, filter, searchId) {
        // Looking again, so the old filter's rows don't sit under a new chip while it waits.
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { searchVersions(c, effective, filter).filter { it.id != song.id } }
                // A search the next filter or query cancelled isn't a failure to show.
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }
    // The same words find the phone's own files, so a version kept only on the phone can be picked too.
    val localHits by produceState<List<Song>>(emptyList(), effective, localTracks) {
        value = withContext(Dispatchers.Default) { phoneMatchesForVersions(effective, localTracks.map { it.song }).take(12) }
    }
    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        asked = q
        searchId++
    }
    fun choose(pick: SongVersions.Pick?) {
        c.pickVersion(song, pick)
        android.widget.Toast.makeText(
            context, if (pick == null) tr(R.string.plays_as_itself_again) else tr(R.string.plays_as_this_version_from_now), android.widget.Toast.LENGTH_SHORT,
        ).show()
        onDismiss()
    }
    // Files on this phone (1.8.3, #243): those with the song's title, and any other the listener picks.
    val files = localTracks
    val canSeeFiles = remember { c.localMusic.hasPermission() }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (canSeeFiles && !c.localMusic.scanned.value) c.localMusic.scan() }
    val fileMatches = remember(files, song.id) {
        val want = SongVersions.bare(song.title)
        if (want.isEmpty() || song.isLocal) emptyList() else files.filter { SongVersions.bare(it.song.title) == want }.take(6)
    }
    var choosingFile by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (choosingFile) {
        PhoneFilePicker(files.map { it.song }, query) { f -> choosingFile = false; f?.let { choose(SongVersions.Pick(it.id, fileLabel(it), it)) } }
        return
    }
    ExpandableSheet(onDismiss, "versionSheetHandle") { cap, _ ->
        Column(
            Modifier.fillMaxWidth().then(cap).verticalScroll(rememberScrollState()).padding(bottom = 24.dp).navigationBarsPadding().testTag("versionSheet"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 8.dp)) {
                Text(tr(R.string.play_a_different_version), style = MaterialTheme.typography.titleLarge)
                Text(tr(R.string.the_one_you_pick_plays_for), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VersionRow(song, tr(R.string.version_original), picked == null, Modifier.testTag("version:own")) { choose(null) }
            // A file of the song on the phone plays from the file (a FLAC of an album bought elsewhere, say).
            if (!song.isLocal) {
                fileMatches.forEach { t ->
                    VersionRow(t.song, tr(R.string.your_file), picked == t.song.id, Modifier.testTag("version:${t.song.id}")) {
                        choose(SongVersions.Pick(t.song.id, fileLabel(t.song), t.song))
                    }
                }
                // A picked file that doesn't match the title still shows, ticked.
                picks[song.id]?.song?.takeIf { it.isLocal && fileMatches.none { t -> t.song.id == it.id } }?.let { f ->
                    VersionRow(f, tr(R.string.your_file), true, Modifier.testTag("version:${f.id}")) {}
                }
                if (canSeeFiles && files.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().clickable { choosingFile = true }.padding(horizontal = 22.dp, vertical = 12.dp).testTag("version:chooseFile"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PrismIcons.Folder, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(tr(R.string.choose_a_file_on_this_phone), style = MaterialTheme.typography.bodyLarge)
                }
            }
            PrismTextField(
                query, { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp).testTag("version:search"),
                label = { Text(tr(R.string.search_with_another_title)) },
                leadingIcon = { Icon(PrismIcons.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; asked = null; focus.clearFocus() }) {
                        Icon(PrismIcons.Close, tr(R.string.clear_search))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }, onDone = { search() }),
            )
            val manual = asked
            if (manual != null) {
                SheetLabel(tr(R.string.search_2, manual))
                val hits = localHits.filter { it.id != song.id }
                if (hits.isNotEmpty()) {
                    SheetLabel(tr(R.string.on_this_phone))
                    VersionRows(song, hits, picked, local = true) { v ->
                        choose(SongVersions.Pick(v.id, versionLabel(v), v))
                    }
                }
                SheetLabel(tr(R.string.youtube))
            }
            // The same kinds Search offers, as chips: everything, only songs, or only videos.
            ChipRow(
                listOf(SearchFilter.TOP, SearchFilter.SONGS, SearchFilter.VIDEOS),
                { it == filter }, { it.label },
                Modifier.fillMaxWidth().testTag("version:filters"),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
            ) { filter = it }
            val r = results
            when {
                r == null -> PrismSpinner(Modifier.padding(22.dp).size(22.dp), stroke = 2.dp)
                r.isFailure -> Quiet(if (manual == null) tr(R.string.couldn_t_look_for_other_versions) else tr(R.string.search_failed))
                r.getOrThrow().isEmpty() -> Quiet(
                    when {
                        manual != null -> tr(R.string.no_results)
                        filter == SearchFilter.TOP -> tr(R.string.youtube_has_no_other_version_of)
                        else -> tr(R.string.no_results)
                    },
                )
                // The version itself is kept too: the song shows as it from now on (#328).
                else -> VersionRows(song, r.getOrThrow(), picked) { v ->
                    choose(SongVersions.Pick(v.id, versionLabel(v), v))
                }
            }
        }
    }
}

/**
 * A sheet whose content opens at 440 dp and grows as its grabber is pulled up, like Add to playlist's: pulling it back
 * down to where it started returns it to 440 dp, and that pull stays with the content instead of closing the sheet.
 * [content] gets the height cap and the sheet's close.
 */
@Composable
private fun ExpandableSheet(
    onDismiss: () -> Unit,
    handleTag: String,
    content: @Composable ColumnScope.(cap: Modifier, close: (() -> Unit) -> Unit) -> Unit,
) {
    val baseCap = 440.dp
    val screenCap = maxOf(baseCap, (LocalConfiguration.current.screenHeightDp * 0.72f).dp)
    val density = LocalDensity.current
    val maxExtra = with(density) { (screenCap - baseCap).toPx() }.coerceAtLeast(0f)
    val closeAt = with(density) { 96.dp.toPx() }
    var extraPx by remember(maxExtra) { mutableStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val dragScope = rememberCoroutineScope()
    // Where the grabber is on screen, so a slide is measured from the finger's own place even as the bar moves with it.
    val grabberTop = remember { mutableStateOf(0f) }
    val listCap = baseCap + with(density) { extraPx.coerceIn(0f, maxExtra).toDp() }
    PrismSheet(
        onDismiss = onDismiss,
        dragHandle = { close ->
            Box(
                Modifier.fillMaxWidth().height(44.dp)
                    .onGloballyPositioned { grabberTop.value = it.positionInRoot().y }
                    .pointerInput(maxExtra, closeAt) {
                        if (maxExtra > 0f) awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // A tap on the bar stays with the bar: it mustn't reach the sheet and close it.
                            down.consume()
                            // A new slide takes over from where the last one was settling.
                            settle?.cancel()
                            settle = null
                            val startFinger = grabberTop.value + down.position.y
                            val startExtra = extraPx
                            var lastY = down.position.y
                            var moved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val travel = lastY - change.position.y
                                lastY = change.position.y
                                if (travel != 0f) moved = true
                                if (moved) {
                                    // Measured from the bar's place on screen, since the bar moves with the finger.
                                    val open = startExtra + (startFinger - (grabberTop.value + change.position.y))
                                    extraPx = open.coerceIn(-closeAt, maxExtra)
                                }
                                change.consume()
                            }
                            if (moved) {
                                when {
                                    extraPx <= -closeAt / 2f -> close()
                                    extraPx < maxExtra / 2f -> settle = dragScope.launch {
                                        animate(extraPx, 0f, animationSpec = tween(180)) { value, _ -> extraPx = value }
                                    }
                                    else -> settle = dragScope.launch {
                                        animate(extraPx, maxExtra, animationSpec = tween(180)) { value, _ -> extraPx = value }
                                    }
                                }
                            }
                        }
                    }
                    .testTag(handleTag),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
            }
        },
    ) { close ->
        content(Modifier.heightIn(max = listCap).nestedScroll(KeepUpwardScroll), close)
    }
}

/**
 * The same search Search shows for [filter]: Top results (the best match, songs and videos, leaned toward what the
 * listener plays), or only its songs or videos. Hidden explicit songs stay out, as in Search, and a live stream or
 * podcast episode isn't offered as a version.
 */
private suspend fun searchVersions(c: com.prism.music.AppContainer, query: String, filter: SearchFilter): List<Song> {
    val explicit = c.settings.current.behavior.explicitContent
    return when (filter) {
        SearchFilter.SONGS, SearchFilter.VIDEOS -> SongVersions.offered(
            filter,
            c.ytm.search(query, filter).items
                .let { ExplicitFilter.items(explicit, it) }
                .filterIsInstance<SongItem>()
                .map { it.song },
        )
        // Top results: the same mixed search Search shows, its best match and its songs and videos.
        else -> {
            val taste = runCatching { c.taste.searchTaste() }.getOrDefault(SearchRank.Taste.NONE)
            val top = ExplicitFilter.top(explicit, c.ytm.searchTop(query, taste))
            SongVersions.offered(SearchFilter.TOP, SongVersions.recordings(top))
        }
    }
}

/** What a version's row (and the song menu, once picked) says of it: artist, album or "Video", length. */
fun versionLabel(v: Song): String = listOfNotNull(
    v.artistText.takeIf { it.isNotBlank() },
    if (v.isVideo) tr(R.string.video) else v.album?.title?.takeIf { it.isNotBlank() },
    formatDuration(v.durationSec).takeIf { it.isNotEmpty() },
).joinToString(" · ")

/** A file's row: "Your file · Artist · 3:41" (in the song menu once picked). */
private fun fileLabel(f: Song): String = listOf(tr(R.string.your_file), versionLabel(f)).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Any music file on the phone, found by typing (1.8.3, #243); null: none picked. Its search starts with [prefill], the
 * words the "Search with another title" box has, so it opens on the song's own files instead of every file.
 */
@Composable
internal fun PhoneFilePicker(files: List<Song>, prefill: String, onPick: (Song?) -> Unit) {
    var query by androidx.compose.runtime.saveable.rememberSaveable(prefill) { mutableStateOf(prefill) }
    val shown = remember(files, query) {
        if (query.isBlank()) files.take(300)
        else {
            val words = com.prism.music.data.LibrarySearch.norm(query).split(' ').filter { it.isNotBlank() }
            files.filter { f -> com.prism.music.data.LibrarySearch.norm("${f.title} ${f.artistText} ${f.album?.title.orEmpty()}").let { hay -> words.all { it in hay } } }.take(300)
        }
    }
    ExpandableSheet({ onPick(null) }, "filePickerHandle") { cap, _ ->
        Column(Modifier.fillMaxWidth().then(cap).padding(bottom = 16.dp).navigationBarsPadding().testTag("filePicker")) {
            Text(tr(R.string.choose_a_file_on_this_phone), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp))
            SearchField(query, tr(R.string.search), { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("file:search"))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                items(shown.size, key = { shown[it].id }) { i ->
                    val f = shown[i]
                    VersionRow(f, null, false, Modifier.testTag("file:${f.id}")) { onPick(f) }
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) = Text(
    text, Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun Quiet(text: String) = Text(
    text, Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** [versions] as rows; each picks its recording as the song's from now on. [local]: they're files on the phone. */
@Composable
private fun VersionRows(song: Song, versions: List<Song>, picked: String?, local: Boolean = false, onPick: (Song) -> Unit) {
    versions.forEach { v ->
        val tag = if (local) null else if (SongVersions.sameRecording(song, v)) tr(R.string.another_upload) else null
        VersionRow(v, tag, picked == v.id, Modifier.testTag("version:${v.id}")) { onPick(v) }
    }
}

@Composable
private fun VersionRow(v: Song, tag: String?, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(v.thumbnail, Modifier.size(48.dp), shape = LocalUi.current.shape(6.dp), size = 120)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(tag, versionLabel(v).takeIf { it.isNotBlank() }).distinct().joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (current) Icon(PrismIcons.Check, tr(R.string.playing_this_one), Modifier.padding(start = 8.dp).size(20.dp), tint = scheme.onSurface)
    }
}
