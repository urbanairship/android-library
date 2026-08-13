/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.AirshipBannerViewManager
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.json.JsonValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
public class BannerLayoutTest {

    private val activity: ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private val viewInstanceId = UUID.randomUUID().toString()

    private val reportedEvents = mutableListOf<ReportingEvent>()
    private val listener: ThomasListenerInterface = mockk(relaxed = true) {
        every { onReportingEvent(capture(reportedEvents)) } just Runs
    }
    private val activityMonitor: ActivityMonitor = mockk(relaxed = true)
    private val actionRunner: ThomasActionRunner = mockk(relaxed = true)
    private val bannerViewManager: AirshipBannerViewManager = mockk(relaxed = true)

    @Test
    public fun testDismissFromUserReportsUserDismissed() {
        val layout = bannerLayout()
        assertNotNull(layout.makeView())

        layout.dismissFromUser()

        val dismiss = reportedEvents.filterIsInstance<ReportingEvent.Dismiss>().single()
        assertEquals(ReportingEvent.DismissData.UserDismissed, dismiss.data)
        assertTrue(dismiss.displayTime.inWholeMilliseconds >= 0)
        verify(exactly = 1) { listener.onDismiss(cancel = false) }
        verify(exactly = 1) { bannerViewManager.dismiss(viewInstanceId) }
    }

    @Test
    public fun testDismissFromTimeoutReportsTimedOut() {
        val layout = bannerLayout()
        assertNotNull(layout.makeView())

        layout.dismissFromTimeout()

        val dismiss = reportedEvents.filterIsInstance<ReportingEvent.Dismiss>().single()
        assertEquals(ReportingEvent.DismissData.TimedOut, dismiss.data)
        assertTrue(dismiss.displayTime.inWholeMilliseconds >= 0)
        verify(exactly = 1) { listener.onDismiss(cancel = false) }
        verify(exactly = 1) { bannerViewManager.dismiss(viewInstanceId) }
    }

    @Test
    public fun testDismissFromViewFailureResolvesWithoutReporting() {
        val layout = bannerLayout()

        layout.dismissFromViewFailure()

        // The display request is resolved as cancelled via the external listener, but no
        // dismiss resolution event is reported, since the banner was never displayed.
        verify(exactly = 1) { listener.onDismiss(cancel = true) }
        assertTrue(reportedEvents.filterIsInstance<ReportingEvent.Dismiss>().isEmpty())
        verify(exactly = 1) { bannerViewManager.dismiss(viewInstanceId) }

        // Further dismiss calls after the banner is dismissed are no-ops.
        layout.dismissFromUser()
        assertTrue(reportedEvents.filterIsInstance<ReportingEvent.Dismiss>().isEmpty())
        verify(exactly = 1) { bannerViewManager.dismiss(viewInstanceId) }
    }

    @Test
    public fun testMakeViewReturnsNullForNonActivityContext() {
        val layout = bannerLayout(context = ApplicationProvider.getApplicationContext())
        assertNull(layout.makeView())
    }

    @Test
    public fun testMakeViewReturnsNullForNonLifecycleOwnerActivity() {
        val plainActivity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val layout = bannerLayout(context = plainActivity)
        assertNull(layout.makeView())
    }

    @Test
    public fun testNonBannerPresentationThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            bannerLayout(layoutJson = MODAL_LAYOUT)
        }
    }

    @Test
    public fun testMakeViewTwiceRegistersSingleApplicationListener() {
        val added = mutableListOf<ApplicationListener>()
        val removed = mutableListOf<ApplicationListener>()
        every { activityMonitor.addApplicationListener(capture(added)) } just Runs
        every { activityMonitor.removeApplicationListener(capture(removed)) } just Runs

        val layout = bannerLayout()
        assertNotNull(layout.makeView())
        assertNotNull(layout.makeView())

        // The second makeView call removes the listener added by the first, so that repeated
        // calls don't stack duplicate listeners.
        assertEquals(2, added.size)
        assertEquals(1, removed.size)
        assertSame(added[0], removed[0])
    }

    private fun bannerLayout(
        context: Context = activity,
        layoutJson: String = BANNER_LAYOUT
    ): BannerLayout {
        val payload = LayoutInfo(JsonValue.parseString(layoutJson).optMap())
        val args = DisplayArgs(
            payload = payload,
            listener = listener,
            inAppActivityMonitor = activityMonitor,
            actionRunner = actionRunner
        )
        return BannerLayout(context, viewInstanceId, args, bannerViewManager)
    }

    private companion object {
        private const val BANNER_LAYOUT = """
            {
              "version": 1,
              "presentation": {
                "type": "banner",
                "default_placement": {
                  "size": { "width": "100%", "height": "auto" },
                  "position": { "horizontal": "center", "vertical": "bottom" }
                }
              },
              "view": {
                "type": "label",
                "text": "Test banner",
                "text_appearance": {
                  "font_size": 14,
                  "alignment": "center",
                  "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 1 } }
                }
              }
            }
        """

        private const val MODAL_LAYOUT = """
            {
              "version": 1,
              "presentation": {
                "type": "modal",
                "default_placement": {
                  "size": { "width": "100%", "height": "100%" },
                  "position": { "horizontal": "center", "vertical": "center" }
                }
              },
              "view": {
                "type": "label",
                "text": "Test modal",
                "text_appearance": {
                  "font_size": 14,
                  "alignment": "center",
                  "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 1 } }
                }
              }
            }
        """
    }
}
