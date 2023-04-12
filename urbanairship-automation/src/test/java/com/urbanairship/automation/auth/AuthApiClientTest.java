/* Copyright Airship and Contributors */

package com.urbanairship.automation.auth;

import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestClock;
import com.urbanairship.TestRequestSession;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestException;
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

    private TestRequestSession requestSession = new TestRequestSession();
    private TestClock clock;
    private AuthApiClient client;

    @Before
    public void setup() {
        clock = new TestClock();

        TestAirshipRuntimeConfig runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                                                   .setDeviceUrl("https://example.com")
                                                   .build());

        client = new AuthApiClient(runtimeConfig, requestSession, clock);
    }

    @Test
    public void test200Response() throws RequestException {
        clock.currentTimeMillis = 100;
        requestSession.addResponse(200,
                JsonMap.newBuilder()
                       .put("token", "some token")
                       .put("expires_in", 300)
                       .build().toString()
        );
        Response<AuthToken> response = client.getToken("some channel");
        assertEquals(response.getResult().getChannelId(), "some channel");
        assertEquals(response.getResult().getToken(), "some token");
        assertEquals(response.getResult().getExpiration(), 400);
    }

    @Test
    public void testRequest() throws RequestException {
        requestSession.addResponse(400);
        client.getToken("some channel");

        assertEquals("https://example.com/api/auth/device", requestSession.getLastRequest().getUrl().toString());
        assertEquals("GET", requestSession.getLastRequest().getMethod());
        assertEquals(new RequestAuth.BearerToken("VWtkZq18HZM3GWzD/q27qPSVszysSyoQfQ6tDEAcAko=\n"), requestSession.getLastRequest().getAuth());
        assertEquals("some channel", requestSession.getLastRequest().getHeaders().get("X-UA-Channel-ID"));
    }

    @Test(expected = RequestException.class)
    public void testInvalidResponse() throws RequestException {
        requestSession.addResponse(200, "what");
        client.getToken("some channel");
    }

}
