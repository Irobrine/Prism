package com.prism.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.SongMetadataEdit
import com.prism.music.data.SongVersions
import com.prism.music.data.model.Song
import com.prism.music.data.model.UNKNOWN_ARTIST
import com.prism.music.data.model.songCount
import com.prism.music.tr
import com.prism.music.ui.theme.LocalContainer

/**
 * Artist, album and genre for one song or every picked one (1.8.3). The fields begin with what the songs show now; a
 * field left as it is isn't touched, and one typed for several songs goes on all of them. Changes show on this phone and
 * leave the file and YouTube Music as they are. "Reset metadata" puts the songs back.
 */
@Composable
fun MetadataEditorSheet(songs: List<Song>, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val list = remember(songs) { songs.distinctBy { it.id } }
    val ids = remember(list) { list.map { it.id } }
    val edits by c.metadata.edits.collectAsState()

    // As they show now, including any edit already made.
    val shown = remember(list, edits) { list.map { SongVersions.shownOf(it) } }
    val artistValues = remember(shown) { shown.map { if (it.artistText == UNKNOWN_ARTIST) "" else it.artistText } }
    val albumValues = remember(shown) { shown.map { it.album?.title.orEmpty() } }
    val initialArtist = common(artistValues)
    val initialAlbum = common(albumValues)

    // The genre songs already go by (looked up earlier), and the listener's own edits.
    val known by produceState<Map<String, String>>(emptyMap(), list) {
        value = runCatching { c.meta.cachedGenres(list) }.getOrDefault(emptyMap())
    }
    val currentGenre = common(remember(known, list) { list.map { known[it.id].orEmpty() } })
    val overrideGenres = remember(list, edits) { list.map { edits[it.id]?.genre.orEmpty() } }
    val initialGenre = common(overrideGenres)

    val mixedArtist = remember(artistValues) { artistValues.distinct().size > 1 }
    val mixedAlbum = remember(albumValues) { albumValues.distinct().size > 1 }
    val mixedGenre = remember(overrideGenres) { overrideGenres.distinct().size > 1 }

    var artist by remember(list) { mutableStateOf(initialArtist) }
    var album by remember(list) { mutableStateOf(initialAlbum) }
    var genre by remember(list) { mutableStateOf(initialGenre) }

    val artistChanged = artist.trim() != initialArtist.trim()
    val albumChanged = album.trim() != initialAlbum.trim()
    val genreChanged = genre.trim() != initialGenre.trim()
    val changed = artistChanged || albumChanged || genreChanged

    PrismSheet(onDismiss = onDismiss) { close ->
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp).navigationBarsPadding().testTag("metadataSheet"),
        ) {
            Text(tr(R.string.edit_metadata), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(
                list.singleOrNull()?.title ?: songCount(list.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            PrismTextField(
                artist, { artist = it.take(SongMetadataEdit.MAX_LENGTH) }, Modifier.fillMaxWidth().testTag("metadataArtist"),
                label = { Text(tr(R.string.artist)) },
                placeholder = { Text(if (mixedArtist) tr(R.string.metadata_mixed) else "") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            PrismTextField(
                album, { album = it.take(SongMetadataEdit.MAX_LENGTH) }, Modifier.fillMaxWidth().testTag("metadataAlbum"),
                label = { Text(tr(R.string.album)) },
                placeholder = { Text(if (mixedAlbum) tr(R.string.metadata_mixed) else "") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            PrismTextField(
                genre, { genre = it.take(SongMetadataEdit.MAX_LENGTH) }, Modifier.fillMaxWidth().testTag("metadataGenre"),
                label = { Text(tr(R.string.genre)) },
                placeholder = { Text(if (mixedGenre) tr(R.string.metadata_mixed) else tr(R.string.genre_example)) },
                supportingText = currentGenre.takeIf { it.isNotBlank() && it != genre.trim() }
                    ?.let { { Text(tr(R.string.metadata_currently, it)) } },
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                tr(R.string.metadata_leave_blank),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ids.any { edits[it] != null }) {
                Spacer(Modifier.height(6.dp))
                PrismTextButton(
                    {
                        c.metadata.clear(ids)
                        Toast.makeText(context, tr(R.string.metadata_reset), Toast.LENGTH_SHORT).show()
                        close {}
                    },
                    modifier = Modifier.testTag("metadataReset"),
                ) { Text(tr(R.string.reset_metadata)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrismTextButton({ close {} }) { Text(tr(R.string.cancel)) }
                PrismTextButton(
                    {
                        c.metadata.set(
                            ids,
                            artist = artist.takeIf { artistChanged },
                            album = album.takeIf { albumChanged },
                            genre = genre.takeIf { genreChanged },
                        )
                        Toast.makeText(context, tr(R.string.metadata_saved), Toast.LENGTH_SHORT).show()
                        close {}
                    },
                    enabled = changed, modifier = Modifier.testTag("metadataSave"),
                ) { Text(tr(R.string.save)) }
            }
        }
    }
}

/** The value every song gives, or "" when they differ (a multi-select's "Mixed"). */
private fun common(values: List<String>): String = values.distinct().singleOrNull()?.takeIf { it.isNotBlank() }.orEmpty()
