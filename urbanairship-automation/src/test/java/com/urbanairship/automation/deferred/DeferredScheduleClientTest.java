/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred;

import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.automation.TriggerContext;
import com.urbanairship.automation.Triggers;
import com.urbanairship.automation.auth.AuthException;
import com.urbanairship.automation.auth.AuthManager;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class DeferredScheduleClientTest {

    private DeferredScheduleClient client;
    private AuthManager mockAuthManager;
    private TestRequest testRequest;
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setup() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();

        testRequest = new TestRequest();
        mockAuthManager = mock(AuthManager.class);
        client = new DeferredScheduleClient(runtimeConfig, mockAuthManager, new RequestFactory() {
            @NonNull
            @Override
            public Request createRequest() {
                return testRequest;
            }
        });
    }

    @Test(expected = AuthException.class)
    public void testAuthManagerExceptions() throws AuthException, MalformedURLException, RequestException {
        when(mockAuthManager.getToken()).thenThrow(new AuthException("neat"));
        client.performRequest(new URL("https://airship.com"), "channel", null);
    }

    @Test
    public void testAndroidRequest() throws AuthException, MalformedURLException, RequestException {
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);
        when(mockAuthManager.getToken()).thenReturn("some_token");

        URL url = new URL("https://airship.com");
        client.performRequest(url, "channel", null);
        assertEquals(url, testRequest.getUrl());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("Bearer some_token", testRequest.getRequestHeaders().get("Authorization"));

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "android")
                                  .put("channel_id", "channel")
                                  .build();

        assertEquals(expected.toString(), testRequest.getRequestBody());
    }

    @Test
    public void testAmazonRequest() throws AuthException, MalformedURLException, RequestException {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);
        when(mockAuthManager.getToken()).thenReturn("some_token");

        URL url = new URL("https://airship.com");
        client.performRequest(url, "channel", null);
        assertEquals(url, testRequest.getUrl());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("Bearer some_token", testRequest.getRequestHeaders().get("Authorization"));

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "amazon")
                                  .put("channel_id", "channel")
                                  .build();

        assertEquals(expected.toString(), testRequest.getRequestBody());
    }

    @Test
    public void testTriggeringContext() throws AuthException, MalformedURLException, RequestException {
        when(mockAuthManager.getToken()).thenReturn("some token");

        CustomEvent event = CustomEvent.newBuilder("some event").build();
        TriggerContext triggerContext = new TriggerContext(Triggers.newCustomEventTriggerBuilder().build(), event.toJsonValue());
        client.performRequest(new URL("https://airship.com"), "channel", triggerContext);

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "android")
                                  .put("channel_id", "channel")
                                  .put("trigger", JsonMap.newBuilder()
                                                         .put("goal", 1)
                                                         .put("type", "custom_event_count")
                                                         .put("event", event.toJsonValue())
                                                         .build())
                                  .build();

        assertEquals(expected.toString(), testRequest.getRequestBody());
    }

    @Test
    public void testMissedResponse() throws AuthException, MalformedURLException, RequestException {
        when(mockAuthManager.getToken()).thenReturn("some_token");

        testRequest.responseStatus = 200;
        testRequest.responseBody = JsonMap.newBuilder()
                                          .put("audience_match", false)
                                          .build()
                                          .toString();

        Response<DeferredScheduleClient.Result> response = client.performRequest(new URL("https://airship.com"), "channel", null);

        assertFalse(response.getResult().isAudienceMatch());
        assertNull(response.getResult().getMessage());
    }

    @Test
    public void testNoMessage() throws AuthException, MalformedURLException, RequestException {
        when(mockAuthManager.getToken()).thenReturn("some_token");

        testRequest.responseStatus = 200;
        testRequest.responseBody = JsonMap.newBuilder()
                                          .put("audience_match", true)
                                          .build()
                                          .toString();

        Response<DeferredScheduleClient.Result> response = client.performRequest(new URL("https://airship.com"), "channel", null);

        assertTrue(response.getResult().isAudienceMatch());
        assertNull(response.getResult().getMessage());
    }

    @Test
    public void testMessage() throws AuthException, MalformedURLException, RequestException {
        InAppMessage message = InAppMessage.newBuilder()
                                           .setId("some id")
                                           .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                           .build();

        when(mockAuthManager.getToken()).thenReturn("some_token");

        testRequest.responseStatus = 200;
        testRequest.responseBody = JsonMap.newBuilder()
                                          .put("audience_match", true)
                                          .put("type", "in_app_message")
                                          .put("message", message)
                                          .build()
                                          .toString();

        Response<DeferredScheduleClient.Result> response = client.performRequest(new URL("https://airship.com"), "channel", null);

        assertTrue(response.getResult().isAudienceMatch());
        assertEquals(message, response.getResult().getMessage());
    }

    @Test
    public void testExpiredToken() throws AuthException, MalformedURLException, RequestException {
        when(mockAuthManager.getToken())
                .thenReturn("expired")
                .thenReturn("some_other_token");

        testRequest.responseStatus = 401;
        client.performRequest(new URL("https://airship.com"), "channel", null);

        assertEquals("Bearer some_other_token", testRequest.getRequestHeaders().get("Authorization"));

        verify(mockAuthManager).tokenExpired("expired");
    }

}
