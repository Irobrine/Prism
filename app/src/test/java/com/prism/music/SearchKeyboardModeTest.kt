package com.prism.music

import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.SearchKeyboardMode
import com.prism.music.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Open the keyboard" (1.8.6): one choice of three over the two switches it is stored as, so a setting from before 1.8.6
 * reads as it did (on was "when Search opens"). Neither switch travels in a Look; both travel in a backup.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class SearchKeyboardModeTest {
    private val names = setOf("search_keyboard", "search_keyboard_second_tap")

    private fun mode(s: AppSettings) = SearchKeyboardMode.of(s.searchKeyboard, s.searchKeyboardOnSecondTap)

    private fun withStore(block: suspend (SettingsRepository) -> Unit): Unit = runBlocking {
        val r = SettingsRepository(org.robolectric.RuntimeEnvironment.getApplication(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        r.editNow { it.clear() }
        try { block(r) } finally { r.editNow { it.clear() } }
    }

    private suspend fun SettingsRepository.storedWhen(until: (AppSettings) -> Boolean): AppSettings = withTimeout(5_000) { stored.first(until) }

    @Test
    fun theTwoSwitchesMakeTheThreeChoices() {
        assertEquals(SearchKeyboardMode.OFF, SearchKeyboardMode.of(opens = false, secondTap = false))
        // The second tap means nothing while the keyboard never comes up.
        assertEquals(SearchKeyboardMode.OFF, SearchKeyboardMode.of(opens = false, secondTap = true))
        // As before 1.8.6: the switch on was "when Search opens".
        assertEquals(SearchKeyboardMode.WHEN_SEARCH_OPENS, SearchKeyboardMode.of(opens = true, secondTap = false))
        assertEquals(SearchKeyboardMode.ON_SECOND_TAP, SearchKeyboardMode.of(opens = true, secondTap = true))
        assertEquals(3, SearchKeyboardMode.entries.size)
    }

    @Test
    fun theStoreKeepsBothSwitchesAndABackupCarriesThem() = withStore { r ->
        assertEquals(SearchKeyboardMode.OFF, mode(r.storedWhen { true }))
        r.setSearchKeyboard(true)
        assertEquals(SearchKeyboardMode.WHEN_SEARCH_OPENS, mode(r.storedWhen { it.searchKeyboard }))
        r.setSearchKeyboardOnSecondTap(true)
        assertEquals(SearchKeyboardMode.ON_SECOND_TAP, mode(r.storedWhen { it.searchKeyboardOnSecondTap }))
        // Off puts the second tap back to what Prism comes with.
        r.setSearchKeyboard(false)
        assertEquals(SearchKeyboardMode.OFF, mode(r.storedWhen { !it.searchKeyboard }))

        assertTrue(r.allKeyNames.containsAll(names))
        assertTrue(r.notLookNames.containsAll(names))
        assertTrue("a Look never sets them: ${r.lookKinds.keys intersect names}", (r.lookKinds.keys intersect names).isEmpty())
        r.setSearchKeyboard(true)
        r.setSearchKeyboardOnSecondTap(true)
        r.storedWhen { it.searchKeyboard && it.searchKeyboardOnSecondTap }
        val backup = r.forBackup(settings = true, accounts = false)
        assertEquals(names.associateWith { "b:true" }, backup.filterKeys { it in names })
    }
}
