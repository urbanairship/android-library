/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import android.view.Choreographer

/** A [NextFrame] whose frames run only when a test calls [advance]. */
internal class FakeNextFrame : NextFrame {
    private val callbacks = mutableListOf<Choreographer.FrameCallback>()

    val isEmpty: Boolean get() = callbacks.isEmpty()

    override fun post(callback: Choreographer.FrameCallback) {
        callbacks.add(callback)
    }

    override fun remove(callback: Choreographer.FrameCallback) {
        callbacks.remove(callback)
    }

    /** Runs the callbacks posted before this frame; ones they post wait for the next. */
    fun advance() {
        val due = callbacks.toList()
        callbacks.clear()
        due.forEach { it.doFrame(0) }
    }
}
