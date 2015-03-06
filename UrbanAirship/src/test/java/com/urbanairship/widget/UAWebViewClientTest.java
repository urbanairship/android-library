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
import com.urbanairship.js.NativeBridge;
import com.urbanairship.js.UAJavascriptInterface;
import com.urbanairship.richpush.RichPushMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
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

    String webViewUrl;
    WebView webView;

    @Before
    public void setup() {

        runRequestFactory = mock(ActionRunRequestFactory.class);

        client = new UAWebViewClient(runRequestFactory);
        webView = Mockito.mock(WebView.class);

        when(webView.getUrl()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return webViewUrl;
            }
        });

        webViewUrl = "http://test-client";
        UAirship.shared().getWhitelist().addEntry("http://test-client");
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

//    Enable this test once Robolectric supports API 19.
//
//    /**
//     * Test onPageFinished evaluates the js bridge on API 19.
//     */
//    @Test
//    @Config(emulateSdk = 19)
//    @SuppressLint("NewApi")
//    public void testOnPageFinishedAPI19() {
//        client.onPageFinished(webView, webViewUrl);
//        verify(webView).evaluateJavascript(NativeBridge.getJavaScriptSource(), null);
//    }

    /**
     * Test onPageFinished loads the js bridge on API 17-18.
     */
    @Test
    @Config(reportSdk = 17)
    @SuppressLint("NewApi")
    public void testOnPageFinishedAPI17() {
        client.onPageFinished(webView, webViewUrl);
        verify(webView).loadUrl("javascript:" + client.createJavascriptInterfaceWrapper(new UAJavascriptInterface(webView)));
        verify(webView).loadUrl("javascript:" + NativeBridge.getJavaScriptSource());
    }

    /**
     * Test onPageFinished loads the js bridge and the wrapper on API < 17.
     */
    @Test
    @Config(reportSdk = 16)
    @SuppressLint("NewApi")
    public void testOnPageFinishedPre17() {
        client.onPageFinished(webView, webViewUrl);

        // Wrapper
        verify(webView).loadUrl("javascript:" + client.createJavascriptInterfaceWrapper(new UAJavascriptInterface(webView)));

        // Bridge
        verify(webView).loadUrl("javascript:" + NativeBridge.getJavaScriptSource());
    }

    /**
     * Test onPageFinished loads the js bridge and the wrapper with the message on API < 17.
     */
    @Test
    @Config(reportSdk = 16)
    @SuppressLint("NewApi")
    public void testOnPageFinishedPre17RichPush() {
        // Set up a RichPushMessageWebView
        RichPushMessage message = Mockito.mock(RichPushMessage.class);
        when(message.getMessageId()).thenReturn("message id");
        when(message.getSentDateMS()).thenReturn(100L);
        when(message.getSentDate()).thenReturn(new Date(100L));

        RichPushMessageWebView richPushMessageWebView = Mockito.mock(RichPushMessageWebView.class);
        when(richPushMessageWebView.getCurrentMessage()).thenReturn(message);
        when(richPushMessageWebView.getUrl()).thenReturn(webViewUrl);

        client.onPageFinished(richPushMessageWebView, webViewUrl);

        // Wrapper
        verify(richPushMessageWebView).loadUrl("javascript:" + client.createJavascriptInterfaceWrapper(new UAJavascriptInterface(richPushMessageWebView, message)));

        // Bridge
        verify(richPushMessageWebView).loadUrl("javascript:" + NativeBridge.getJavaScriptSource());
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
    public void testActionCompletionCallback() {
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
}
