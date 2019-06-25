/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.net.Uri;
import androidx.annotation.NonNull;
import android.view.View;
import android.webkit.WebView;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.ActionValueException;
import com.urbanairship.actions.StubbedActionRunRequest;
import com.urbanairship.iam.html.HtmlWebViewClient;
import com.urbanairship.json.JsonValue;
import com.urbanairship.widget.UAWebViewClient;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;

import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HtmlWebViewClientTest extends BaseTestCase {

    ActionRunRequestFactory runRequestFactory;
    UAWebViewClient client;
    WebView webView;
    View rootView;
    String webViewUrl;
    final ArrayList<JsonValue> passedValue = new ArrayList<>();

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
        when(webView.getContext()).thenReturn(TestApplication.getApplication());

        UAirship.shared().getWhitelist().addEntry("http://test-client");

        client = new HtmlWebViewClient(runRequestFactory) {
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

    /**
     * Test that default behavior is inherited from the superclass
     */
    @Test
    public void testDefaultBehavior() throws ActionValueException {
        ActionRunRequest actionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("action")).thenReturn(actionRunRequest);

        ActionRunRequest anotherActionRunRequest = Mockito.mock(StubbedActionRunRequest.class, Mockito.CALLS_REAL_METHODS);
        when(runRequestFactory.createActionRequest("anotherAction")).thenReturn(anotherActionRunRequest);

        String url = "uairship://run-basic-actions?action=value&anotherAction=anotherValue";

        assertTrue("Client should override any ua scheme urls", client.shouldOverrideUrlLoading(webView, url));

        // Verify that the action runner ran the "action" action
        verify(actionRunRequest).setValue(ActionValue.wrap("value"));
        verify(actionRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(actionRunRequest).run(any(ActionCompletionCallback.class));

        // Verify that the action runner ran the "anotherAction" action
        verify(anotherActionRunRequest).setValue(eq(ActionValue.wrap("anotherValue")));
        verify(anotherActionRunRequest).setSituation(Action.SITUATION_WEB_VIEW_INVOCATION);
        verify(anotherActionRunRequest).run(any(ActionCompletionCallback.class));
    }

}