package com.prism.music

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.data.spotify.Spotify
import com.prism.music.data.spotify.SpotifyOAuth
import kotlinx.coroutines.flow.MutableStateFlow
import com.prism.music.ui.screens.SettingsIndex
import com.prism.music.ui.screens.settings.SettingsMap
import com.prism.music.ui.screens.settings.SettingsPageId
import com.prism.music.ui.screens.settings.SettingsRowValue
import com.prism.music.ui.screens.settings.SettingsTags
import com.prism.music.ui.screens.settings.SettingsTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 1.8.4, Settings → Accounts & sharing rebuilt with the kit (plan §2.3, §2.7, the ledger's table): five groups within
 * the budgets (Accounts, Last.fm, Spotify, Discord, Sharing and links); a control for both stored settings the ledger
 * gives the page; each service's first row saying its state; Spotify's steps in a "How to connect" sheet and not on the
 * page, worded as they must be (the official API through the listener's own app, never "sign in with Spotify", nothing
 * about a web player); the rows that only explained gone; Reset this page; the old links; and the page drawn in light,
 * dark, Performance mode and at large text.
 *
 * Nothing here reaches a service: no Spotify switch is turned on, Connect is never tapped, and Discord's switch is only
 * turned off. The Discord sign-in the tests make up is never shown (and that is checked).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h4000dp-xhdpi", application = android.app.Application::class)
class AccountsSharingPageTest : ListeningPageHarness(SettingsPageId.ACCOUNTS_SHARING) {
    private fun t(id: Int, vararg args: Any) = tr(id, *args)
    private val me = SettingsPageId.ACCOUNTS_SHARING
    private val app get() = RuntimeEnvironment.getApplication()

    private val accounts = t(R.string.accounts)
    private val sharing = t(R.string.sharing_and_links)
    private val prismAccount = t(R.string.prism_account)
    private val youtubeMusic = t(R.string.youtube_music_2)
    private val history = t(R.string.cn_youtube_history)
    private val spotify = t(R.string.spotify)
    private val discord = t(R.string.cn_discord_show)
    private val discordSignOut = t(R.string.sign_out_of_discord)
    private val links = t(R.string.links_you_share)
    private val openLinks = t(R.string.open_youtube_music_links_in_prism)
    private val notConnected = t(R.string.cn_not_connected)

    /** `ConnectTags`: a line and a paragraph of a sheet's content, the Client ID's field and Connect. */
    private val line = "settings:connect:line"
    private val paragraph = "settings:connect:text"
    private val clientIdField = "settings:connect:spotify-client-id"
    private val connectButton = "settings:connect:spotify-connect"

    /** A row by its title alone (a row's value can be another row's title: "YouTube Music"). */
    private fun titled(title: String): SemanticsNode = rows().single { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text == title }
    private fun hasTitled(title: String) = rows().any { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text == title }
    private fun value(title: String): String = titled(title).config[SettingsRowValue]
    private fun type(title: String): String = titled(title).config[SemanticsProperties.TestTag]

    /** The titles over the page's cards. */
    private fun headings(): List<String> = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasAnyAncestor(hasTestTag(SettingsTags.GROUP)))
        .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }

    private fun texts(tag: String): List<String> = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
        .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    private fun saveNode(tag: String, name: String): File {
        val shot = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val dir = File("build/settings-pages/accounts-sharing").apply { mkdirs() }
        return File(dir, "$name.png").also { f -> f.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    /**
     * Spotify as connected, with no access at all (no feature is on, and none is turned on): the connection's state is
     * set as it is read. (`connectForTest` seals its tokens with the Android Keystore, which a unit test doesn't have.)
     */
    @Suppress("UNCHECKED_CAST")
    private fun connectSpotify() {
        val state = Spotify::class.java.getDeclaredField("_connection").apply { isAccessible = true }.get(container.spotify) as MutableStateFlow<Spotify.Connection>
        state.value = Spotify.Connection.Connected("Tester")
    }

    /** Discord as signed in (a made-up sign-in in its own preferences), with the switch as stored. */
    private fun signInToDiscord() {
        app.getSharedPreferences("discord", Context.MODE_PRIVATE).edit().putString("token", MADE_UP_ACCESS).commit()
        container.discord.userName.value = "tester"
    }

    private fun assertNoSecretShown() {
        val all = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) or SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + listOfNotNull(n.config.getOrNull(SemanticsProperties.EditableText)?.text) }
        assertTrue("the page's text was read", all.size > 5)
        assertTrue("the made-up sign-in is on the page", all.none { MADE_UP_ACCESS in it })
    }

    @Test fun fiveGroupsWithinTheBudgets() {
        show()
        assertBudgets("defaults")
        // Accounts 3 · Last.fm 1 · Spotify 1 · Discord 1 · Sharing and links 2.
        assertEquals(listOf(3, 1, 1, 1, 2), groupSizes())
        assertEquals(0, changedRows())
        // Last.fm, Spotify and Discord have no title over their card: their first row names them (§2.7).
        assertEquals(listOf(accounts, sharing), headings())
        assertTrue(headings().none { it == me.title })

        assertEquals(SettingsTags.ROW_LINK, type(prismAccount))
        assertEquals(t(R.string.cn_not_set_up), value(prismAccount))
        assertEquals(SettingsTags.ROW_LINK, type(youtubeMusic))
        assertEquals(t(R.string.tv_not_signed_in), value(youtubeMusic))
        assertEquals(SettingsTags.ROW_LINK, type(history))
        assertEquals(t(R.string.ytlive_connection_signed_out), value(history))
        // Last.fm: "Connect Last.fm" · Not connected, or (a copy built without Last.fm's key) a row that says so.
        if (container.lastFm.available) {
            assertEquals(SettingsTags.ROW_LINK, type(t(R.string.connect_last_fm)))
            assertEquals(notConnected, value(t(R.string.connect_last_fm)))
        } else {
            assertEquals(t(R.string.this_copy_of_prism_was_built), value("Last.fm"))
        }
        assertEquals(SettingsTags.ROW_PICK, type(spotify))
        assertEquals(notConnected, value(spotify))
        assertEquals(SettingsTags.ROW_SWITCH, type(discord))
        assertEquals(t(R.string.tv_not_signed_in), value(discord))
        assertFalse(hasTitled(discordSignOut))
        assertEquals(SettingsTags.ROW_PICK, type(links))
        assertEquals(youtubeMusic, value(links))
        assertEquals(SettingsTags.ROW_LINK, type(openLinks))
        assertTrue(value(openLinks).isNotBlank())

        // §2.7: nothing that only explains is on the page: Spotify's notes and steps, Discord's "About this", and the
        // sentences that sat under the old rows.
        for (id in listOf(
            R.string.spot_intro, R.string.spot_premium_note, R.string.spot_step_1, R.string.spot_step_6, R.string.spot_set_up, R.string.spot_client_id,
            R.string.about_this, R.string.a_small_risk_to_your_discord, R.string.listening_to_prism_on_your_profile, R.string.opens_last_fm_to_let_prism,
        )) {
            assertEquals(t(id), 0, compose.onAllNodes(hasText(t(id))).fetchSemanticsNodes().size)
        }
    }

    /** Principle 8: both stored settings the ledger gives the page have their control here, and it stores them. */
    @Test fun everyStoredSettingHasItsControl() {
        val d = AppSettings()
        // discord_presence: the switch shows on only signed in to Discord. It is stored on first, signed out (so Prism
        // doesn't go to Discord), given time to be read, and only then is a made-up sign-in put in place.
        container.settings.setDiscordPresence(true)
        until("discord_presence stored") { it.discordPresence }
        Thread.sleep(600)
        signInToDiscord()
        show()

        assertEquals(t(R.string.on_discord_as, "tester"), value(discord))
        assertTrue("signed in: Sign out shows", hasTitled(discordSignOut))
        assertEquals(listOf(3, 1, 1, 2, 2), groupSizes())
        tap(discord)
        until("discord_presence") { !it.discordPresence }
        assertEquals(t(R.string.cn_signed_in_as, "tester"), value(discord))
        // Sign out of Discord forgets the sign-in; its row goes.
        tap(discordSignOut)
        compose.waitUntil(5_000) { !hasTitled(discordSignOut) }
        assertFalse(container.discord.signedIn)
        assertEquals(t(R.string.tv_not_signed_in), value(discord))

        // share_youtube_links: in its pick sheet, a card for each.
        tap(links)
        assertEquals(2, compose.onAllNodes(hasTestTag(SettingsTags.SHEET_OPTION)).fetchSemanticsNodes().size)
        swipeSheetAway()
        assertEquals("a sheet swiped away changes nothing", d.shareYoutubeLinks, stored.shareYoutubeLinks)
        pick(links, t(R.string.youtube))
        until("share_youtube_links") { it.shareYoutubeLinks }
        assertEquals(t(R.string.youtube), value(links))
        assertEquals(1, changedRows())

        // Reset this page lists it and puts it back.
        val lines = resetLines(confirm = true)
        assertEquals(listOf("$links: ${t(R.string.youtube)} → $youtubeMusic"), lines)
        until("the reset") { it.shareYoutubeLinks == d.shareYoutubeLinks && it.discordPresence == d.discordPresence }
        assertEquals(0, changedRows())
        assertNoSecretShown()
    }

    /** Signed out of Discord, the switch goes to Discord's own sign-in page, and the accounts' rows to their screens. */
    @Test fun theLinksLeadToTheirOwnScreens() {
        val source = File("src/main/java/com/prism/music/ui/screens/settings/AccountsSharingPage.kt").readText()
        assertTrue("if (on && !signedIn) nav.go(Routes.DISCORD_LOGIN) else c.settings.setDiscordPresence(on)" in source)
        assertTrue("{ nav.go(Routes.ACCOUNT) }" in source)
        assertTrue("{ nav.go(if (s.isLoggedIn) Routes.LOGIN_ADD else Routes.LOGIN) }" in source)
        assertTrue("{ nav.go(Routes.BRING_HISTORY) }" in source)
        assertTrue("{ LinkHandling.openSettings(context) }" in source)
        // The page is its own code now.
        for (old in listOf("InterimPage(", "SpotifyPage(", "DiscordGroup(", "SharingGroup(", "LastFmGroup(")) assertTrue(old, old !in source)
        // Signed in to YouTube Music, the rows say so.
        show(over = { it.copy(cookie = "SAPISID=x", accountName = "Mina") })
        assertEquals(t(R.string.cn_signed_in_as, "Mina"), value(youtubeMusic))
        assertTrue(value(history) == t(R.string.ytlive_connection_off) || value(history) == t(R.string.ytlive_connection_on))
        assertBudgets("signed in")
    }

    /**
     * §2.7: Spotify's set-up is a "How to connect" sheet behind its row: the six steps in order, the dashboard's link,
     * the redirect address to copy, the Client ID's field and Connect. Its words: the official API through the
     * listener's own developer app; never "sign in with Spotify"; nothing about a web player.
     */
    @Test fun spotifysStepsAreInItsHowToConnectSheet() {
        show()
        assertEquals(0, texts(paragraph).size)
        compose.onNode(rowIs(spotify)).performClick()
        compose.waitForIdle()
        assertEquals(1, sheets())
        assertEquals(1, compose.onAllNodes(hasText(t(R.string.cn_how_to_connect))).fetchSemanticsNodes().size)
        val said = texts(paragraph)
        val steps = listOf(R.string.spot_step_1, R.string.spot_step_2, R.string.spot_step_3, R.string.spot_step_4, R.string.spot_step_5, R.string.spot_step_6).map { t(it) }
        assertEquals("the steps, in order", steps, said.filter { it in steps })
        assertEquals(steps.indices.map { "${it + 1}." }, steps.map { it.take(2) })
        assertEquals(listOf(t(R.string.spot_intro), t(R.string.spot_premium_note)), said.take(2))
        // The dashboard (a link out of Prism) and the redirect address (a tap copies it) are the kit's rows, between
        // their steps; nothing else in the sheet is a line of its own.
        assertEquals(0, compose.onAllNodes(hasTestTag(line)).fetchSemanticsNodes().size)
        val links = compose.onAllNodes(hasTestTag(SettingsTags.ROW_LINK) and hasAnyAncestor(hasTestTag(SettingsTags.SHEET))).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertTrue(links.toString(), links.containsAll(listOf(t(R.string.spot_open_dashboard), t(R.string.spot_redirect_uri), SpotifyOAuth.REDIRECT_TO_REGISTER)))

        // The wording rules, over everything Spotify says here and behind its ⓘs.
        val words = (said + links + listOf(R.string.spot_about, R.string.spot_set_up_info, R.string.cn_how_to_connect, R.string.spot_client_id, R.string.spot_connect).map { t(it) })
            .joinToString("\n").lowercase()
        assertFalse("never \"sign in with Spotify\"", "sign in with spotify" in words || "log in with spotify" in words || "login with spotify" in words)
        assertFalse("nothing about a web player", "web player" in words || "web-player" in words || "webplayer" in words || "open.spotify.com" in words)
        assertTrue("the official API", "official" in words)
        assertTrue("the listener's own developer app", "developer" in words && ("your own" in words || "for yourself" in words))

        // The Client ID is typed in the sheet; Connect waits for one that can be a Client ID.
        compose.onNodeWithTag(connectButton).assertIsNotEnabled()
        compose.onNodeWithTag(clientIdField).performTextInput("not-an-id")
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodes(hasText(t(R.string.spot_client_id_invalid))).fetchSemanticsNodes().size)
        compose.onNodeWithTag(connectButton).assertIsNotEnabled()
        // (Connect is never tapped here: it opens Spotify's page.)
        assertEquals(Spotify.Connection.None, container.spotify.connection.value)
        swipeSheetAway()
        assertEquals(notConnected, value(spotify))
        assertBudgets("after the sheet")
    }

    /** Connected, Spotify's card is titled, holds the features' switches, and Disconnect says who is connected. */
    @Test fun spotifysOptionsShowWhileConnected() {
        connectSpotify()
        show()
        assertBudgets("Spotify connected")
        assertEquals(listOf(accounts, spotify, sharing), headings())
        // Recent plays, Top songs and artists, Your playlists, Liked Songs both ways, Disconnect Spotify.
        assertEquals(listOf(3, 1, 5, 1, 2), groupSizes())
        for (id in listOf(R.string.spot_recent, R.string.spot_top, R.string.spot_playlists, R.string.spot_likes)) assertEquals(t(id), SettingsTags.ROW_SWITCH, type(t(id)))
        // "Keep in step" only shows with the playlists on.
        assertFalse(hasTitled(t(R.string.spot_keep_in_step)))
        val disconnect = t(R.string.cn_disconnect_spotify)
        assertEquals(SettingsTags.ROW_LINK, type(disconnect))
        assertEquals(t(R.string.spot_connected_as, "Tester"), value(disconnect))
        assertFalse("its own row is gone", hasTitled(spotify))
        assertNoSecretShown()

        // Disconnect asks first, as before: what to forget, and Cancel changes nothing.
        tap(disconnect)
        compose.onNodeWithText(t(R.string.spot_disconnect_title)).assertExists()
        compose.onNodeWithText(t(R.string.spot_disconnect_text)).assertExists()
        assertEquals(1, compose.onAllNodes(hasText(t(R.string.spot_forget_client_id))).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasText(t(R.string.spot_keep_playlists))).fetchSemanticsNodes().size)
        compose.onNodeWithText(t(R.string.disconnect)).assertIsEnabled()
        compose.onNodeWithText(t(R.string.cancel)).performClick()
        compose.waitForIdle()
        assertEquals(Spotify.Connection.Connected("Tester"), container.spotify.connection.value)
        assertNoSecretShown()
    }

    /** The old links land, and every line of the page's search names a group the page draws. */
    @Test fun theOldRoutesAndSearch() {
        val discordKey = t(R.string.discord)
        assertEquals(SettingsTarget(me, discordKey), SettingsMap.resolve("discord"))
        assertEquals(SettingsTarget(me, "Last.fm"), SettingsMap.resolve("lastfm"))
        assertEquals(SettingsTarget(me, spotify), SettingsMap.resolve("spotify"))
        // Spotify's "Set up" and "Connection" groups are its one card.
        assertEquals(SettingsTarget(me, spotify), SettingsMap.resolve("spotify", t(R.string.spot_set_up)))
        assertEquals(SettingsTarget(me, spotify), SettingsMap.resolve("spotify", t(R.string.spot_connection)))
        // Sharing and links came from Devices & connections.
        assertEquals(SettingsTarget(me, sharing), SettingsMap.resolve("devices", sharing))

        val mine = SettingsIndex.entries.filter { it.page == me && !it.isPage }
        assertEquals(setOf(accounts, "Last.fm", spotify, discordKey, sharing), mine.map { it.group }.toSet())
        // The groups' keys are in the page as it is written (three of the cards have no title to read them from).
        val source = File("src/main/java/com/prism/music/ui/screens/settings/AccountsSharingPage.kt").readText()
        for (key in listOf("group(tr(R.string.accounts))", "group(null, key = LAST_FM)", "key = tr(R.string.spotify)", "group(null, key = tr(R.string.discord))", "group(tr(R.string.sharing_and_links))")) {
            assertTrue(key, key in source)
        }
        // The rows that only explained have no line any more; what they said is words for the row that took it over.
        assertTrue(mine.none { it.title == t(R.string.about_this) })
        assertTrue(SettingsIndex.search("discord risk").any { it.title == discord })
        assertTrue(SettingsIndex.search("scrobble").any { it.page == me && it.group == "Last.fm" })
        assertTrue(SettingsIndex.search("spotify client id").any { it.page == me && it.group == spotify })
        assertTrue(SettingsIndex.search("links you share").any { it.title == links })
        // Search never says "sign in with Spotify" either.
        assertTrue(mine.none { "sign in with spotify" in it.shown.lowercase() || "sign in with spotify" in it.title.lowercase() })
    }

    @Test fun atLargeTextNothingIsCutOff() {
        connectSpotify()
        signInToDiscord()
        show(fontScale = 1.5f)
        assertBudgets("large text")
        assertNoTextCutOff("font scale 1.5")
    }

    private fun drawn(name: String, glass: Boolean, over: (AppSettings) -> AppSettings) {
        show(glass = glass, over = over)
        val file = save("accounts-sharing", name)
        assertTrue(file.path, file.length() > 10_000)
        assertBudgets(name)
        assertNoTextCutOff(name)
        assertNoSecretShown()
    }

    @Test @Config(qualifiers = "w360dp-h900dp-xxhdpi") fun drawnLight() = drawn("accounts-light", true) { it.copy(themeMode = ThemeMode.LIGHT) }
    @Test @Config(qualifiers = "w360dp-h900dp-xxhdpi") fun drawnDark() = drawn("accounts-dark", true) { it.copy(themeMode = ThemeMode.DARK) }
    @Test @Config(qualifiers = "w360dp-h900dp-xxhdpi") fun drawnPerformance() = drawn("accounts-performance", false) { it.copy(themeMode = ThemeMode.DARK, performanceMode = true) }

    /** The page's longest state: Spotify connected and signed in to Discord (the whole page, on a tall window). */
    @Test @Config(qualifiers = "w360dp-h1700dp-xxhdpi") fun drawnConnected() {
        connectSpotify()
        signInToDiscord()
        drawn("accounts-dark-connected", true) { it.copy(themeMode = ThemeMode.DARK, cookie = "SAPISID=x", accountName = "Mina") }
    }

    /** "How to connect", and the "Links you share" sheet. */
    @Test @Config(qualifiers = "w360dp-h900dp-xxhdpi") fun drawnSheets() {
        show(glass = true) { it.copy(themeMode = ThemeMode.DARK) }
        compose.onNode(rowIs(spotify)).performClick()
        compose.waitForIdle()
        assertEquals(1, sheets())
        val how = runCatching { saveNode(SettingsTags.SHEET, "accounts-dark-how-to-connect") }
        assertTrue("the sheet's picture: ${how.exceptionOrNull()}", how.isSuccess)
        swipeSheetAway()
        tap(links)
        assertEquals(1, sheets())
        val cards = runCatching { saveNode(SettingsTags.SHEET, "accounts-dark-links-you-share") }
        assertTrue("the sheet's picture: ${cards.exceptionOrNull()}", cards.isSuccess)
    }

    private companion object {
        const val MADE_UP_ACCESS = "made-up-sign-in-for-the-test"
    }
}
