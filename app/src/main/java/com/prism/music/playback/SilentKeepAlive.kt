package com.prism.music.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Compatibility mode's silent playback: a zero-volume AudioTrack that keeps Android (and OriginOS/Funtouch) treating
 * Prism as a playing media app while the placeholder card is up. It makes no sound; it is stopped as soon as a real
 * song starts or the card goes away.
 */
internal class SilentKeepAlive(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null

    fun start() {
        if (track != null) return
        runCatching {
            val samples = 8_000 // one second at 8 kHz
            val buffer = ShortArray(samples)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            // The focus is what makes some phones treat Prism as the playing media app (not only the audio track).
            runCatching {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { }
                    .build()
                if (audioManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = request
            }
            val t = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(8_000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buffer.size * 2)
                .build()
            t.write(buffer, 0, buffer.size)
            t.setLoopPoints(0, buffer.size, -1)
            t.setVolume(0f)
            t.play()
            track = t
            android.util.Log.i("PrismControl", "compatibility mode: silent playback on")
        }.onFailure { android.util.Log.w("PrismControl", "compatibility mode: couldn't play silence: $it") }
    }

    fun stop() {
        track?.let { t ->
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        track = null
        focus?.let { request -> runCatching { audioManager?.abandonAudioFocusRequest(request) } }
        focus = null
    }
}
