package com.prism.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.LinkHandling
import com.prism.music.data.lastfm.LastFm
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.spotify.Spotify
import com.prism.music.data.spotify.SpotifyFeature
import com.prism.music.data.spotify.SpotifyGuard
import com.prism.music.data.spotify.SpotifyOAuth
import com.prism.music.playback.DiscordPresence
import com.prism.music.tr
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.Routes
import com.prism.music.ui.components.PrismButton
import com.prism.music.ui.components.PrismOutlinedButton
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.PrismTextField
import com.prism.music.ui.components.rememberResumeCount
import com.prism.music.ui.screens.PrismCheck
import com.prism.music.ui.screens.SettingEntry
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlinx.coroutines.launch

private val page = SettingsPageId.ACCOUNTS_SHARING

/** What "Links you share" is called at each of its two stored values (`share_youtube_links`). */
internal fun sharedLinkLabel(youtube: Boolean): String = if (youtube) tr(R.string.youtube) else tr(R.string.youtube_music_2)

/**
 * What the Discord switch's line says: not signed in; signed in but off; and, on, how the connection is going. A pure
 * function of the four things it reads, so that it's tested without Discord.
 */
internal fun discordValue(signedIn: Boolean, on: Boolean, state: DiscordPresence.State, user: String?): String = when {
    !signedIn -> tr(R.string.tv_not_signed_in)
    !on -> user?.let { tr(R.string.cn_signed_in_as, it) } ?: tr(R.string.signed_in_2)
    state == DiscordPresence.State.CONNECTED -> user?.let { tr(R.string.on_as, it) } ?: tr(R.string.on_2)
    state == DiscordPresence.State.CONNECTING -> tr(R.string.connecting_to_discord)
    else -> user?.let { tr(R.string.on_discord_as, it) } ?: tr(R.string.on_discord)
}

/** What the Spotify row says while it isn't connected. */
internal fun spotifyValue(connection: Spotify.Connection): String = when (connection) {
    is Spotify.Connection.Connected -> tr(R.string.spot_connected_as, connection.name)
    Spotify.Connection.Waiting -> tr(R.string.spot_waiting)
    Spotify.Connection.Revoked -> tr(R.string.cn_spot_access_taken_back)
    else -> tr(R.string.cn_not_connected)
}

/**
 * Settings → Accounts & sharing (1.8.4's map, plan §2.3), built with the kit. This file is the only place for the
 * page: its composable, its lines in Settings' search ([accountsSharingIndex]), its "Reset this page" keys
 * ([accountsSharingLookKeys]), its line on the root ([accountsSharingValue]) and the old group names it answers to
 * ([accountsSharingOldGroups]).
 *
 * Five groups: Accounts (the Prism Account and YouTube Music, each a link to its own screen, and YouTube Music's
 * history, a link to Bring your history), Last.fm, Spotify, Discord, and Sharing and links (from the old Devices &
 * connections). A service's first row says its state ("Connected as …", "Not connected"); its options show while it's
 * connected. Last.fm, Spotify and Discord have no title over their card while their first row names them (§2.7); the
 * old keys "lastfm", "spotify" and "discord" still open the page at their card.
 *
 * Spotify is connected through the listener's own developer app and Spotify's official API only: the steps are in the
 * "How to connect" sheet behind its row, with the Client ID's field.
 */
