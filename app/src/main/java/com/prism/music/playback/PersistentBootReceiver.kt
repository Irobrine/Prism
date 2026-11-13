package com.prism.music.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prism.music.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Best-effort autostart (1.8.5): after a restart (or Prism's own update), bring the persistent media controls back.
 * Android 15+ refuses a mediaPlayback foreground service from BOOT_COMPLETED; the start then fails quietly and the
 * card comes back the next time Prism is opened. Phones with their own Autostart switch may also stop this.
 */
class PersistentBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.container.settings.flow.first().persistent.controls) {
                    runCatching { context.startService(Intent(context, PlaybackServices.target(context))) }
                }
            } finally {
                result.finish()
            }
        }
    }
}
