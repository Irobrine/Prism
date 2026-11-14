package com.prism.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tab switching hands over quicker than a page opening from a list; only two top-level tabs count. */
class TabSwapFadeTest {
    private val tabs = setOf("home", "search", "library", "liked")

    @Test fun twoTabsHandOverQuickly() {
        assertTrue(bothAreTabs("home", "library", tabs))
        assertTrue(bothAreTabs("library", "home", tabs))
        // A tab's page with optional arguments is still that tab.
        assertTrue(bothAreTabs("liked?genre={genre}", "home", tabs))
        assertTrue(bothAreTabs("home", "liked?genre={genre}", tabs))
    }

    @Test fun aPageOpenedFromATabKeepsTheLongerFade() {
        assertFalse(bothAreTabs("album/{id}", "library", tabs))
        assertFalse(bothAreTabs("home", "artist/{id}", tabs))
        assertFalse(bothAreTabs("settings/about", "settings", tabs))
        // The settings tab's own route is a tab, but the page leaving it is not.
        assertFalse(bothAreTabs("settings/about", "home", tabs))
        assertFalse(bothAreTabs(null, "home", tabs))
    }
}
