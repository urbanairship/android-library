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

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionArgumentsMatcher;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunner;
import com.urbanairship.actions.Situation;
import com.urbanairship.richpush.RichPushMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowApplication;

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
    private Configuration configuration;
    private Resources resources;
    private ShadowApplication application;
    private ActionRunner actionRunner;
    private Activity activity;

    private RichPushMessage message;


    @Before
    public void setup() {
        actionRunner = mock(ActionRunner.class);
        application = Robolectric.getShadowApplication();
        resources = mock(Resources.class);
        configuration = new Configuration();
        activity = mock(Activity.class);
        rootView = mock(View.class);
        message = mock(RichPushMessage.class);


        webView = mock(WebView.class);
        when(webView.getContext()).thenReturn(activity);
        when(webView.getResources()).thenReturn(resources);
        when(resources.getConfiguration()).thenReturn(configuration);
        when(webView.getRootView()).thenReturn(rootView);

        js = new UAJavascriptInterface(webView, actionRunner, message);
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
        js.actionCall("actionName", "invalid json", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error('Unable to decode arguments payload'), null, 'callbackKey');");
    }


    /**
     * Test running an action that is not found
     */
    @Test
    public void testActionCallActionNotFound() {
        final MutableActionResult result = new MutableActionResult();
        result.status = ActionResult.Status.ACTION_NOT_FOUND;

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[2];
                callback.onFinish(result);
                return null;
            }
        }).when(actionRunner).runAction(Mockito.anyString(),
                any(ActionArguments.class),
                any(ActionCompletionCallback.class));

        js.actionCall("actionName", "{ \"value\": true }", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error('Action actionName not found'), null, 'callbackKey');");
    }


    /**
     * Test running an action that rejects the arguments
     */
    @Test
    public void testActionCallActionRejectedArguments() {
        final MutableActionResult result = new MutableActionResult();
        result.status = ActionResult.Status.REJECTED_ARGUMENTS;

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[2];
                callback.onFinish(result);
                return null;
            }
        }).when(actionRunner).runAction(Mockito.anyString(),
                any(ActionArguments.class),
                any(ActionCompletionCallback.class));

        js.actionCall("actionName", "{ \"value\": true }", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error('Action actionName rejected its arguments'), null, 'callbackKey');");
    }

    /**
     * Test running an action that had an execution error
     */
    @Test
    public void testActionCallActionExecutionError() {
        final MutableActionResult result = new MutableActionResult();
        result.status = ActionResult.Status.EXECUTION_ERROR;
        result.error = new Exception("error!");

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[2];
                callback.onFinish(result);
                return null;
            }
        }).when(actionRunner).runAction(Mockito.anyString(),
                any(ActionArguments.class),
                any(ActionCompletionCallback.class));

        js.actionCall("actionName", "{ \"value\": true }", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        verify(webView).loadUrl("javascript:UAirship.finishAction(new Error('error!'), null, 'callbackKey');");
    }

    /**
     * Test running an action with a result
     */
    @Test
    public void testActionCallAction() {
        final MutableActionResult result = new MutableActionResult();
        result.status = ActionResult.Status.COMPLETED;
        result.value = "actionValue";

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionCompletionCallback callback = (ActionCompletionCallback) args[2];
                callback.onFinish(result);
                return null;
            }
        }).when(actionRunner).runAction(Mockito.anyString(),
                Mockito.argThat(new ActionArgumentsMatcher(Situation.WEB_VIEW_INVOCATION, true) {
                    @Override
                    public boolean matches(Object o) {
                        if (!super.matches(o)) {
                            return false;
                        }
                        ActionArguments actionArguments = (ActionArguments) o;
                        RichPushMessage otherMessage = actionArguments.getMetadata(ActionArguments.RICH_PUSH_METADATA);
                        return message == otherMessage;
                    }
                }),
                any(ActionCompletionCallback.class)
                                       );

        js.actionCall("actionName", "{ \"value\": true }", "callbackKey");

        // Callbacks are posted on the main thread using post, capture and run the runnable
        ArgumentCaptor<Runnable> argument = ArgumentCaptor.forClass(Runnable.class);
        verify(webView).post(argument.capture());
        argument.getValue().run();

        verify(webView).loadUrl("javascript:UAirship.finishAction(null, '{\"value\":\"actionValue\"}', 'callbackKey');");
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
        js = new UAJavascriptInterface(webView, actionRunner, null);
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
        js = new UAJavascriptInterface(webView, actionRunner, null);
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
        js = new UAJavascriptInterface(webView, actionRunner, null);
        assertEquals("Should return -1 if the message is null",
                -1, js.getMessageSentDateMS());
    }

    /**
     * A mutable action result
     */
    public static class MutableActionResult extends ActionResult {
        public Status status;
        public Object value;
        public Exception error;

        public MutableActionResult() {
            super(null, null, Status.COMPLETED);
            this.status = Status.COMPLETED;
            this.value = null;
            this.error = null;
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public Exception getException() {
            return error;
        }
    }
}