@Composable
fun AccountsSharingPage(bottomPadding: Dp, find: String? = null, showBack: Boolean = true, onSearch: (() -> Unit)? = null) {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val d = remember { AppSettings() }
    val reset = ListeningResets()
    val onOff: (Boolean) -> String = { ListeningResets.onOff(it) }

    // Accounts
    val profile by c.account.profile.collectAsState()
    val ytLive by c.imports.youtubeLive.on.collectAsState()

    // Last.fm (its options are kept by the Last.fm client, not in the settings)
    val lastFm = c.lastFm
    val lastFmUser by lastFm.user.collectAsState()
    val lastFmOptions by lastFm.options.collectAsState()
    val lastFmWaiting by lastFm.waiting.collectAsState()
    val lastFmDefault = remember { LastFm.Options() }
    val lastFmOn = lastFm.available && lastFmUser != null
    val scrobbleChanged = lastFmOn && reset.add(tr(R.string.last_fm_scrobbling), lastFmOptions.scrobble, lastFmDefault.scrobble, onOff) {
        lastFm.setOptions(lastFm.options.value.copy(scrobble = it))
    }
    val firstArtistChanged = lastFmOn && reset.add(tr(R.string.first_artist_only), lastFmOptions.firstArtistOnly, lastFmDefault.firstArtistOnly, onOff) {
        lastFm.setOptions(lastFm.options.value.copy(firstArtistOnly = it))
    }
    val loveChanged = lastFmOn && reset.add(tr(R.string.love_when_i_like_a_song), lastFmOptions.loveOnLike, lastFmDefault.loveOnLike, onOff) {
        lastFm.setOptions(lastFm.options.value.copy(loveOnLike = it))
    }

    // Spotify
    val spotifyConnection by c.spotify.connection.collectAsState()

    // Discord
    val discordState by c.discord.state.collectAsState()
    val discordUser by c.discord.userName.collectAsState()
    val discordChanged = reset.add(tr(R.string.cn_discord_show), s.discordPresence, d.discordPresence, onOff) { c.settings.setDiscordPresence(it) }

    // Sharing
    val linksChanged = reset.add(tr(R.string.links_you_share), s.shareYoutubeLinks, d.shareYoutubeLinks, { sharedLinkLabel(it) }) { c.settings.setShareYoutubeLinks(it) }
    // Android 12+ asks the listener whether Prism opens music.youtube.com links (an app that isn't YouTube Music can't
    // claim them), so the state is read again whenever Prism comes back from that page.
    val resumed = rememberResumeCount()
    val opensLinks = remember(resumed) { LinkHandling.opensYouTubeMusicLinks(context) }

    SettingsPageScaffold(page.title, bottomPadding, showBack = showBack, onSearch = onSearch, find = find, reset = reset.pageReset()) {
        group(tr(R.string.accounts)) {
            // Each has its own screen; the root's account card is where signing out and switching are.
            LinkRow(tr(R.string.prism_account), value = profile?.name ?: tr(R.string.cn_not_set_up), info = tr(R.string.your_name_and_picture_kept_on)) { nav.go(Routes.ACCOUNT) }
            LinkRow(
                tr(R.string.youtube_music_2),
                value = if (s.isLoggedIn) s.accountName.takeIf { it.isNotBlank() }?.let { tr(R.string.cn_signed_in_as, it) } ?: tr(R.string.signed_in_2) else tr(R.string.tv_not_signed_in),
                info = if (s.isLoggedIn) tr(R.string.cn_youtube_account_info) else tr(R.string.optional_your_library_history_and_recommendation),
            ) { nav.go(if (s.isLoggedIn) Routes.LOGIN_ADD else Routes.LOGIN) }
            // YouTube Music (live): the switch is in Bring your history, next to Takeout, which brings the past.
            LinkRow(
                tr(R.string.cn_youtube_history),
                value = when {
                    !s.isLoggedIn -> tr(R.string.ytlive_connection_signed_out)
                    ytLive -> tr(R.string.ytlive_connection_on)
                    else -> tr(R.string.ytlive_connection_off)
                },
            ) { nav.go(Routes.BRING_HISTORY) }
        }

        group(null, key = LAST_FM) {
            LastFmRows(
                lastFm.available, lastFmUser, lastFmOptions, lastFmWaiting, scrobbleChanged, firstArtistChanged, loveChanged,
                onOptions = { lastFm.setOptions(it) },
                onConnect = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(lastFm.authUrl()))) } },
                onDisconnect = { lastFm.signOut() },
            )
        }

        // Titled only while connected: its rows then are the features', and none of them says "Spotify".
        group(if (spotifyConnection is Spotify.Connection.Connected) tr(R.string.spotify) else null, key = tr(R.string.spotify), info = if (spotifyConnection is Spotify.Connection.Connected) tr(R.string.spot_about) else null) {
            when (val conn = spotifyConnection) {
                is Spotify.Connection.Connected -> {
                    SpotifyFeatureRows()
                    SpotifyDisconnectRow(conn.name)
                }
                Spotify.Connection.Revoked -> {
                    SpotifyConnectRow(conn)
                    SpotifyDisconnectRow(null)
                }
                else -> SpotifyConnectRow(conn)
            }
        }

        group(null, key = tr(R.string.discord)) {
            val signedIn = c.discord.signedIn
            SwitchRow(
                tr(R.string.cn_discord_show), s.discordPresence && signedIn,
                value = discordValue(signedIn, s.discordPresence, discordState, discordUser),
                // "About this" was a row that only explained: the small risk to the account is said here.
                info = tr(R.string.your_profile_shows_the_song_and) + "\n\n" + tr(R.string.discord_has_no_way_for_android),
                changed = discordChanged,
            ) { on ->
                // The first time, it signs in on Discord's own page; the switch is turned on from there.
                if (on && !signedIn) nav.go(Routes.DISCORD_LOGIN) else c.settings.setDiscordPresence(on)
            }
            if (signedIn) LinkRow(tr(R.string.sign_out_of_discord), info = tr(R.string.forgets_the_sign_in_on_this), action = true) {
                c.settings.setDiscordPresence(false)
                c.discord.signOut()
            }
        }

        group(tr(R.string.sharing_and_links)) {
            PickCardsRow(
                tr(R.string.links_you_share), listOf(false, true), s.shareYoutubeLinks, { sharedLinkLabel(it) },
                info = tr(R.string.albums_have_no_page_on_youtube), changed = linksChanged, exampleScale = 1f,
                onPick = { c.settings.setShareYoutubeLinks(it) },
            ) { SharedLinkExample(it) }
            LinkRow(
                tr(R.string.open_youtube_music_links_in_prism),
                value = when (opensLinks) {
                    true -> tr(R.string.on_links_you_tap_open_here)
                    false -> tr(R.string.off_turn_on_open_supported_links)
                    null -> tr(R.string.choose_prism_when_a_link_asks)
                },
                info = tr(R.string.android_only_lets_an_app_open), external = true,
            ) { LinkHandling.openSettings(context) }
        }
    }
}

