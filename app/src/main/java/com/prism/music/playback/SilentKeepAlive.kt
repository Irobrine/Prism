package com.prism.music.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Compatibility mode's silent playback: a zero-volume AudioTrack that keeps Android (and OriginOS/Funtouch) treating
 * Prism as a playing media app while the placeholder card is up. It takes no audio focus and makes no sound; it is
 * stopped as soon as a real song starts or the card goes away.
 */
internal class SilentKeepAlive {
    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return
        runCatching {
            val samples = 8_000 // one second at 8 kHz
            val buffer = ShortArray(samples)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
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
    }
}
