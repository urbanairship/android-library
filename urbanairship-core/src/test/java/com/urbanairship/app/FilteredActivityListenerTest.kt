package com.urbanairship.app

import android.app.Activity
import android.os.Bundle
import com.urbanairship.Predicate
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

public class FilteredActivityListenerTest {

    private val mockPredicate: Predicate<Activity> = mockk()
    private val mockListener: ActivityListener = mockk(relaxed = true)
    private var filteredActivityListener = FilteredActivityListener(mockListener, mockPredicate)

    @Test
    public fun testAcceptActivity() {
        val activity = Activity()
        val bundle = Bundle()

        every { mockPredicate.apply(any()) } returns true

        filteredActivityListener.onActivityCreated(activity, bundle)
        verify(exactly = 1) { mockListener.onActivityCreated(activity, bundle) }

        filteredActivityListener.onActivityStarted(activity)
        verify(exactly = 1) { mockListener.onActivityStarted(activity) }

        filteredActivityListener.onActivityResumed(activity)
        verify(exactly = 1) { mockListener.onActivityResumed(activity) }

        filteredActivityListener.onActivityPaused(activity)
        verify(exactly = 1) { mockListener.onActivityPaused(activity) }

        filteredActivityListener.onActivityStopped(activity)
        verify(exactly = 1) { mockListener.onActivityStopped(activity) }

        filteredActivityListener.onActivitySaveInstanceState(activity, bundle)
        verify { mockListener.onActivitySaveInstanceState(activity, bundle) }

        filteredActivityListener.onActivityDestroyed(activity)
        verify(exactly = 1) { mockListener.onActivityDestroyed(activity) }
    }

    @Test
    public fun testRejectActivity() {
        val activity = Activity()
        val bundle = Bundle()

        every { mockPredicate.apply(any()) } returns false

        filteredActivityListener.onActivityCreated(activity, bundle)
        filteredActivityListener.onActivityStarted(activity)
        filteredActivityListener.onActivityResumed(activity)
        filteredActivityListener.onActivityPaused(activity)
        filteredActivityListener.onActivityStopped(activity)
        filteredActivityListener.onActivitySaveInstanceState(activity, bundle)
        filteredActivityListener.onActivityDestroyed(activity)

        verify { mockListener wasNot Called }
    }
}
