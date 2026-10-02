/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import android.view.Choreographer

/** Schedules a callback for the start of the next frame. */
internal interface NextFrame {
    fun post(callback: Choreographer.FrameCallback)
    fun remove(callback: Choreographer.FrameCallback)

    /** The main thread's [Choreographer]. */
    object Main : NextFrame {
        override fun post(callback: Choreographer.FrameCallback) =
            Choreographer.getInstance().postFrameCallback(callback)

        override fun remove(callback: Choreographer.FrameCallback) =
            Choreographer.getInstance().removeFrameCallback(callback)
    }
}
