package com.urbanairship.preferencecenter.testing

import android.view.View
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import java.util.concurrent.TimeoutException
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.StringDescription

fun waitForView(viewMatcher: Matcher<View>, timeout: Long = 5_000, isDisplayed: Boolean = true): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return Matchers.any(View::class.java)
        }

        override fun getDescription(): String {
            val description = StringDescription()
            viewMatcher.describeTo(description)
            val condition = if (isDisplayed) "displayed" else "hidden"
            return "wait for view <$description> to be $condition for $timeout ms."
        }

        override fun perform(uiController: UiController, view: View?) {
            uiController.loopMainThreadUntilIdle()

            val endTime = System.currentTimeMillis() + timeout
            do {
                val isViewDisplayed = TreeIterables.breadthFirstViewTraversal(view)
                    .any { viewMatcher.matches(it) && isDisplayed().matches(it) }
                if (isViewDisplayed == isDisplayed) return
                uiController.loopMainThreadForAtLeast(10)
            } while (System.currentTimeMillis() < endTime)

            // Timeout :(
            throw PerformException.Builder()
                .withActionDescription(description)
                .withViewDescription(HumanReadables.describe(view))
                .withCause(TimeoutException("Timed out waiting for view!"))
                .build()
        }
    }
}