/** A "Links you share" card: the link as it's sent, over where it opens. */
@Composable
private fun SharedLinkExample(youtube: Boolean) {
    Column {
        Text(
            if (youtube) tr(R.string.youtu_be_fj9ruzimczq) else tr(R.string.music_youtube_com_watch_v_fj9ruzimczq),
            Modifier.fillMaxWidth().padding(vertical = 10.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text(
            if (youtube) tr(R.string.opens_in_youtube) else tr(R.string.opens_in_youtube_music),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------- Last.fm

/**
 * Last.fm's rows (#133, #160), from what the Last.fm client says (so that they're tested without it): in a copy of
 * Prism built without Last.fm, a row that says so; not connected, "Connect Last.fm" (it opens Last.fm's own page in
 * the browser); connected ([user]), scrobbling's switch with who is connected and what waits to be sent, the two
 * options, and Disconnect.
 */
@Composable
internal fun LastFmRows(
    available: Boolean,
    user: String?,
    options: LastFm.Options,
    waiting: Int,
    scrobbleChanged: Boolean,
    firstArtistChanged: Boolean,
    loveChanged: Boolean,
    onOptions: (LastFm.Options) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val about = tr(R.string.sends_what_you_listen_to_scrobbles_all)
    when {
        !available -> LinkRow(LAST_FM, value = tr(R.string.this_copy_of_prism_was_built), enabled = false) {}
        user == null -> LinkRow(tr(R.string.connect_last_fm), value = tr(R.string.cn_not_connected), info = about, external = true, onClick = onConnect)
        else -> {
            SwitchRow(
                tr(R.string.last_fm_scrobbling), options.scrobble,
                // (The string's own leading space doesn't survive strings.xml: "Connected as mina· 3 waiting" before 1.8.4.)
                value = tr(R.string.connected_as, user) + if (waiting > 0) " " + tr(R.string.waiting_to_send, waiting).trim() else "",
                info = about, changed = scrobbleChanged,
            ) { onOptions(options.copy(scrobble = it)) }
            SwitchRow(
                tr(R.string.first_artist_only), options.firstArtistOnly,
                info = tr(R.string.a_b_c_goes_to_last) + ". " + tr(R.string.last_fm_matches_one_artist_per), changed = firstArtistChanged,
            ) { onOptions(options.copy(firstArtistOnly = it)) }
            SwitchRow(tr(R.string.love_when_i_like_a_song), options.loveOnLike, info = tr(R.string.liking_here_loves_it_on_last), changed = loveChanged) {
                onOptions(options.copy(loveOnLike = it))
            }
            LinkRow(tr(R.string.cn_disconnect_lastfm), info = tr(R.string.stops_scrobbling_and_forgets_the_connection), action = true, onClick = onDisconnect)
        }
    }
}

// ---------------------------------------------------------------------------- Spotify

/**
 * Spotify while it isn't connected: one row that says so (or that Prism is waiting for Spotify's answer, or that
 * Spotify took the access back), opening "How to connect".
 */
@Composable
private fun SpotifyConnectRow(connection: Spotify.Connection) {
    val title = tr(R.string.spotify)
    var open by rememberSaveable(title, "spotify-connect") { mutableStateOf(false) }
    PickRow(title, spotifyValue(connection), info = tr(R.string.spot_about)) { open = true }
    if (open) SpotifyConnectSheet(reconnect = connection == Spotify.Connection.Revoked) { open = false }
}

/**
 * "How to connect": the steps to make a developer app in Spotify's dashboard (the dashboard's link and the redirect
 * address to copy among them), the Client ID's field, and Connect. The steps are a numbered list, as they were on the
 * page before 1.8.4 (§2.7). Only Spotify's official API, through the listener's own app.
 */
@Composable
private fun SpotifyConnectSheet(reconnect: Boolean, onDismiss: () -> Unit) {
    val spotify = LocalContainer.current.spotify
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val copy = copier()
    val connection by spotify.connection.collectAsState()
    var clientId by rememberSaveable { mutableStateOf(spotify.store.clientId) }
    val valid = SpotifyOAuth.validClientId(clientId)
    RowsSheet(tr(R.string.cn_how_to_connect), onDismiss, info = tr(R.string.spot_set_up_info)) {
        if (reconnect) ConnectSheetText(tr(R.string.spot_revoked)) else {
            ConnectSheetText(tr(R.string.spot_intro))
            ConnectSheetText(tr(R.string.spot_premium_note))
        }
        ConnectSheetText(tr(R.string.spot_step_1))
        // The kit's rows: a link out of Prism, and an action (a tap copies the address).
        LinkRow(tr(R.string.spot_open_dashboard), value = Spotify.DASHBOARD.removePrefix("https://"), external = true) { runCatching { uri.openUri(Spotify.DASHBOARD) } }
        ConnectSheetText(tr(R.string.spot_step_2))
        ConnectSheetText(tr(R.string.spot_step_3))
        LinkRow(tr(R.string.spot_redirect_uri), value = SpotifyOAuth.REDIRECT_TO_REGISTER, action = true) { copy(SpotifyOAuth.REDIRECT_TO_REGISTER) }
        ConnectSheetText(tr(R.string.spot_step_4))
        ConnectSheetText(tr(R.string.spot_step_5))
        ConnectSheetText(tr(R.string.spot_step_6))
        PrismTextField(
            clientId, { clientId = it.trim() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag(ConnectTags.SPOTIFY_CLIENT_ID),
            label = { Text(tr(R.string.spot_client_id)) },
            singleLine = true,
            isError = clientId.isNotEmpty() && !valid,
            supportingText = if (clientId.isNotEmpty() && !valid) ({ Text(tr(R.string.spot_client_id_invalid)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (connection == Spotify.Connection.Waiting) {
                Text(tr(R.string.spot_waiting), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                PrismOutlinedButton(onClick = { spotify.cancelConnect() }) { Text(tr(R.string.cancel)) }
            } else {
                PrismButton(onClick = { spotify.connect(context, clientId) }, modifier = Modifier.testTag(ConnectTags.SPOTIFY_CONNECT), enabled = valid) {
                    Text(if (reconnect) tr(R.string.spot_reconnect) else tr(R.string.spot_connect))
                }
            }
        }
        (connection as? Spotify.Connection.Failed)?.let { ConnectSheetText(it.message) }
    }
}

/** Each feature's switch; its line says how it's going when that isn't simply "fine". */
@Composable
private fun SpotifyFeatureRows() {
    val spotify = LocalContainer.current.spotify
    val o by spotify.options.collectAsState()
    val scope = rememberCoroutineScope()
    var dryRun by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    /** Spotify not answering for [f] (and when Prism tries again), else [normal]. */
    @Composable
    fun status(f: SpotifyFeature, normal: String? = null): String? {
        val state by spotify.guards.getValue(f).state.collectAsState()
        return when (state) {
            SpotifyGuard.State.OK -> normal
            SpotifyGuard.State.NOT_ANSWERING -> tr(R.string.spot_not_answering)
            SpotifyGuard.State.PAUSED -> tr(R.string.spot_paused)
        }
    }
    SwitchRow(tr(R.string.spot_recent), o.recent, value = status(SpotifyFeature.RECENT), info = tr(R.string.spot_recent_info)) { spotify.setEnabled(SpotifyFeature.RECENT, it) }
    SwitchRow(tr(R.string.spot_top), o.top, value = status(SpotifyFeature.TOP), info = tr(R.string.spot_top_info)) { spotify.setEnabled(SpotifyFeature.TOP, it) }
    val mirror = remember(o.playlists) { spotify.playlists.mirror() }
    // How many are in Library, and how many Spotify doesn't share (what to do about those is behind the ⓘ).
    val counted = if (o.playlists && mirror.listedAt > 0) listOfNotNull(
        tr(R.string.spot_playlists_count, mirror.lists.size),
        if (mirror.notYours > 0) tr(R.string.cn_spot_followed_short, mirror.notYours) else null,
    ).joinToString(" · ") else null
    val followed = if (o.playlists && mirror.notYours > 0) {
        "\n\n" + tr(R.string.spot_playlists_not_yours, mirror.notYours) + "\n" + tr(R.string.spot_followed_why) + "\n" + tr(R.string.spot_followed_steps) + "\n" + tr(R.string.spot_followed_tunemymusic)
    } else ""
    SwitchRow(
        tr(R.string.spot_playlists), o.playlists, value = status(SpotifyFeature.PLAYLISTS, counted), info = tr(R.string.spot_playlists_info) + followed,
    ) { spotify.setEnabled(SpotifyFeature.PLAYLISTS, it) }
    if (o.playlists) SwitchRow(tr(R.string.spot_keep_in_step), o.keepInStep, info = tr(R.string.spot_keep_in_step_info)) { spotify.setKeepInStep(it) }
    SwitchRow(
        tr(R.string.spot_likes), o.likes, value = status(SpotifyFeature.LIKES, if (checking) tr(R.string.spot_likes_counting) else null),
        info = tr(R.string.spot_likes_info),
    ) { on ->
        if (!on) spotify.setEnabled(SpotifyFeature.LIKES, false)
        else if (!checking) {
            // First the counts, then the listener's word.
            checking = true
            scope.launch {
                val r = spotify.likesDryRun()
                checking = false
                dryRun = r.fold({ tr(R.string.spot_likes_dry_run, it.toLikeHere, it.toLikeOnSpotify) }, { "" })
            }
        }
    }
    dryRun?.let { text ->
        AlertDialog(
            onDismissRequest = { dryRun = null },
            title = { Text(tr(R.string.spot_likes)) },
            text = { Text(text.ifEmpty { tr(R.string.spot_not_answering) }) },
            confirmButton = {
                if (text.isNotEmpty()) PrismTextButton(onClick = { dryRun = null; spotify.setEnabled(SpotifyFeature.LIKES, true) }) { Text(tr(R.string.spot_likes_turn_on)) }
            },
            dismissButton = { PrismTextButton(onClick = { dryRun = null }) { Text(tr(R.string.cancel)) } },
        )
    }
}

/** Disconnect (its line says who is connected), asking what to forget. [name] null: Spotify took the access back. */
@Composable
private fun SpotifyDisconnectRow(name: String?) {
    val c = LocalContainer.current
    var asking by remember { mutableStateOf(false) }
    var clientIdToo by remember { mutableStateOf(false) }
    var keepPlaylists by remember { mutableStateOf(false) }
    LinkRow(tr(R.string.cn_disconnect_spotify), value = name?.let { tr(R.string.spot_connected_as, it) }, info = tr(R.string.spot_disconnect_sub), action = true) { asking = true }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(tr(R.string.spot_disconnect_title)) },
        text = {
            Column {
                Text(tr(R.string.spot_disconnect_text))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrismCheck(clientIdToo, label = tr(R.string.spot_forget_client_id)) { clientIdToo = it }
                    Text(tr(R.string.spot_forget_client_id))
                }
                if (name != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    PrismCheck(keepPlaylists, label = tr(R.string.spot_keep_playlists)) { keepPlaylists = it }
                    Text(tr(R.string.spot_keep_playlists))
                }
            }
        },
        confirmButton = {
            PrismTextButton(onClick = { asking = false; c.scope.launch { c.spotify.disconnect(clientIdToo, keepPlaylists) } }) { Text(tr(R.string.disconnect)) }
        },
        dismissButton = { PrismTextButton(onClick = { asking = false }) { Text(tr(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------------------- The page's other parts

/**
 * The root's line for Accounts & sharing: the accounts it holds, with "Discord on" first while it shows what you play
 * (the one account whose state is in the settings).
 */
fun accountsSharingValue(s: AppSettings, dark: Boolean): String =
    if (s.discordPresence) tr(R.string.root_accounts_discord_on) else tr(R.string.root_accounts_short)

/** The look settings "Reset this page" puts back: none (accounts aren't part of a look; the page resets its rows itself). */
val accountsSharingLookKeys: List<String> = emptyList()

/**
 * Old group titles → this page's groups: Spotify's "Set up" and "Connection" are its one card now. (Discord, Last.fm,
 * Spotify and "Sharing and links" are still found by those names: they are the cards' keys.)
 */
val accountsSharingOldGroups: Map<String, String> by lazy {
    mapOf(tr(R.string.spot_set_up) to tr(R.string.spotify), tr(R.string.spot_connection) to tr(R.string.spotify))
}

/** The page's lines in Settings' search (each names the group it is drawn in). */
val accountsSharingIndex: List<SettingEntry> by lazy {
    val accounts = tr(R.string.accounts)
    val discord = tr(R.string.discord)
    val spotify = tr(R.string.spotify)
    val sharing = tr(R.string.sharing_and_links)
    val spot = tr(R.string.spot_keywords)
    listOf(
        SettingEntry(page, accounts, tr(R.string.prism_account), tr(R.string.profile_name_picture_photo_local_on)),
        SettingEntry(page, accounts, tr(R.string.youtube_music_2), tr(R.string.cn_youtube_keywords), shown = tr(R.string.sign_in_to_youtube_music)),
        SettingEntry(page, accounts, tr(R.string.cn_youtube_history), tr(R.string.cn_youtube_history_keywords)),
        // Last.fm: "Connect Last.fm" until it's connected, then its three switches and Disconnect.
        SettingEntry(page, LAST_FM, tr(R.string.connect_last_fm), tr(R.string.lastfm_last_fm_scrobble_scrobbling_account)),
        SettingEntry(page, LAST_FM, tr(R.string.last_fm_scrobbling), tr(R.string.lastfm_last_fm_scrobble) + " " + tr(R.string.lastfm_scrobble_send_plays) + " " + tr(R.string.scrobble)),
        SettingEntry(page, LAST_FM, tr(R.string.first_artist_only), tr(R.string.lastfm_featured_artists_main_artist)),
        SettingEntry(page, LAST_FM, tr(R.string.love_when_i_like_a_song), tr(R.string.lastfm_love_like_heart)),
        SettingEntry(page, LAST_FM, tr(R.string.cn_disconnect_lastfm), tr(R.string.lastfm_sign_out)),
        // 1.8.1: Spotify, through the listener's own developer app. Its set-up is the "How to connect" sheet of its row.
        SettingEntry(page, spotify, spotify, spot),
        SettingEntry(page, spotify, tr(R.string.cn_how_to_connect), spot + " " + tr(R.string.spot_set_up), shown = spotify + ": " + tr(R.string.cn_how_to_connect)),
        SettingEntry(page, spotify, tr(R.string.spot_open_dashboard), spot),
        SettingEntry(page, spotify, tr(R.string.spot_redirect_uri), spot),
        SettingEntry(page, spotify, tr(R.string.spot_client_id), spot),
        // The switches' second lines of before 1.8.4 are words to find them by.
        SettingEntry(page, spotify, tr(R.string.spot_recent), spot + " " + tr(R.string.spot_recent_sub)),
        SettingEntry(page, spotify, tr(R.string.spot_top), spot + " " + tr(R.string.spot_top_sub)),
        SettingEntry(page, spotify, tr(R.string.spot_playlists), spot + " " + tr(R.string.spot_playlists_sub)),
        SettingEntry(page, spotify, tr(R.string.spot_keep_in_step), spot + " " + tr(R.string.spot_keep_in_step_sub)),
        SettingEntry(page, spotify, tr(R.string.spot_likes), spot + " " + tr(R.string.spot_likes_sub)),
        SettingEntry(page, spotify, tr(R.string.cn_disconnect_spotify), spot + " " + tr(R.string.spot_connected_as, "").trim()),
        // "About this" (the small risk to the Discord account) is behind the switch's ⓘ now.
        SettingEntry(page, discord, tr(R.string.cn_discord_show), tr(R.string.discord_rich_presence_status_listening) + " " + tr(R.string.discord_terms_risk)),
        SettingEntry(page, discord, tr(R.string.sign_out_of_discord)),
        SettingEntry(page, sharing, sharing, tr(R.string.share_link)),
        SettingEntry(page, sharing, tr(R.string.links_you_share), tr(R.string.share_link_youtube_youtu_be_youtube)),
        SettingEntry(page, sharing, tr(R.string.open_youtube_music_links_in_prism), tr(R.string.open_by_default_supported_links_music)),
    )
}
