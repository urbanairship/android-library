/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.actions.ActionValue.Companion.wrap
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ActionTest {

    /**
     * Test running an action calls onStart, perform, and onFinish
     * with the expected inputs.
     */
    @Test
    public fun testRun() {
        val metadata = bundleOf("metadata_key" to "metadata_value")

        val expectedResult = newResult(wrap("result"))
        val originalArguments =
            ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "value", metadata)

        // Create a test action that verifies the result, handle, and arguments
        // in each method
        val action: TestAction = object : TestAction(true, expectedResult) {
            override fun perform(arguments: ActionArguments): ActionResult {
                assertEquals(
                    "Action arguments is a different instance then the passed in arguments",
                    arguments,
                    originalArguments
                )

                assertEquals(
                    "Bundle does not contain the passed in metadata",
                    "metadata_value",
                    arguments.metadata.getString("metadata_key")
                )

                return super.perform(arguments)
            }

            override fun onStart(arguments: ActionArguments) {
                super.onStart(arguments)

                assertEquals(
                    "Bundle does not contain the passed in metadata",
                    arguments.metadata.getString("metadata_key"),
                    "metadata_value"
                )

                assertEquals(
                    "Action arguments is a different instance then the passed in arguments",
                    arguments,
                    originalArguments
                )
            }

            override fun onFinish(arguments: ActionArguments, result: ActionResult) {
                super.onFinish(arguments, result)

                assertEquals(
                    "Bundle does not contain the passed in metadata",
                    arguments.metadata.getString("metadata_key"),
                    "metadata_value"
                )

                assertEquals(
                    "Action arguments is a different instance then the passed in arguments",
                    arguments,
                    originalArguments
                )

                assertEquals(
                    "Action result is a different instance then then the returned results",
                    result,
                    expectedResult
                )
            }
        }

        // Verify the result is passed back properly
        val results = action.run(originalArguments)

        assertEquals("Action result is unexpected", expectedResult, results)
        assertEquals(
            "Result should have COMPLETED status",
            ActionResult.Status.COMPLETED,
            expectedResult.status
        )

        // Verify the methods were called
        assertTrue("Action.onStart is not being called", action.onStartCalled)
        assertTrue("Action.perform is not being called", action.performCalled)
        assertTrue("Action.onFinish is not being called", action.onFinishCalled)
    }

    /**
     * Test running an action that does not
     * accept the arguments.
     */
    @Test
    public fun testRunBadArgs() {
        val performResult = newResult(wrap("result"))
        val action = TestAction(false, performResult)

        val badArgsResult =
            action.run(ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "value"))

        assertTrue(
            "Does not accept arguments should return a 'null' result value",
            badArgsResult.value.isNull
        )

        assertEquals(
            "Result should have an rejected arguemnts status",
            ActionResult.Status.REJECTED_ARGUMENTS,
            badArgsResult.status
        )

        assertFalse(
            "Does not accept arguments should not call any of the actions perform methods.",
            action.onStartCalled
        )
        assertFalse(
            "Does not accept arguments should not call any of the actions perform methods.",
            action.performCalled
        )
        assertFalse(
            "Does not accept arguments should not call any of the actions perform methods.",
            action.onFinishCalled
        )
    }

    /**
     * Test unexpected runtime exceptions being thrown when an action is performing
     * returns a result with the exception as the value.
     */
    @Test
    public fun testRunPerformException() {
        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "value")
        val performResult = newResult(wrap("result"))

        val exception = IllegalStateException("oh no!")

        val action: TestAction = object : TestAction(true, performResult) {
            override fun perform(arguments: ActionArguments): ActionResult {
                super.perform(arguments)
                throw exception
            }
        }

        val result = action.run(args)
        when(result) {
            is ActionResult.Error -> assertEquals("Result should pass back exception as the value", exception, result.exception)
            else -> fail("Result should be an error")
        }


        assertTrue("Result should be 'null'", result.value.isNull)
        assertEquals(
            "Result should have an error status", ActionResult.Status.EXECUTION_ERROR, result.status
        )

        assertTrue("Action.onStart is not being called", action.onStartCalled)
        assertTrue("Action.perform is not being called", action.performCalled)
        assertFalse(
            "Action.onFinish should not be called if a runtime exception is thrown",
            action.onFinishCalled
        )
    }

    /**
     * Test that when perform returns null, an empty result is generated and returned.
     */
    @Test
    public fun testRunPerformNullResult() {
        val args = ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "value")

        val action = TestAction(true, newEmptyResult())

        val result = action.run(args)
        assertNotNull("Result should never be null", result)
        assertTrue("Result should be 'null'", result.value.isNull)
        assertEquals(
            "Result should have the COMPLETED status", ActionResult.Status.COMPLETED, result.status
        )
    }
}
