package com.farel.animecursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class CursorAccessibilityService : AccessibilityService() {

    companion object {
        var instance: CursorAccessibilityService? = null

        fun performClick(x: Float, y: Float) {
            instance?.doClick(x, y)
        }

        fun performDoubleClick(x: Float, y: Float) {
            instance?.doDoubleClick(x, y)
        }

        fun performDrag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
            instance?.doDrag(fromX, fromY, toX, toY)
        }

        fun performLongPress(x: Float, y: Float) {
            instance?.doLongPress(x, y)
        }

        fun performScroll(x: Float, y: Float, direction: Int) {
            instance?.doScroll(x, y, direction)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun doClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun doDoubleClick(x: Float, y: Float) {
        val path1 = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, 100)
        val path2 = Path().apply { moveTo(x, y) }
        val stroke2 = GestureDescription.StrokeDescription(path2, 250, 100)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun doDrag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 500)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun doLongPress(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1000)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun doScroll(x: Float, y: Float, direction: Int) {
        // direction: 1=down, -1=up
        val dy = direction * 300f
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y - dy)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
