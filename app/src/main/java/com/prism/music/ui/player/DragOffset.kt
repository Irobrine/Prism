package com.prism.music.ui.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A drag's offset. The finger writes [value] straight through ([set]); when it lifts, [settle] eases the offset home.
 *
 * A drag used to send every pointer delta through `scope.launch { animated.snapTo(…) }`: a coroutine per event, whose
 * updates could land out of order on a quick swipe, so the card lagged a frame or jumped back mid-drag. A plain state
 * written where the finger is removes that, and the easing jobs are kept to one, cancelled when the next drag starts.
 */
internal class DragOffset {
    var value by mutableFloatStateOf(0f)
        private set
    private var settling: Job? = null

    /** The finger's own position, at once. */
    fun set(v: Float) {
        cancel()
        value = v
    }

    /** Back to a place without animating (once it has fallen out of sight). */
    fun reset(v: Float) {
        value = v
    }

    fun cancel() {
        settling?.cancel()
        settling = null
    }

    fun settle(
        scope: CoroutineScope,
        to: Float,
        spec: AnimationSpec<Float> = tween(220, easing = com.prism.music.ui.theme.PrismTokens.Motion.Ease),
        then: (suspend () -> Unit)? = null,
    ) {
        cancel()
        settling = scope.launch {
            animate(value, to, animationSpec = spec) { v, _ -> value = v }
            then?.invoke()
        }
    }
}
