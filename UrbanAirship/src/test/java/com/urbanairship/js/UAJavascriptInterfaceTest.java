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

package com.urbanairship.js;

import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.Situation;
import com.urbanairship.actions.StubbedActionRunRequest;
import com.urbanairship.richpush.RichPushMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Date;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class UAJavascriptInterfaceTest {

    private UAJavascriptInterface js;

    private WebView webView;
    private View rootView;
    private ActionRunRequestFactory actionRunRequestFactory;

    private RichPushMessage message;


    @Before
    public void setup() {
        actionRunRequestFactory = mock(ActionRunRequestFactory.class);
        rootView = mock(View.class);
        message = mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message id");


        webView = mock(WebView.class);
        when(webView.getRootView()).thenReturn(rootView);

        js = new UAJavascriptInterface(webView, message, actionRunRequestFactory);
    }

    /**
     * Test getting the device's model name
     */
    @Test
    public void testGetDeviceModel() {
        assertEquals("The model should match the Build.Model", Build.MODEL, js.getDeviceModel());
    }

    /**
     * Test running an action with an invalid arguments payload
     */
    @Test
    public void testActionCallInvalidArguments() {
        js.actionCall("actionName", "{invalid json}}}", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error(\"Unable to decode arguments payload\"), null, 'callbackKey');");
    }


    /**
     * Test running an action that is not found
     */
    @Test
    public void testActionCallActionNotFound() {
        final ActionResult result = ActionTestUtils.createResult(null, null, ActionResult.Status.ACTION_NOT_FOUND);

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        js.actionCall("actionName", "true", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();


        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error(\"Action actionName not found\"), null, 'callbackKey');");
    }


    /**
     * Test running an action that rejects the arguments
     */
    @Test
    public void testActionCallActionRejectedArguments() {
        final ActionResult result = ActionTestUtils.createResult(null, null, ActionResult.Status.REJECTED_ARGUMENTS);

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        js.actionCall("actionName", "true", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error(\"Action actionName rejected its arguments\"), null, 'callbackKey');");
    }

    /**
     * Test running an action that had an execution error
     */
    @Test
    public void testActionCallActionExecutionError() {
        final ActionResult result = ActionTestUtils.createResult(null, new Exception("error!"), ActionResult.Status.EXECUTION_ERROR);

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        js.actionCall("actionName", "true", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error(\"error!\"), null, 'callbackKey');");
    }

    /**
     * Test running an action with a result
     */
    @Test
    public void testActionCallAction() throws ActionValue.ActionValueException {
        final ActionResult result = ActionTestUtils.createResult("action_result", null, ActionResult.Status.COMPLETED);

        ActionRunRequest runRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(actionRunRequestFactory.createActionRequest("actionName")).thenReturn(runRequest);

        // Call the action completion handler on run
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[0];
                callback.onFinish(result);
                return null;
            }
        }).when(runRequest).run(Mockito.any(ActionCompletionCallback.class));

        js.actionCall("actionName", "true", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        // Verify the callback
        verify(webView).loadUrl("javascript:UAirship.finishAction(null, \"action_result\", 'callbackKey');");

        // Verify the action request
        verify(runRequest).run(any(ActionCompletionCallback.class));
        verify(runRequest).setSituation(Situation.WEB_VIEW_INVOCATION);
        verify(runRequest).setValue(ActionValue.wrap(true));
        verify(runRequest).setMetadata(argThat(new ArgumentMatcher<Bundle>() {
            @Override
            public boolean matches(Object o) {
                Bundle bundle = (Bundle) o;
                return bundle.get(ActionArguments.RICH_PUSH_ID_METADATA).equals(message.getMessageId());
            }
        }));
    }

    /**
     * Test close simulates a back press on the activity
     */
    @Test
    public void testClose() {
        js.close();

        // Dispatching the key events are posted on the main thread using post,
        // capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

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
     * Test getMessageId returns the message's id
     */
    @Test
    public void testGetMessageId() {
        when(message.getMessageId()).thenReturn("messageId");
        assertEquals("Should fetch message id", "messageId", js.getMessageId());
    }

    /**
     * Test getMessageId returns null when the message is null
     */
    @Test
    public void testGetMessageIdNullMesssage() {
        js = new UAJavascriptInterface(webView, null);
        assertNull("Should return null", js.getMessageId());
    }


    /**
     * Test getMessageSentDate returns the message's sent date as a formatted date
     */
    @Test
    public void testMessageSentDate() {
        when(message.getSentDate()).thenReturn(new Date(0));
        assertEquals("Should return the message's sent date as a formatted date",
                "1970-01-01 00:00:00.000+0000", js.getMessageSentDate());
    }

    /**
     * Test getMessageSentDate returns null when the message is null
     */
    @Test
    public void testMessageSentDateNullMessage() {
        js = new UAJavascriptInterface(webView, null);
        assertNull("Should return null", js.getMessageSentDate());
    }

    /**
     * Test getMessageSentDateMS returns the message's sent date in milliseconds
     */
    @Test
    public void testMessageSentDateMS() {
        when(message.getSentDateMS()).thenReturn(100L);
        assertEquals("Should return the message's sent date",
                100, js.getMessageSentDateMS());
    }

    /**
     * Test getMessageSentDateMS returns -1 if the message is null
     */
    @Test
    public void testMessageSentDateMSNullMessage() {
        js = new UAJavascriptInterface(webView, null);
        assertEquals("Should return -1 if the message is null",
                -1, js.getMessageSentDateMS());
    }
}
