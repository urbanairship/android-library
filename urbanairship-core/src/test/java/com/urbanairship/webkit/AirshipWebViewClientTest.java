/* Copyright Airship and Contributors */

package com.urbanairship.webkit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionRunRequestExtender;
import com.urbanairship.contacts.Contact;
import com.urbanairship.javascript.JavaScriptEnvironment;
import com.urbanairship.javascript.JavaScriptExecutor;
import com.urbanairship.javascript.NativeBridge;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static junit.framework.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class AirshipWebViewClientTest extends BaseTestCase {

    private View rootView = mock(View.class);
    private String webViewUrl = "http://test-client";
    private WebView webView = mock(WebView.class);
    private NativeBridge nativeBridge = mock(NativeBridge.class);
    private Contact mockContact = mock(Contact.class);

    private AirshipWebViewClient client;

    @Before
    public void setup() {
        when(webView.getRootView()).thenReturn(rootView);
        when(webView.getUrl()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                return webViewUrl;
            }
        });
        when(webView.getContext()).thenReturn(TestApplication.getApplication());

        TestApplication.getApplication().setContact(mockContact);
        UAirship.shared().getUrlAllowList().addEntry("http://test-client");
        client = new AirshipWebViewClient(nativeBridge);
    }

    /**
     * Test any uairship scheme does not get intercepted when the webview's url is not allowed.
     */
    @Test
    public void testHandleCommandNotAllowed() {
        webViewUrl = "http://not-allowed";
        String url = "uairship://run-actions?action";
        assertFalse(client.shouldOverrideUrlLoading(webView, url));

        webViewUrl = null;
        assertFalse(client.shouldOverrideUrlLoading(webView, url));
        verifyZeroInteractions(nativeBridge);
    }

    /**
     * Test onPageFinished loads the js bridge
     */
    @Test
    @SuppressLint("NewApi")
    public void testOnPageFinished() {
        client.onPageFinished(webView, webViewUrl);

        ArgumentCaptor<JavaScriptExecutor> argument = ArgumentCaptor.forClass(JavaScriptExecutor.class);

        verify(nativeBridge).loadJavaScriptEnvironment(
                any(Context.class),
                any(JavaScriptEnvironment.class),
                argument.capture());

        argument.getValue().executeJavaScript("test");
        verify(webView).evaluateJavascript("test", null);
    }

    /**
     * Test the js interface is not injected if the url is not allowed.
     */
    @Test
    @SuppressLint("NewApi")
    public void testOnPageFinishedNotAllowed() {
        webViewUrl = "http://notallowed";
        client.onPageFinished(webView, webViewUrl);
        verifyZeroInteractions(nativeBridge);
    }

    /**
     * Test close command calls onClose
     */
    @Test
    public void testOnClose() {
        String url = "uairship://close";

        AirshipWebViewClient spy = spy(client);

        ArgumentCaptor<NativeBridge.CommandDelegate> argument = ArgumentCaptor.forClass(NativeBridge.CommandDelegate.class);
        when(nativeBridge.onHandleCommand(
                eq(url),
                any(JavaScriptExecutor.class),
                any(ActionRunRequestExtender.class),
                argument.capture()))
                .thenReturn(true);

        spy.shouldOverrideUrlLoading(webView, url);

        // Call close
        argument.getValue().onClose();

        verify(spy).onClose(webView);

        verify(rootView).dispatchKeyEvent(argThat(new ArgumentMatcher<KeyEvent>() {
            @Override
            public boolean matches(KeyEvent event) {
                return KeyEvent.ACTION_DOWN == event.getAction() &&
                        KeyEvent.KEYCODE_BACK == event.getKeyCode();
            }
        }));


        verify(rootView).dispatchKeyEvent(argThat(new ArgumentMatcher<KeyEvent>() {
            @Override
            public boolean matches(KeyEvent event) {
                return KeyEvent.ACTION_UP == event.getAction() &&
                        KeyEvent.KEYCODE_BACK == event.getKeyCode();
            }
        }));
    }



    /**
     * Test close command calls onClose
     */
    @Test
    public void testOnAirshipCommand() {
        String url = "uairship://cool";

        AirshipWebViewClient spy = spy(client);

        ArgumentCaptor<NativeBridge.CommandDelegate> argument = ArgumentCaptor.forClass(NativeBridge.CommandDelegate.class);
        when(nativeBridge.onHandleCommand(
                eq(url),
                any(JavaScriptExecutor.class),
                any(ActionRunRequestExtender.class),
                argument.capture()))
                .thenReturn(true);

        spy.shouldOverrideUrlLoading(webView, url);

        // Call close
        argument.getValue().onAirshipCommand("cool", Uri.parse(url));

        verify(spy).onAirshipCommand(webView, "cool", Uri.parse(url));
    }

    /**
     * Test JavaScriptExecutor executes on the right web view.
     */
    @Test
    public void testHandleCommandJavaScriptExecutor() {
        String url = "uairship://whatever";

        ArgumentCaptor<JavaScriptExecutor> argument = ArgumentCaptor.forClass(JavaScriptExecutor.class);
        when(nativeBridge.onHandleCommand(
                eq(url),
                argument.capture(),
                any(ActionRunRequestExtender.class),
                any(NativeBridge.CommandDelegate.class)))
                .thenReturn(true);

        client.shouldOverrideUrlLoading(webView, url);

        // Call close
        argument.getValue().executeJavaScript("cool");
        verify(webView).evaluateJavascript("cool", null);
    }
}
