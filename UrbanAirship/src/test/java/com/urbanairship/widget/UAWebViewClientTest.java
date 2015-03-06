/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.widget;

import android.annotation.SuppressLint;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.ActionValueException;
import com.urbanairship.actions.Situation;
import com.urbanairship.actions.StubbedActionRunRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class UAWebViewClientTest {

    ActionRunRequestFactory runRequestFactory;
    UAWebViewClient client;
    View rootView;

    String webViewUrl;
    WebView webView;

    @Before
    public void setup() {
        runRequestFactory = mock(ActionRunRequestFactory.class);
        rootView = mock(View.class);

        webView = Mockito.mock(WebView.class);
        when(webView.getRootView()).thenReturn(rootView);

        webViewUrl = "http://test-client";
        when(webView.getUrl()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return webViewUrl;
            }
        });

        UAirship.shared().getWhitelist().addEntry("http://test-client");

        client = new UAWebViewClient(runRequestFactory);
    }

    /**
     * Test run basic actions command
     */
    @Test
    public void testRunBasicActionsCommand() throws ActionValueException {
        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("action")).thenReturn(actionRunRequest);

        ActionRunRequest anotherActionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("anotherAction")).thenReturn(anotherActionRunRequest);

        String url = "uairship://run-basic-actions?action=value&anotherAction=anotherValue";

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        // Verify that the action runner ran the "action" action
        verify(actionRunRequest).setValue(ActionValue.wrap("value"));
        verify(actionRunRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(actionRunRequest).run(any(ActionCompletionCallback.class));

        // Verify that the action runner ran the "anotherAction" action
        verify(anotherActionRunRequest).setValue(eq(ActionValue.wrap("anotherValue")));
        verify(anotherActionRunRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(anotherActionRunRequest).run(any(ActionCompletionCallback.class));
    }

    /**
     * Test run basic actions command with encoded arguments
     */
    @Test
    public void testRunBasicActionsCommandEncodedParamters() throws ActionValueException {
        ActionRunRequest removeTagRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        ActionRunRequest addTagRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("^-t")).thenReturn(removeTagRunRequest);
        when(runRequestFactory.createActionRequest("^+t")).thenReturn(addTagRunRequest);

        // uairship://run-basic-actions?^+t=addTag&^-t=removeTag
        String encodedUrl = "uairship://run-basic-actions?%5E%2Bt=addTag&%5E-t=removeTag";

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, encodedUrl));

        // Verify that the action runner ran the removeTag action
        verify(removeTagRunRequest).setValue(ActionValue.wrap("removeTag"));
        verify(removeTagRunRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(removeTagRunRequest).run(any(ActionCompletionCallback.class));

        // Verify that the action runner ran the addTag action
        verify(addTagRunRequest).setValue(ActionValue.wrap("addTag"));
        verify(addTagRunRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(addTagRunRequest).run(any(ActionCompletionCallback.class));
    }

    /**
     * Test run actions command with encoded parameters and one bogus encoded parameter aborts running
     * any actions.
     */
    @Test
    public void testRunActionsCommandEncodedParamatersWithBogusParameter() {
        String encodedUrl = "uairship://run-actions?%5E%2Bt=addTag&%5E-t=removeTag&bogus={{{}}}";

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, encodedUrl));

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

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        // Verify that the action runner ran the addTag action
        verify(addTagRunRequest).setValue(new ActionValue());
        verify(addTagRunRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(addTagRunRequest).run(any(ActionCompletionCallback.class));
    }

    /**
     * Test run basic actions command with no parameters
     */
    @Test
    public void testRunBasicActionsCommandNoParameters() {
        String url = "uairship://run-basic-actions";

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

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

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));


        // Verify the action "action" ran with a map
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("key", "value");
        verify(actionRunRequest).setValue(ActionValue.wrap(expectedMap));
        verify(actionRunRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(actionRunRequest).run(any(ActionCompletionCallback.class));

        // Verify that action "anotherAction" ran with a list
        List<String> expectedList = new ArrayList<>();
        expectedList.add("one");
        expectedList.add("two");
        verify(anotherActionRunRequest).setValue(ActionValue.wrap(expectedList));
        verify(anotherActionRunRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(anotherActionRunRequest).run(any(ActionCompletionCallback.class));
    }

    /**
     * Test run actions command with no parameters
     */
    @Test
    public void testRunActionsCommandNoParameters() {
        // uairship://run-actions?action={"key":"value"}&anotherAction=["one","two"]
        String url = "uairship://run-actions";

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

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

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        verify(actionRunRequest).setValue(new ActionValue());
        verify(actionRunRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(actionRunRequest).run(any(ActionCompletionCallback.class));
    }

    /**
     * Test any uairship scheme does not get intercepted when the webview's url is not white listed.
     */
    @Test
    public void testRunActionNotWhiteListed() {
        webViewUrl = "http://not-white-listed";
        String url = "uairship://run-actions?action";

        assertFalse("Client should not override any ua scheme urls from an url that is not white listed",
                client.shouldOverrideUrlLoading(webView, url));

        webViewUrl = null;

        assertFalse("Client should not override any ua scheme urls from a null url",
                client.shouldOverrideUrlLoading(webView, url));
    }

    /**
     * Test onPageFinished loads the js bridge
     */
    @Test
    @SuppressLint("NewApi")
    public void testOnPageFinished() {
        client.onPageFinished(webView, webViewUrl);
        verify(webView).loadUrl(Mockito.argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object argument) {
                return ((String)argument).startsWith("javascript:");
            }
        }));
    }

    /**
     * Test the js interface is not injected if the url is not white listed.
     */
    @Test
    public void testOnPageFinishedNotWhiteListed() {
        webViewUrl = "http://notwhitelisted";
        client.onPageFinished(webView, webViewUrl);
        verifyZeroInteractions(webView);
    }

    /**
     * Test running an action calls the action completion callback
     */
    @Test
    public void testRunActionsCallsCompletionCallback() {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.Status.COMPLETED);
        final ActionArguments arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what");

        ActionCompletionCallback completionCallback = mock(ActionCompletionCallback.class);
        client.setActionCompletionCallback(completionCallback);

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

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        // Verify our callback was called
        verify(completionCallback).onFinish(arguments, result);
    }

    /**
     * Test close command calls onClose
     */
    @Test
    public void testCloseCommand() {
        String url = "uairship://close";
        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        verify(rootView).dispatchKeyEvent(argThat(new ArgumentMatcher<KeyEvent>() {
            @Override
            public boolean matches(Object o) {
                KeyEvent event = (KeyEvent) o;
                return KeyEvent.ACTION_DOWN == event.getAction() &&
                        KeyEvent.KEYCODE_BACK == event.getKeyCode();
            }
        }));

        verify(rootView).dispatchKeyEvent(argThat(new ArgumentMatcher<KeyEvent>() {
            @Override
            public boolean matches(Object o) {
                KeyEvent event = (KeyEvent) o;
                return KeyEvent.ACTION_UP == event.getAction() &&
                        KeyEvent.KEYCODE_BACK == event.getKeyCode();
            }
        }));
    }

    /**
     * Test close simulates a back press on the activity
     */
    @Test
    public void testOnClose() {
        client.onClose(webView);

        verify(rootView).dispatchKeyEvent(argThat(new ArgumentMatcher<KeyEvent>() {
            @Override
            public boolean matches(Object o) {
                KeyEvent event = (KeyEvent) o;
                return KeyEvent.ACTION_DOWN == event.getAction() &&
                        KeyEvent.KEYCODE_BACK == event.getKeyCode();
            }
        }));

        verify(rootView).dispatchKeyEvent(argThat(new ArgumentMatcher<KeyEvent>() {
            @Override
            public boolean matches(Object o) {
                KeyEvent event = (KeyEvent) o;
                return KeyEvent.ACTION_UP == event.getAction() &&
                        KeyEvent.KEYCODE_BACK == event.getKeyCode();
            }
        }));
    }

    /**
     * Test running an action with an invalid arguments payload
     */
    @Test
    public void testActionCallInvalidArguments() {
        // actionName = {invalid_json}}}
        String url = "uairship://android-run-action-cb/actionName/%7Binvalid_json%7D%7D%7D/callbackId";
        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error(\"Unable to decode arguments payload\"), null, 'callbackId');");
    }

    /**
     * Test running an action that is not found
     */
    @Test
    public void testActionCallActionNotFound() {
        final ActionResult result = ActionTestUtils.createResult(null, null, ActionResult.Status.ACTION_NOT_FOUND);
        final ActionArguments arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what");

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

        String url = "uairship://android-run-action-cb/actionName/true/callbackId";
        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error(\"Action actionName not found\"), null, 'callbackId');");
    }

    /**
     * Test running an action that rejects the arguments
     */
    @Test
    public void testActionCallActionRejectedArguments() {
        final ActionResult result = ActionTestUtils.createResult(null, null, ActionResult.Status.REJECTED_ARGUMENTS);
        final ActionArguments arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what");

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

        String url = "uairship://android-run-action-cb/actionName/true/callbackId";
        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error(\"Action actionName rejected its arguments\"), null, 'callbackId');");
    }

    /**
     * Test running an action that had an execution error
     */
    @Test
    public void testActionCallActionExecutionError() {
        final ActionResult result = ActionTestUtils.createResult(null, new Exception("error!"), ActionResult.Status.EXECUTION_ERROR);
        final ActionArguments arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what");

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

        String url = "uairship://android-run-action-cb/actionName/true/callbackId";
        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error(\"error!\"), null, 'callbackId');");
    }

    /**
     * Test running an action with a result
     */
    @Test
    public void testActionCallAction() throws ActionValueException {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.Status.COMPLETED);
        final ActionArguments arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what");

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

        String url = "uairship://android-run-action-cb/actionName/true/callbackId";
        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        // Verify the callback
        verify(webView).loadUrl("javascript:UAirship.finishAction(null, \"action_result\", 'callbackId');");

        // Verify the action request
        verify(runRequest).run(any(ActionCompletionCallback.class));
        verify(runRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(runRequest).setValue(ActionValue.wrap(true));
    }

    /**
     * Test setting a action completion callback gets called for completed actions with callbacks
     */
    @Test
    public void testRunActionCallsCompletionCallback() throws ActionValueException {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.Status.COMPLETED);
        final ActionArguments arguments = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "what");

        ActionCompletionCallback completionCallback = mock(ActionCompletionCallback.class);
        client.setActionCompletionCallback(completionCallback);

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

        String url = "uairship://android-run-action-cb/actionName/true/callbackId";
        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        // Verify our callback was called
        verify(completionCallback).onFinish(arguments, result);
    }
}
