/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebView;

import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.contacts.Contact;
import com.urbanairship.iam.html.HtmlDisplayContent;
import com.urbanairship.iam.html.HtmlWebViewClient;
import com.urbanairship.javascript.JavaScriptEnvironment;
import com.urbanairship.javascript.JavaScriptExecutor;
import com.urbanairship.javascript.NativeBridge;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class HtmlWebViewClientTest {

    private WebView webView = mock(WebView.class);
    private String webViewUrl = "http://test-client";
    private Contact mockContact = mock(Contact.class);
    private InAppMessage message;

    @Before
    public void setup() {
        when(webView.getUrl()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                return webViewUrl;
            }
        });
        when(webView.getContext()).thenReturn(TestApplication.getApplication());

        HtmlDisplayContent content = HtmlDisplayContent.newBuilder()
                                                       .setUrl("www.cool.story")
                                                       .setAllowFullscreenDisplay(true)
                                                       .setRequireConnectivity(false)
                                                       .build();

        JsonMap extrasMap = JsonMap.newBuilder().put("coolkey", "coolvalue").build();

        this.message = InAppMessage.newBuilder().setDisplayContent(content)
                                   .setExtras(extrasMap)
                                   .build();

        UAirship.shared().getUrlAllowList().addEntry("http://test-client");
        TestApplication.getApplication().setContact(mockContact);
    }

    /**
     * Test that the special case of the dismiss command calls onMessageDismissed
     */
    @Test
    public void testDismissCommand() {
        final List<JsonValue> passedValue = new ArrayList<>();
        HtmlWebViewClient client = new HtmlWebViewClient(message) {
            @Override
            public void onMessageDismissed(@NonNull JsonValue argument) {
                passedValue.add(0, argument);
            }
        };

        String url = "uairship://dismiss/";

        ButtonInfo button = ButtonInfo.newBuilder()
                                      .setId("foo")
                                      .setLabel(TextInfo.newBuilder()
                                                        .setText("bar")
                                                        .build())
                                      .build();

        ResolutionInfo resolution = ResolutionInfo.buttonPressed(button);

        JsonValue jsonValue = resolution.toJsonValue();
        String jsonString = jsonValue.toString();

        url = url + Uri.encode(jsonString);

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));
        assertTrue(passedValue.get(0).equals(jsonValue));
    }

    @Test
    public void testJavaScriptEnvironment() {
        NativeBridge nativeBridge = mock(NativeBridge.class);
        HtmlWebViewClient client = new HtmlWebViewClient(nativeBridge, message) {
            @Override
            public void onMessageDismissed(@NonNull JsonValue argument) {
            }
        };
        client.onPageFinished(webView, webViewUrl);

        ArgumentCaptor<JavaScriptEnvironment> argument = ArgumentCaptor.forClass(JavaScriptEnvironment.class);

        verify(nativeBridge).loadJavaScriptEnvironment(
                any(Context.class),
                argument.capture(),
                any(JavaScriptExecutor.class));

        JavaScriptEnvironment javaScriptEnvironment = argument.getValue();
        String environment = javaScriptEnvironment.getJavaScript(webView.getContext());
        String expected = "_UAirship.getMessageExtras = function(){return {\"coolkey\":\"coolvalue\"};};";

        assertTrue(environment.contains(expected));
    }

}
