package com.prism.music

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.ui.components.PhoneFilePicker
import com.prism.music.ui.theme.PrismTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Choose a file on this phone" starts its search with the words the "Search with another title" box has. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class SongVersionsSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theFilePickerStartsWithTheSearchBoxsWords() {
        compose.setContent {
            PrismTheme(AppSettings(themeMode = ThemeMode.DARK), artworkColor = null) {
                PhoneFilePicker(emptyList(), "File Anthem Test Artist") {}
            }
        }
        compose.waitForIdle()
        compose.onNode(hasText("File Anthem Test Artist"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("filePickerHandle", useUnmergedTree = true).assertExists()
    }
}
