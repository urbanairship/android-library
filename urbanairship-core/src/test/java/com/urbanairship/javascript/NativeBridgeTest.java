/* Copyright Airship and Contributors */

package com.urbanairship.javascript;

import android.net.Uri;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestExtender;
import com.urbanairship.actions.ActionRunner;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.ActionValueException;
import com.urbanairship.contacts.Contact;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class NativeBridgeTest extends BaseTestCase {

    private ActionRunner actionRunner = mock(ActionRunner.class);
    private ActionRunRequestExtender runRequestExtender = new ActionRunRequestExtender() {
        @NonNull
        @Override
        public ActionRunRequest extend(@NonNull ActionRunRequest request) {
            return request;
        }
    };

    private JavaScriptExecutor javaScriptExecutor = mock(JavaScriptExecutor.class);
    private NativeBridge.CommandDelegate commandDelegate = mock(NativeBridge.CommandDelegate.class);
    private Contact contact = mock(Contact.class);

    private Executor executor = Runnable::run;

    private NativeBridge nativeBridge;

    @Before
    public void setup() {
        TestApplication.getApplication().setContact(contact);
        nativeBridge = new NativeBridge(actionRunner, executor);
    }

    /**
     * Test run basic actions command
     */
    @Test
    public void testRunBasicActionsCommand() {
        String url = "uairship://run-basic-actions?action=value&anotherAction=anotherValue";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify that the action runner ran the "action" action
        verify(actionRunner).run("action", ActionValue.wrap("value"), Action.SITUATION_WEB_VIEW_INVOCATION, runRequestExtender, null);

        // Verify that the action runner ran the "anotherAction" action
        verify(actionRunner).run("anotherAction", ActionValue.wrap("anotherValue"), Action.SITUATION_WEB_VIEW_INVOCATION, runRequestExtender, null);

        verifyNoInteractions(javaScriptExecutor, commandDelegate);
    }

    /**
     * Test run basic actions command with encoded arguments
     */
    @Test
    public void testRunBasicActionsCommandEncodedParameters() {
        // uairship://run-basic-actions?^+t=addTag&^-t=removeTag
        String encodedUrl = "uairship://run-basic-actions?%5E%2Bt=addTag&%5E-t=removeTag";
        assertTrue(nativeBridge.onHandleCommand(encodedUrl, javaScriptExecutor, runRequestExtender, commandDelegate));


        // Verify that the action runner ran the removeTag action
        verify(actionRunner).run("^-t", ActionValue.wrap("removeTag"), Action.SITUATION_WEB_VIEW_INVOCATION, runRequestExtender, null);

        // Verify that the action runner ran the addTag action
        verify(actionRunner).run("^+t", ActionValue.wrap("addTag"), Action.SITUATION_WEB_VIEW_INVOCATION, runRequestExtender, null);

        verifyNoInteractions(javaScriptExecutor, commandDelegate);
    }

    /**
     * Test run actions command with encoded parameters and one bogus encoded parameter aborts running
     * any actions.
     */
    @Test
    public void testRunActionsCommandEncodedParametersWithBogusParameter() {
        String encodedUrl = "uairship://run-actions?%5E%2Bt=addTag&%5E-t=removeTag&bogus={{{}}}";
        assertTrue(nativeBridge.onHandleCommand(encodedUrl, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify action were not executed
        verifyNoInteractions(actionRunner);
    }

    /**
     * Test run basic actions command with no action args
     */
    @Test
    public void testRunBasicActionsCommandNoActionArgs() {
        String url = "uairship://run-basic-actions?addTag";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify that the action runner ran the addTag action
        verify(actionRunner).run("addTag", ActionValue.wrap(JsonValue.NULL), Action.SITUATION_WEB_VIEW_INVOCATION, runRequestExtender, null);
    }

    /**
     * Test run basic actions command with no parameters
     */
    @Test
    public void testRunBasicActionsCommandNoParameters() {
        String url = "uairship://run-basic-actions";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verifyNoInteractions(actionRunner);
    }

    /**
     * Test run actions command
     */
    @Test
    public void testRunActionsCommand() throws ActionValueException {
        // uairship://run-actions?action={"key":"value"}&anotherAction=["one","two"]
        String url = "uairship://run-actions?action=%7B%20%22key%22%3A%22value%22%20%7D&anotherAction=%5B%22one%22%2C%22two%22%5D";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify the action "action" ran with a map
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("key", "value");

        verify(actionRunner).run("action", ActionValue.wrap(expectedMap), Action.SITUATION_WEB_VIEW_INVOCATION, runRequestExtender, null);

        // Verify that action "anotherAction" ran with a list
        List<String> expectedList = new ArrayList<>();
        expectedList.add("one");
        expectedList.add("two");
        verify(actionRunner).run("anotherAction", ActionValue.wrap(expectedList), Action.SITUATION_WEB_VIEW_INVOCATION, runRequestExtender, null);


        verifyNoInteractions(javaScriptExecutor, commandDelegate);
    }

    /**
     * Test run actions command with no parameters
     */
    @Test
    public void testRunActionsCommandNoParameters() {
        // uairship://run-actions?action={"key":"value"}&anotherAction=["one","two"]
        String url = "uairship://run-actions";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verifyNoInteractions(actionRunner);
    }

    /**
     * Test run actions command with no action args
     */
    @Test
    public void testRunActionsCommandNoActionArgs() {
        String url = "uairship://run-actions?action";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));
        verify(actionRunner).run("action", new ActionValue(), Action.SITUATION_WEB_VIEW_INVOCATION, runRequestExtender, null);
        verifyNoInteractions(javaScriptExecutor, commandDelegate);
    }

    /**
     * Test running an action calls the action completion callback
     */
    @Test
    public void testRunActionsCallsCompletionCallback() {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.STATUS_COMPLETED);
        final ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "what");

        ActionCompletionCallback completionCallback = mock(ActionCompletionCallback.class);
        nativeBridge.setActionCompletionCallback(completionCallback);

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionCompletionCallback callback = (ActionCompletionCallback) args[4];
            callback.onFinish(arguments, result);
            return null;

        }).when(actionRunner).run(eq("addTag"), eq(ActionValue.wrap("what")), eq(Action.SITUATION_WEB_VIEW_INVOCATION), eq(runRequestExtender), any(ActionCompletionCallback.class));


        String url = "uairship://run-basic-actions?addTag=what";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify our callback was called
        verify(completionCallback).onFinish(arguments, result);
    }

    /**
     * Test running an action with an invalid arguments payload
     */
    @Test
    public void testActionCallInvalidArguments() {
        // actionName = {invalid_json}}}
        String url = "uairship://run-action-cb/actionName/%7Binvalid_json%7D%7D%7D/callbackId";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(javaScriptExecutor)
                .executeJavaScript("UAirship.finishAction(new Error(\"Unable to decode arguments payload\"), null, 'callbackId');");
    }

    /**
     * Test running an action that is not found
     */
    @Test
    public void testActionCallActionNotFound() {
        final ActionResult result = ActionTestUtils.createResult(null, null, ActionResult.STATUS_ACTION_NOT_FOUND);
        final ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "what");

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionCompletionCallback callback = (ActionCompletionCallback) args[4];
            callback.onFinish(arguments, result);
            return null;

        }).when(actionRunner).run(eq("actionName"), eq(ActionValue.wrap(true)), eq(Action.SITUATION_WEB_VIEW_INVOCATION), eq(runRequestExtender), any(ActionCompletionCallback.class));


        String url = "uairship://run-action-cb/actionName/true/callbackId";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(javaScriptExecutor)
                .executeJavaScript("UAirship.finishAction(new Error(\"Action actionName not found\"), null, 'callbackId');");
    }

    /**
     * Test running an action that rejects the arguments
     */
    @Test
    public void testActionCallActionRejectedArguments() {
        final ActionResult result = ActionTestUtils.createResult(null, null, ActionResult.STATUS_REJECTED_ARGUMENTS);
        final ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "what");

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionCompletionCallback callback = (ActionCompletionCallback) args[4];
            callback.onFinish(arguments, result);
            return null;

        }).when(actionRunner).run(eq("actionName"), eq(ActionValue.wrap(true)), eq(Action.SITUATION_WEB_VIEW_INVOCATION), eq(runRequestExtender), any(ActionCompletionCallback.class));


        String url = "uairship://run-action-cb/actionName/true/callbackId";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(javaScriptExecutor).executeJavaScript("UAirship.finishAction(new Error(\"Action actionName rejected its arguments\"), null, 'callbackId');");
    }

    /**
     * Test running an action that had an execution error
     */
    @Test
    public void testActionCallActionExecutionError() {
        final ActionResult result = ActionTestUtils.createResult(null, new Exception("error!"), ActionResult.STATUS_EXECUTION_ERROR);
        final ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "what");

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionCompletionCallback callback = (ActionCompletionCallback) args[4];
            callback.onFinish(arguments, result);
            return null;

        }).when(actionRunner).run(eq("actionName"), eq(ActionValue.wrap(true)), eq(Action.SITUATION_WEB_VIEW_INVOCATION), eq(runRequestExtender), any());


        String url = "uairship://run-action-cb/actionName/true/callbackId";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(javaScriptExecutor)
                .executeJavaScript("UAirship.finishAction(new Error(\"error!\"), null, 'callbackId');");
    }

    /**
     * Test running an action with a result
     */
    @Test
    public void testActionCallAction() {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.STATUS_COMPLETED);
        final ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "what");

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionCompletionCallback callback = (ActionCompletionCallback) args[4];
            callback.onFinish(arguments, result);
            return null;
        }).when(actionRunner).run(eq("actionName"), eq(ActionValue.wrap(true)), eq(Action.SITUATION_WEB_VIEW_INVOCATION), eq(runRequestExtender), any());


        String url = "uairship://run-action-cb/actionName/true/callbackId";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(javaScriptExecutor)
                .executeJavaScript("UAirship.finishAction(null, \"action_result\", 'callbackId');");

        // Verify the action request
        verify(actionRunner).run(eq("actionName"), eq(ActionValue.wrap(true)), eq(Action.SITUATION_WEB_VIEW_INVOCATION), eq(runRequestExtender), any(ActionCompletionCallback.class));
    }

    /**
     * Test setting a action completion callback gets called for completed actions with callbacks
     */
    @Test
    public void testRunActionCallsCompletionCallback() {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.STATUS_COMPLETED);
        final ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "what");

        ActionCompletionCallback completionCallback = mock(ActionCompletionCallback.class);
        nativeBridge.setActionCompletionCallback(completionCallback);

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionCompletionCallback callback = (ActionCompletionCallback) args[4];
            callback.onFinish(arguments, result);
            return null;

        }).when(actionRunner).run(eq("actionName"), eq(ActionValue.wrap(true)), eq(Action.SITUATION_WEB_VIEW_INVOCATION), eq(runRequestExtender), any(ActionCompletionCallback.class));


        String url = "uairship://run-action-cb/actionName/true/callbackId";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify our callback was called
        verify(completionCallback).onFinish(arguments, result);
    }


    /**
     * Test close command calls onClose
     */
    @Test
    public void testCloseCommand() {
        String url = "uairship://close";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(commandDelegate).onClose();
    }

    /**
     * Test extending the bridge with a custom call.
     */
    @Test
    public void testExtendingBridge() {
        String url = "uairship://foo";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));
        verify(commandDelegate).onAirshipCommand("foo", Uri.parse(url));
    }

    /**
     * Test run multi actions command
     */
    @Test
    public void testMultiCommand() {
        NativeBridge spyNativeBridge = Mockito.spy(nativeBridge);

        String url = "uairship://multi?uairship%3A%2F%2Frun-basic-actions%3Fadd_tags_action%3Dcoffee%26remove_tags_action%3Dtea&uairship%3A%2F%2Frun-actions%3Fadd_tags_action%3D%255B%2522foo%2522%252C%2522bar%2522%255D&uairship%3A%2F%2Fclose";
        assertTrue(spyNativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(spyNativeBridge).onHandleCommand("uairship://run-basic-actions?add_tags_action=coffee&remove_tags_action=tea", javaScriptExecutor, runRequestExtender, commandDelegate);
        verify(spyNativeBridge).onHandleCommand("uairship://run-actions?add_tags_action=%5B%22foo%22%2C%22bar%22%5D", javaScriptExecutor, runRequestExtender, commandDelegate);
        verify(spyNativeBridge).onHandleCommand("uairship://close", javaScriptExecutor, runRequestExtender, commandDelegate);
    }

    @Test
    public void testNamedUserCommand() {
        String url = "uairship://named_user?id=cool";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));
        verify(contact).identify("cool");
    }

    @Test
    public void testEncodedNamedUserCommand() {
        String url = "uairship://named_user?id=my%2Fname%26%20user";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));
        verify(contact).identify("my/name& user");
    }

    @Test
    public void testNamedUserNullCommand() {
        String url = "uairship://named_user?id=";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));
        verify(contact).reset();
    }
}
