/* Copyright Airship and Contributors */

package com.urbanairship.automation.auth;

import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestClock;
import com.urbanairship.TestRequest;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AuthApiClientTest {

    private TestRequest testRequest;
    private TestClock clock;
    private AuthApiClient client;

    @Before
    public void setup() {
        clock = new TestClock();
        testRequest = new TestRequest();

        TestAirshipRuntimeConfig runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://example.com")
                                                   .build());

        client = new AuthApiClient(runtimeConfig, clock, new RequestFactory() {
            @NonNull
            @Override
            public Request createRequest() {
                return testRequest;
            }
        });
    }

    @Test
    public void test200Response() throws RequestException {
        clock.currentTimeMillis = 100;
        testRequest.responseBody = JsonMap.newBuilder()
                                      .put("token", "some token")
                                      .put("expires_in", 300)
                                      .build().toString();
        testRequest.responseStatus = 200;

        Response<AuthToken> response = client.getToken("some channel");
        assertEquals(response.getResult().getChannelId(), "some channel");
        assertEquals(response.getResult().getToken(), "some token");
        assertEquals(response.getResult().getExpiration(), 400);
    }

    @Test
    public void testRequest() throws RequestException {
        client.getToken("some channel");

        assertEquals("https://example.com/api/auth/device", testRequest.getUrl().toString());
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals("Bearer VWtkZq18HZM3GWzD/q27qPSVszysSyoQfQ6tDEAcAko=\n", testRequest.getRequestHeaders().get("Authorization"));
        assertEquals("appKey", testRequest.getRequestHeaders().get("X-UA-App-Key"));
        assertEquals("some channel", testRequest.getRequestHeaders().get("X-UA-Channel-ID"));
    }

    @Test(expected = RequestException.class)
    public void testInvalidResponse() throws RequestException {
        testRequest.responseBody = "what";
        testRequest.responseStatus = 200;
        client.getToken("some channel");
    }
}
