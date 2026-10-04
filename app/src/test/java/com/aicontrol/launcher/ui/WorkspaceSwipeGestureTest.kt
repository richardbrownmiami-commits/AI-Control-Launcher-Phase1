package com.aicontrol.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceSwipeGestureTest {
    @Test
    fun recognizesLeftAndRightAfterScaledThreshold() {
        val gesture = WorkspaceSwipeGesture(touchSlopPx = 8, density = 2f)
        gesture.begin(100f, 100f)
        assertNull(gesture.move(21f, 100f))
        assertEquals(WorkspaceSwipeDirection.LEFT, gesture.finish(20f, 100f))
    }

    @Test
    fun reportsBothDirectionsAndResetsAfterCompletion() {
        val gesture = WorkspaceSwipeGesture(touchSlopPx = 8, density = 1f)
        gesture.begin(100f, 30f)
        assertNull(gesture.move(61f, 31f))
        assertEquals(WorkspaceSwipeDirection.LEFT, gesture.move(59f, 31f))
        assertEquals(WorkspaceSwipeDirection.LEFT, gesture.finish(50f, 31f))

        gesture.begin(50f, 31f)
        assertEquals(WorkspaceSwipeDirection.RIGHT, gesture.finish(100f, 31f))
    }

    @Test
    fun preservesVerticalAndVerticallyDominantDiagonalGestures() {
        val gesture = WorkspaceSwipeGesture(touchSlopPx = 8, density = 1f)
        gesture.begin(0f, 0f)
        assertNull(gesture.move(3f, 80f))
        assertNull(gesture.finish(12f, 60f))

        gesture.begin(0f, 0f)
        assertNull(gesture.move(80f, 70f))
        assertNull(gesture.finish(78f, 70f))
    }

    @Test
    fun thresholdUsesBothPlatformTouchSlopAndDisplayDensity() {
        assertEquals(40f, WorkspaceSwipeGesture(touchSlopPx = 8, density = 1f).thresholdPx, 0f)
        assertEquals(120f, WorkspaceSwipeGesture(touchSlopPx = 30, density = 3f).thresholdPx, 0f)

        val gesture = WorkspaceSwipeGesture(touchSlopPx = 8, density = 1f)
        gesture.begin(0f, 0f)
        assertNull(gesture.move(39.9f, 0f))
        assertEquals(WorkspaceSwipeDirection.RIGHT, gesture.move(40f, 0f))
    }

    @Test
    fun cancelClearsAnyRecognizedDirection() {
        val gesture = WorkspaceSwipeGesture(touchSlopPx = 8, density = 1f)
        gesture.begin(0f, 0f)
        assertEquals(WorkspaceSwipeDirection.LEFT, gesture.move(-50f, 0f))
        gesture.cancel()
        assertNull(gesture.finish(-60f, 0f))
    }
}
