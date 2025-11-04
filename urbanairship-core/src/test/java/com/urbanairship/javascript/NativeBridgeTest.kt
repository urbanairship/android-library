/* Copyright Airship and Contributors */
package com.urbanairship.javascript

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionCompletionCallback
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestExtender
import com.urbanairship.actions.ActionRunner
import com.urbanairship.actions.ActionTestUtils
import com.urbanairship.actions.ActionTestUtils.createResult
import com.urbanairship.actions.ActionValue
import com.urbanairship.actions.ActionValue.Companion.wrap
import com.urbanairship.actions.ActionValueException
import com.urbanairship.contacts.Contact
import com.urbanairship.javascript.NativeBridge.CommandDelegate
import com.urbanairship.json.JsonValue
import java.util.concurrent.Executor
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class NativeBridgeTest {

    private val actionRunner: ActionRunner = mockk(relaxed = true)
    private val runRequestExtender = object : ActionRunRequestExtender {
        override fun extend(request: ActionRunRequest): ActionRunRequest {
            return request
        }
    }

    private val javaScriptExecutor: JavaScriptExecutor = mockk(relaxed = true)
    private val commandDelegate: CommandDelegate = mockk(relaxed = true)
    private val contact: Contact = mockk(relaxed = true)

    private val executor = Executor { obj: Runnable -> obj.run() }

    private var nativeBridge = NativeBridge(actionRunner, executor, { contact })


    /**
     * Test run basic actions command
     */
    @Test
    public fun testRunBasicActionsCommand() {
        val url = "uairship://run-basic-actions?action=value&anotherAction=anotherValue"

        assertTrue(
            nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate)
        )

//       Verify that the action runner ran the "action" action
        verify { actionRunner.run(
            name = "action",
            value = wrap("value"),
            situation = Situation.WEB_VIEW_INVOCATION,
            extender = any(),//runRequestExtender,
            callback = any())
        }

        // Verify that the action runner ran the "anotherAction" action
        verify { actionRunner.run(
            name = "anotherAction",
            value = wrap("anotherValue"),
            situation = Situation.WEB_VIEW_INVOCATION,
            extender = runRequestExtender,
            callback = null)
        }

        verify { javaScriptExecutor wasNot Called }
        verify { commandDelegate wasNot Called }
    }

    /**
     * Test run basic actions command with encoded arguments
     */
    @Test
    public fun testRunBasicActionsCommandEncodedParameters() {
        // uairship://run-basic-actions?^+t=addTag&^-t=removeTag
        val encodedUrl = "uairship://run-basic-actions?%5E%2Bt=addTag&%5E-t=removeTag"
        assertTrue(
            nativeBridge.onHandleCommand(
                encodedUrl, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        // Verify that the action runner ran the removeTag action
        verify {
            actionRunner.run(
                "^-t", wrap("removeTag"), Situation.WEB_VIEW_INVOCATION, runRequestExtender, null
            )
        }

        verify {
            actionRunner.run(
                "^+t", wrap("addTag"), Situation.WEB_VIEW_INVOCATION, runRequestExtender, null
            )

        }

        verify { javaScriptExecutor wasNot Called }
        verify { commandDelegate wasNot Called }
    }

    /**
     * Test run actions command with encoded parameters and one bogus encoded parameter aborts running
     * any actions.
     */
    @Test
    public fun testRunActionsCommandEncodedParametersWithBogusParameter() {
        val encodedUrl = "uairship://run-actions?%5E%2Bt=addTag&%5E-t=removeTag&bogus={{{}}}"
        assertTrue(
            nativeBridge.onHandleCommand(
                encodedUrl, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        // Verify action were not executed
        verify { actionRunner wasNot Called }
    }

    /**
     * Test run basic actions command with no action args
     */
    @Test
    public fun testRunBasicActionsCommandNoActionArgs() {
        val url = "uairship://run-basic-actions?addTag"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        // Verify that the action runner ran the addTag action
        verify {
            actionRunner.run(
                "addTag",
                wrap(JsonValue.NULL),
                Situation.WEB_VIEW_INVOCATION,
                runRequestExtender,
                null
            )
        }
    }

    /**
     * Test run basic actions command with no parameters
     */
    @Test
    public fun testRunBasicActionsCommandNoParameters() {
        val url = "uairship://run-basic-actions"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify { actionRunner wasNot Called }
    }

    /**
     * Test run actions command
     */
    @Test
    @Throws(ActionValueException::class)
    public fun testRunActionsCommand() {
        // uairship://run-actions?action={"key":"value"}&anotherAction=["one","two"]
        val url =
            "uairship://run-actions?action=%7B%20%22key%22%3A%22value%22%20%7D&anotherAction=%5B%22one%22%2C%22two%22%5D"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        // Verify the action "action" ran with a map
        val expectedMap = mapOf("key" to "value")

        verify {
            actionRunner.run(
                name = "action",
                value = wrap(expectedMap),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = null)
        }

        // Verify that action "anotherAction" ran with a list
        val expectedList = listOf("one", "two")
        verify {
            actionRunner.run(
                name = "anotherAction",
                value = wrap(expectedList),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = null
            )
        }

        verify { javaScriptExecutor wasNot Called }
        verify { commandDelegate wasNot Called }
    }

    /**
     * Test run actions command with no parameters
     */
    @Test
    public fun testRunActionsCommandNoParameters() {
        // uairship://run-actions?action={"key":"value"}&anotherAction=["one","two"]
        val url = "uairship://run-actions"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify { actionRunner wasNot Called }
    }

    /**
     * Test run actions command with no action args
     */
    @Test
    public fun testRunActionsCommandNoActionArgs() {
        val url = "uairship://run-actions?action"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            actionRunner.run(
                name = "action",
                value = ActionValue(),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = null
            )
        }

        verify { javaScriptExecutor wasNot Called }
        verify { commandDelegate wasNot Called }
    }

    /**
     * Test running an action calls the action completion callback
     */
    @Test
    public fun testRunActionsCallsCompletionCallback() {
        val result = createResult("action_result", null, ActionResult.Status.COMPLETED)
        val arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what")

        val completionCallback: ActionCompletionCallback = mockk(relaxed = true)
        nativeBridge.setActionCompletionCallback(completionCallback)

        every {
            actionRunner.run(
                name = "addTag",
                value = wrap("what"),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = completionCallback
            )
        } answers {
            val callback: ActionCompletionCallback = args[4] as ActionCompletionCallback
            callback.onFinish(arguments, result)
        }

        val url = "uairship://run-basic-actions?addTag=what"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        // Verify our callback was called
        verify { completionCallback.onFinish(arguments, result) }
    }

    /**
     * Test running an action with an invalid arguments payload
     */
    @Test
    public fun testActionCallInvalidArguments() {
        // actionName = {invalid_json}}}
        val url = "uairship://run-action-cb/actionName/%7Binvalid_json%7D%7D%7D/callbackId"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            javaScriptExecutor.executeJavaScript("UAirship.finishAction(new Error(\"Unable to decode arguments payload\"), null, 'callbackId');")
        }
    }

    /**
     * Test running an action that is not found
     */
    @Test
    public fun testActionCallActionNotFound() {
        val result = ActionResult.newEmptyResultWithStatus(ActionResult.Status.ACTION_NOT_FOUND)
        val arguments = ActionTestUtils.createArgs(
            situation = Situation.WEB_VIEW_INVOCATION,
            value = "what")

        every {
            actionRunner.run(
                name = "actionName",
                value = wrap(true),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = any()
            )
        } answers {
            val callback = args[4] as ActionCompletionCallback
            callback.onFinish(arguments, result)
        }

        val url = "uairship://run-action-cb/actionName/true/callbackId"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            javaScriptExecutor.executeJavaScript("UAirship.finishAction(new Error(\"Action actionName not found\"), null, 'callbackId');")
        }
    }

    /**
     * Test running an action that rejects the arguments
     */
    @Test
    public fun testActionCallActionRejectedArguments() {
        val result = ActionResult.newEmptyResultWithStatus(ActionResult.Status.REJECTED_ARGUMENTS)
        val arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what")

        every {
            actionRunner.run(
                name = "actionName",
                value = wrap(true),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = any()
            )
        } answers {
            val callback = args[4] as ActionCompletionCallback
            callback.onFinish(arguments, result)
        }

        val url = "uairship://run-action-cb/actionName/true/callbackId"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            javaScriptExecutor.executeJavaScript("UAirship.finishAction(new Error(\"Action actionName rejected its arguments\"), null, 'callbackId');")
        }
    }

    /**
     * Test running an action that had an execution error
     */
    @Test
    public fun testActionCallActionExecutionError() {
        val result = ActionResult.newErrorResult(Exception("error!"))
        val arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what")

        every {
            actionRunner.run(
                name = "actionName",
                value = wrap(true),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = any()
            )
        } answers {
            val callback = args[4] as ActionCompletionCallback
            callback.onFinish(arguments, result)
        }

        val url = "uairship://run-action-cb/actionName/true/callbackId"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            javaScriptExecutor.executeJavaScript("UAirship.finishAction(new Error(\"error!\"), null, 'callbackId');")
        }
    }

    /**
     * Test running an action with a result
     */
    @Test
    public fun testActionCallAction() {
        val result = ActionResult.newResult(wrap("action_result"))
        val arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what")

        every {
            actionRunner.run(
                name = "actionName",
                value = wrap(true),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = any()
            )
        } answers {
            val callback = args[4] as ActionCompletionCallback
            callback.onFinish(arguments, result)
        }

        val url = "uairship://run-action-cb/actionName/true/callbackId"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            javaScriptExecutor.executeJavaScript("UAirship.finishAction(null, \"action_result\", 'callbackId');")
        }

        verify {
            actionRunner.run(any(), any(), any(), any(), any())
        }
    }

    /**
     * Test setting a action completion callback gets called for completed actions with callbacks
     */
    @Test
    public fun testRunActionCallsCompletionCallback() {
        val result = ActionResult.newResult(wrap("action_result"))
        val arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what")

        val completionCallback: ActionCompletionCallback = mockk(relaxed = true)
        nativeBridge.setActionCompletionCallback(completionCallback)

        every {
            actionRunner.run(
                name = "actionName",
                value = wrap(true),
                situation = Situation.WEB_VIEW_INVOCATION,
                extender = runRequestExtender,
                callback = any()
            )
        } answers {
            val callback = args[4] as ActionCompletionCallback
            callback.onFinish(arguments, result)
        }

        val url = "uairship://run-action-cb/actionName/true/callbackId"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify { completionCallback.onFinish(arguments, result) }
    }

    /**
     * Test close command calls onClose
     */
    @Test
    public fun testCloseCommand() {
        val url = "uairship://close"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify { commandDelegate.onClose() }
    }

    /**
     * Test extending the bridge with a custom call.
     */
    @Test
    public fun testExtendingBridge() {
        val url = "uairship://foo"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify { commandDelegate.onAirshipCommand("foo", Uri.parse(url)) }
    }

    /**
     * Test run multi actions command
     */
    @Test
    public fun testMultiCommand() {
        val url =
            "uairship://multi?uairship%3A%2F%2Frun-basic-actions%3Fadd_tags_action%3Dcoffee%26remove_tags_action%3Dtea&uairship%3A%2F%2Frun-actions%3Fadd_tags_action%3D%255B%2522foo%2522%252C%2522bar%2522%255D&uairship%3A%2F%2Fclose"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            nativeBridge.onHandleCommand(
                "uairship://run-basic-actions?add_tags_action=coffee&remove_tags_action=tea",
                javaScriptExecutor,
                runRequestExtender,
                commandDelegate
            )
        }

        verify {
            nativeBridge.onHandleCommand(
                "uairship://run-actions?add_tags_action=%5B%22foo%22%2C%22bar%22%5D",
                javaScriptExecutor,
                runRequestExtender,
                commandDelegate
            )
        }

        verify {
            nativeBridge.onHandleCommand(
                "uairship://close",
                javaScriptExecutor,
                runRequestExtender,
                commandDelegate
            )
        }
    }

    @Test
    public fun testNamedUserCommand() {
        val url = "uairship://named_user?id=cool"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify { contact.identify("cool") }
    }

    @Test
    public fun testEncodedNamedUserCommand() {
        val url = "uairship://named_user?id=my%2Fname%26%20user"
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            contact.identify("my/name& user")
        }
    }

    @Test
    public fun testNamedUserNullCommand() {
        val url = "uairship://named_user?id="
        assertTrue(
            nativeBridge.onHandleCommand(
                url, javaScriptExecutor, runRequestExtender, commandDelegate
            )
        )

        verify {
            contact.reset()
        }
    }
}
