/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.net.Uri;
import android.webkit.WebView;

import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.iam.html.HtmlWebViewClient;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class HtmlWebViewClientTest {

    private HtmlWebViewClient client;
    private WebView webView;
    private String webViewUrl;
    private ArrayList<JsonValue> passedValue;

    @Before
    public void setup() {
        webView = Mockito.mock(WebView.class);

        webViewUrl = "http://test-client";
        when(webView.getUrl()).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return webViewUrl;
            }
        });
        when(webView.getContext()).thenReturn(TestApplication.getApplication());

        UAirship.shared().getWhitelist().addEntry("http://test-client");

        passedValue = new ArrayList<>();
        client = new HtmlWebViewClient() {
            @Override
            public void onMessageDismissed(@NonNull JsonValue argument) {
                passedValue.add(0, argument);
            }
        };
    }

    /**
     * Test that the special case of the dismiss command calls onMessageDismissed
     */
    @Test
    public void testDismissCommand() {
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
}
