package com.aicontrol.launcher.ui

import kotlin.math.abs
import kotlin.math.max

internal enum class WorkspaceSwipeDirection { LEFT, RIGHT }

/** Platform-neutral gesture classification so threshold and direction rules remain JVM-testable. */
internal class WorkspaceSwipeGesture(
    touchSlopPx: Int,
    density: Float,
    minimumDistanceDp: Float = 40f,
    private val horizontalDominance: Float = 1.2f
) {
    val thresholdPx: Float = max(touchSlopPx.coerceAtLeast(1) * 2f, density.coerceAtLeast(0.1f) * minimumDistanceDp)

    private var downX = 0f
    private var downY = 0f
    private var active = false
    private var recognized: WorkspaceSwipeDirection? = null

    fun begin(x: Float, y: Float) {
        downX = x
        downY = y
        active = true
        recognized = null
    }

    /** Returns a direction only after a decisive, sufficiently long horizontal movement. */
    fun move(x: Float, y: Float): WorkspaceSwipeDirection? {
        if (!active) return null
        recognized?.let { return it }
        val dx = x - downX
        val dy = y - downY
        if (abs(dx) < thresholdPx || abs(dx) <= abs(dy) * horizontalDominance) return null
        return (if (dx > 0f) WorkspaceSwipeDirection.RIGHT else WorkspaceSwipeDirection.LEFT)
            .also { recognized = it }
    }

    /** Completes a gesture, checking the final coordinates and always resetting its state. */
    fun finish(x: Float, y: Float): WorkspaceSwipeDirection? {
        val direction = move(x, y)
        cancel()
        return direction
    }

    fun cancel() {
        active = false
        recognized = null
    }
}
