/* Copyright Airship and Contributors */

package com.urbanairship.javascript;

import android.net.Uri;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestExtender;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.ActionValueException;
import com.urbanairship.actions.StubbedActionRunRequest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class NativeBridgeTest extends BaseTestCase {

    private NativeBridge nativeBridge;

    private ActionRunRequestFactory runRequestFactory;
    private ActionRunRequestExtender runRequestExtender;
    private JavaScriptExecutor javaScriptExecutor;
    private NativeBridge.CommandDelegate commandDelegate;

    private Executor executor = new Executor() {
        @Override
        public void execute(Runnable runnable) {
            runnable.run();
        }
    };

    @Before
    public void setup() {
        runRequestFactory = mock(ActionRunRequestFactory.class);
        runRequestExtender = mock(ActionRunRequestExtender.class);
        when(runRequestExtender.extend(any(ActionRunRequest.class))).then(new Answer<ActionRunRequest>() {
            @Override
            public ActionRunRequest answer(InvocationOnMock invocation) throws Throwable {
                return (ActionRunRequest) invocation.getArgument(0);
            }
        });

        javaScriptExecutor = mock(JavaScriptExecutor.class);
        commandDelegate = mock(NativeBridge.CommandDelegate.class);

        nativeBridge = new NativeBridge(runRequestFactory, executor);
    }

    /**
     * Test run basic actions command
     */
    @Test
    public void testRunBasicActionsCommand() {
        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("action")).thenReturn(actionRunRequest);

        ActionRunRequest anotherActionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("anotherAction")).thenReturn(anotherActionRunRequest);

        String url = "uairship://run-basic-actions?action=value&anotherAction=anotherValue";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify that the action runner ran the "action" action
        verify(actionRunRequest).setValue(ActionValue.wrap("value"));
        verify(actionRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(actionRunRequest).run(any(ActionCompletionCallback.class));

        // Verify that the action runner ran the "anotherAction" action
        verify(anotherActionRunRequest).setValue(eq(ActionValue.wrap("anotherValue")));
        verify(anotherActionRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(anotherActionRunRequest).run(any(ActionCompletionCallback.class));

        verify(runRequestExtender).extend(actionRunRequest);
        verify(runRequestExtender).extend(anotherActionRunRequest);

        verifyZeroInteractions(javaScriptExecutor, commandDelegate);
    }

    /**
     * Test run basic actions command with encoded arguments
     */
    @Test
    public void testRunBasicActionsCommandEncodedParameters() {
        ActionRunRequest removeTagRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        ActionRunRequest addTagRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("^-t")).thenReturn(removeTagRunRequest);
        when(runRequestFactory.createActionRequest("^+t")).thenReturn(addTagRunRequest);

        // uairship://run-basic-actions?^+t=addTag&^-t=removeTag
        String encodedUrl = "uairship://run-basic-actions?%5E%2Bt=addTag&%5E-t=removeTag";
        assertTrue(nativeBridge.onHandleCommand(encodedUrl, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify that the action runner ran the removeTag action
        verify(removeTagRunRequest).setValue(ActionValue.wrap("removeTag"));
        verify(removeTagRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(removeTagRunRequest).run(any(ActionCompletionCallback.class));

        // Verify that the action runner ran the addTag action
        verify(addTagRunRequest).setValue(ActionValue.wrap("addTag"));
        verify(addTagRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(addTagRunRequest).run(any(ActionCompletionCallback.class));

        verify(runRequestExtender).extend(removeTagRunRequest);
        verify(runRequestExtender).extend(addTagRunRequest);

        verifyZeroInteractions(javaScriptExecutor, commandDelegate);
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
        verify(runRequestFactory, never()).createActionRequest(Mockito.anyString());
    }

    /**
     * Test run basic actions command with no action args
     */
    @Test
    public void testRunBasicActionsCommandNoActionArgs() {
        ActionRunRequest addTagRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("addTag")).thenReturn(addTagRunRequest);

        String url = "uairship://run-basic-actions?addTag";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify that the action runner ran the addTag action
        verify(addTagRunRequest).setValue(new ActionValue());
        verify(addTagRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(addTagRunRequest).run(any(ActionCompletionCallback.class));
    }

    /**
     * Test run basic actions command with no parameters
     */
    @Test
    public void testRunBasicActionsCommandNoParameters() {
        String url = "uairship://run-basic-actions";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(runRequestFactory, never()).createActionRequest(Mockito.anyString());
    }

    /**
     * Test run actions command
     */
    @Test
    public void testRunActionsCommand() throws ActionValueException {
        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("action")).thenReturn(actionRunRequest);

        ActionRunRequest anotherActionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("anotherAction")).thenReturn(anotherActionRunRequest);

        // uairship://run-actions?action={"key":"value"}&anotherAction=["one","two"]
        String url = "uairship://run-actions?action=%7B%20%22key%22%3A%22value%22%20%7D&anotherAction=%5B%22one%22%2C%22two%22%5D";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        // Verify the action "action" ran with a map
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("key", "value");
        verify(actionRunRequest).setValue(ActionValue.wrap(expectedMap));
        verify(actionRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(actionRunRequest).run(any(ActionCompletionCallback.class));

        // Verify that action "anotherAction" ran with a list
        List<String> expectedList = new ArrayList<>();
        expectedList.add("one");
        expectedList.add("two");
        verify(anotherActionRunRequest).setValue(ActionValue.wrap(expectedList));
        verify(anotherActionRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(anotherActionRunRequest).run(any(ActionCompletionCallback.class));

        verify(runRequestExtender).extend(actionRunRequest);
        verify(runRequestExtender).extend(anotherActionRunRequest);

        verifyZeroInteractions(javaScriptExecutor, commandDelegate);
    }

    /**
     * Test run actions command with no parameters
     */
    @Test
    public void testRunActionsCommandNoParameters() {
        // uairship://run-actions?action={"key":"value"}&anotherAction=["one","two"]
        String url = "uairship://run-actions";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(runRequestFactory, never()).createActionRequest(Mockito.anyString());
    }

    /**
     * Test run actions command with no action args
     */
    @Test
    public void testRunActionsCommandNoActionArgs() {
        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("action")).thenReturn(actionRunRequest);

        String url = "uairship://run-actions?action";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(actionRunRequest).setValue(new ActionValue());
        verify(actionRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(actionRunRequest).run(any(ActionCompletionCallback.class));

        verify(runRequestExtender).extend(actionRunRequest);

        verifyZeroInteractions(javaScriptExecutor, commandDelegate);
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

        ActionRunRequest addTagRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("addTag")).thenReturn(addTagRunRequest);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(arguments, result);
                return null;
            }
        }).when(addTagRunRequest).run(Mockito.any(ActionCompletionCallback.class));

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

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(arguments, result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

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

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(arguments, result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

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

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(arguments, result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        String url = "uairship://run-action-cb/actionName/true/callbackId";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(javaScriptExecutor)
                .executeJavaScript("UAirship.finishAction(new Error(\"error!\"), null, 'callbackId');");
    }

    /**
     * Test running an action with a result
     */
    @Test
    public void testActionCallAction() throws ActionValueException {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.STATUS_COMPLETED);
        final ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "what");

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(arguments, result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        String url = "uairship://run-action-cb/actionName/true/callbackId";
        assertTrue(nativeBridge.onHandleCommand(url, javaScriptExecutor, runRequestExtender, commandDelegate));

        verify(javaScriptExecutor)
                .executeJavaScript("UAirship.finishAction(null, \"action_result\", 'callbackId');");

        // Verify the action request
        verify(runRequest).run(any(ActionCompletionCallback.class));
        verify(runRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(runRequest).setValue(ActionValue.wrap(true));
    }

    /**
     * Test setting a action completion callback gets called for completed actions with callbacks
     */
    @Test
    public void testRunActionCallsCompletionCallback() throws ActionValueException {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.STATUS_COMPLETED);
        final ActionArguments arguments = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "what");

        ActionCompletionCallback completionCallback = mock(ActionCompletionCallback.class);
        nativeBridge.setActionCompletionCallback(completionCallback);

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(arguments, result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

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

        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("add_tags_action")).thenReturn(actionRunRequest);

        ActionRunRequest secondActionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("remove_tags_action")).thenReturn(secondActionRunRequest);

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
        assertEquals("cool", UAirship.shared().getNamedUser().getId());
    }
}
