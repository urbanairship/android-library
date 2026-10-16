/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicReference

/**
 * Root cause of Ryan's on-device report ("works if the image is already cached; if not, it never
 * updates"): `View.invalidate()` requires the calling thread to be the UI thread and silently
 * no-ops otherwise -- it doesn't throw, it just does nothing. A cached/synchronous image load
 * calls back on whatever thread asked for it (always the UI thread here), so it happened to work;
 * a genuinely async load's completion callback isn't guaranteed to land on the UI thread, and
 * `BackgroundsPainter`'s image-loaded callback now uses `postInvalidate()` for exactly this
 * reason -- see the seven `BackgroundsPainter(context, viewEnvironment) { postInvalidate() }`
 * host-view call sites.
 *
 * A pixel/screenshot-based test can't actually discriminate this bug: `View.draw()` always paints
 * whatever the view's *current* state is, whether or not `invalidate()` ever ran -- the bug isn't
 * "stale content gets painted," it's "nothing ever schedules a repaint at all," so the screen just
 * never updates on its own. The only thing worth asserting is the mechanism itself: does calling
 * this from a genuine background thread get a redraw *scheduled* on the main thread. Robolectric's
 * `invalidate()` shadow doesn't reject an off-thread call the way real Android's `ViewRootImpl`
 * does, so this checks the concrete, thread-safe alternative directly against a real attached
 * `ViewRootImpl`, exercised from a real, joined background `Thread` -- not the test's own thread.
 */
@RunWith(RobolectricTestRunner::class)
public class LinearLayoutBackgroundsCrossThreadTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    public fun postInvalidateFromABackgroundThreadSchedulesWorkOnTheMainThread() {
        val view = View(context)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).let { controller ->
            controller.get().setContentView(view)
            controller.setup().get()
        }
        // The initial attach/setup already ran one traversal; drain it so the scheduler below
        // starts from a clean baseline.
        shadowOf(Looper.getMainLooper()).idle()

        val error = AtomicReference<Throwable?>(null)
        val thread = Thread {
            try {
                // The exact call BackgroundsPainter's image-loaded callback makes.
                view.postInvalidate()
            } catch (t: Throwable) {
                error.set(t)
            }
        }
        thread.start()
        thread.join(2000)

        assertNull("postInvalidate() must not throw when called off the UI thread", error.get())
        assertTrue(
            "postInvalidate() from a background thread must schedule work on the main thread's " +
                "queue -- if nothing is scheduled, the view has no way to ever find out it needs " +
                "to redraw",
            !shadowOf(Looper.getMainLooper()).isIdle
        )

        activity.finish()
    }

    @Test
    public fun invalidateFromABackgroundThreadDoesNotThrowButRobolectricDoesNotEnforceTheRealContract() {
        // Documents why this file doesn't just assert "invalidate() fails, postInvalidate()
        // works" by reverting BackgroundsPainter's fix and re-running: Robolectric's shadow of
        // View.invalidate() doesn't replicate real Android's UI-thread check (ViewRootImpl.
        // checkThread(), which either throws or silently drops an off-thread call depending on
        // the code path), so a same-thread-agnostic assertion here would pass either way and
        // prove nothing. The property that IS verifiable and IS what BackgroundsPainter depends
        // on is proven above: postInvalidate() is unconditionally safe and unconditionally
        // schedules something, from any thread, which is exactly the guarantee invalidate()
        // does not make.
        val view = View(context)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).let { controller ->
            controller.get().setContentView(view)
            controller.setup().get()
        }
        shadowOf(Looper.getMainLooper()).idle()

        val error = AtomicReference<Throwable?>(null)
        val thread = Thread {
            try {
                view.invalidate()
            } catch (t: Throwable) {
                error.set(t)
            }
        }
        thread.start()
        thread.join(2000)

        assertNull("Robolectric's invalidate() shadow doesn't throw off-thread either", error.get())
        activity.finish()
    }
}
