package com.prism.music.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.prism.music.container
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Best-effort start of the persistent controls after Prism is swiped away and its process is killed anyway (vivo does
 * this to a foreground service). The battery-unrestricted prompt is what lets a background start reach the foreground.
 */
class PersistentRestartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext
        val on = runCatching { runBlocking { app.container.settings.flow.first().persistent.controls } }.getOrDefault(false)
        if (on) {
            val intent = Intent(app, PlaybackServices.target(app))
            runCatching { ContextCompat.startForegroundService(app, intent) }
                .recoverCatching { app.startService(intent) }
                .onFailure { android.util.Log.w("PrismControl", "couldn't restart the persistent controls: $it") }
        }
        return Result.success()
    }
}
