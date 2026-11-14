package com.prism.music

import com.prism.music.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prismatica's updater asks for Prismatica's own releases, never Prism's (another app, with its own package and
 * signing key), and reads Prismatica's APK name. GitHub's "latest" leaves out the releases it marks as pre-releases
 * (1.8.5's archaic builds), so a stable build is never offered one.
 */
class PrismaticaUpdatesTest {
    @Test fun theForkSReleasesAreAskedForAndItsApkRead() {
        assertEquals("Irobrine/Prismatica", UpdateChecker.REPO)
        assertEquals("Prismatica.apk", UpdateChecker.APK_NAME)
        assertEquals(
            "1.8.4.1-prismatica.3",
            UpdateChecker.versionFromLatestRedirect("https://github.com/Irobrine/Prismatica/releases/tag/v1.8.4.1-prismatica.3"),
        )
        assertEquals(null, UpdateChecker.versionFromLatestRedirect("https://github.com/Irobrine/Prismatica/releases"))
    }

    /**
     * An APK reports no rebuild number in its version (1.8.4.1 is 1.8.4.1 for every prismatica.N, and the versionCode
     * is the same too), so a rebuild of the installed version isn't offered; only a newer version is.
     */
    @Test fun onlyANewerVersionIsOffered() {
        assertFalse(UpdateChecker.isNewer("1.8.4.1-prismatica.3", "1.8.4.1"))
        assertFalse(UpdateChecker.isNewer("1.8.5-prismatica.1", "1.8.5"))
        assertTrue(UpdateChecker.isNewer("1.8.5-prismatica.1", "1.8.4.1"))
    }
}
