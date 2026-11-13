package com.prism.music.playback

import androidx.media3.common.AdPlaybackState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi

/**
 * The one-item timeline the media session shows while the persistent controls wait for something to play: [item] has
 * metadata only (no URI, nothing to prepare). Android's media notification ignores an empty timeline, so the card needs
 * a window even though there is no song.
 */
@OptIn(UnstableApi::class)
internal class PlaceholderTimeline(private val item: MediaItem, private val durationUs: Long = C.TIME_UNSET) : Timeline() {

    override fun getWindowCount(): Int = 1

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        require(windowIndex == 0) { "PlaceholderTimeline has one window" }
        window.set(
            Window.SINGLE_WINDOW_UID, item, /* manifest= */ null,
            C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET,
            /* isSeekable= */ false, /* isDynamic= */ false, /* liveConfiguration= */ null,
            /* defaultPositionUs= */ 0, /* durationUs= */ durationUs,
            /* firstPeriodIndex= */ 0, /* lastPeriodIndex= */ 0, /* positionInFirstPeriodUs= */ 0,
        )
        return window
    }

    override fun getPeriodCount(): Int = 1

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        require(periodIndex == 0) { "PlaceholderTimeline has one period" }
        period.set(
            /* id= */ if (setIds) Window.SINGLE_WINDOW_UID else null,
            /* uid= */ Window.SINGLE_WINDOW_UID,
            /* windowIndex= */ 0,
            /* durationUs= */ durationUs,
            /* positionInWindowUs= */ 0,
            AdPlaybackState.NONE,
            /* isPlaceholder= */ false,
        )
        return period
    }

    override fun getIndexOfPeriod(uid: Any): Int = if (uid == Window.SINGLE_WINDOW_UID) 0 else C.INDEX_UNSET

    override fun getUidOfPeriod(periodIndex: Int): Any {
        require(periodIndex == 0) { "PlaceholderTimeline has one period" }
        return Window.SINGLE_WINDOW_UID
    }
}
