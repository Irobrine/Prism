package com.prism.music.playback

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.prism.music.container
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Persistent media controls (1.8.5): keeps Prism's process up after its task is swiped away, the way Tasker does.
 *
 * Phones such as vivo's stop an app's foreground service when its task is dismissed, which takes the persistent
 * controls with it. Android keeps an enabled accessibility service's process running, and starts it again if it is
 * stopped: so this service only exists, and every time it comes up (and every little while) it makes sure the playback
 * service is running too. It reads nothing on screen and performs no gestures.
 */
class PersistentKeepAliveService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val check = object : Runnable {
        override fun run() {
            ensurePlaying()
            handler.postDelayed(this, EVERY_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("PrismControl", "keep-alive: accessibility service connected")
        ensurePlaying()
        handler.removeCallbacks(check)
        handler.postDelayed(check, EVERY_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(check)
        Log.i("PrismControl", "keep-alive: accessibility service stopped")
        super.onDestroy()
    }

    /** Starts the playback service (and with it the persistent controls) when they're on and it isn't running. */
    private fun ensurePlaying() {
        val app = applicationContext
        if (PlaybackServices.running != null) return
        Thread {
            val on = runCatching {
                runBlocking { app.container.settings.flow.first().persistent.controls }
            }.getOrDefault(false)
            if (!on || PlaybackServices.running != null) return@Thread
            val intent = Intent(app, PlaybackServices.target(app))
            runCatching { ContextCompat.startForegroundService(app, intent) }
                .recoverCatching { app.startService(intent) }
                .onSuccess { Log.i("PrismControl", "keep-alive: the playback service was started") }
                .onFailure { Log.w("PrismControl", "keep-alive: couldn't start the playback service: $it") }
        }.start()
    }

    companion object {
        /** How often the playback service is checked on (it may be stopped without the process going). */
        private const val EVERY_MS = 15_000L

        /** Whether the listener has turned Prism's keep-alive accessibility service on. */
        fun enabled(context: android.content.Context): Boolean {
            val flat = runCatching {
                android.provider.Settings.Secure.getString(
                    context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            }.getOrNull() ?: return false
            val me = "${context.packageName}/${PersistentKeepAliveService::class.java.name}"
            return flat.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
