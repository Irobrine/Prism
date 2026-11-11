package com.prism.music

import android.os.Looper
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.robolectric.Shadows.shadowOf

/**
 * A container a test made for itself is closed as [PrismApp.onTerminate] closes the app's: its work is cancelled and
 * waited for, then its database closes. Without it the library's lists keep reading a database Robolectric has already
 * let go of, and the error ("Illegal connection pointer", from Room's invalidation tracker or a read on Dispatchers.IO)
 * lands in whichever Compose test runs next as "uncaught exceptions before the test started" (1.8.4: SettingsMapTest, then
 * ComposeTestThreadTest, in full runs).
 *
 * The wait runs the main thread's queue meanwhile (a cancelled coroutine that has to come back to the main thread can't
 * end while this holds it) and gives a read already inside SQLite time to come out, up to [waitMs]: a test can afford
 * longer than the app's own 300 ms.
 */
fun Lazy<AppContainer>.closeAfterTest(waitMs: Long = 5_000) {
    if (!isInitialized()) return
    val c = value
    val job = c.scope.coroutineContext[Job]
    c.scope.cancel()
    val main = shadowOf(Looper.getMainLooper())
    val until = System.currentTimeMillis() + waitMs
    while (job != null && !job.isCompleted && System.currentTimeMillis() < until) {
        runCatching { main.idle() }
        Thread.sleep(5)
    }
    // Room's own refresh runs on its executor, outside the container's scope: let one already started finish.
    runCatching { main.idle() }
    Thread.sleep(50)
    runCatching { c.db.close() }
}
