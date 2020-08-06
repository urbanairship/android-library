/* Copyright Airship and Contributors */

package com.urbanairship.automation.auth;

import com.urbanairship.TestClock;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class AuthManagerTest {

    private AuthManager authManager;
    private AuthApiClient mockClient;
    private AirshipChannel mockChannel;
    private TestClock clock;

    @Before
    public void setup() {
        this.mockChannel = mock(AirshipChannel.class);
        this.mockClient = mock(AuthApiClient.class);
        this.clock = new TestClock();

        this.authManager = new AuthManager(mockClient, mockChannel, clock);
    }

    @Test
    public void testGetTokenFromClient() throws RequestException, AuthException {
        when(mockChannel.getId()).thenReturn("channel id");

        when(mockClient.getToken("channel id"))
                .thenReturn(new Response.Builder<AuthToken>(200)
                        .setResult(new AuthToken("channel id", "some token", 100))
                        .build());

        assertEquals("some token", authManager.getToken());
    }

    @Test(expected = AuthException.class)
    public void testGetTokenFailed() throws RequestException, AuthException {
        when(mockChannel.getId()).thenReturn("channel id");

        when(mockClient.getToken("channel id"))
                .thenReturn(new Response.Builder<AuthToken>(400)
                        .build());

        authManager.getToken();
    }

    @Test
    public void testGetTokenExpired() throws RequestException, AuthException {
        clock.currentTimeMillis = 0;
        when(mockChannel.getId()).thenReturn("channel id");

        when(mockClient.getToken("channel id"))
                .thenReturn(new Response.Builder<AuthToken>(200)
                        .setResult(new AuthToken("channel id", "some token", 100))
                        .build())
                .thenReturn(new Response.Builder<AuthToken>(200)
                        .setResult(new AuthToken("channel id", "some other token", 200))
                        .build());

        assertEquals("some token", authManager.getToken());

        clock.currentTimeMillis = 99;
        assertEquals("some token", authManager.getToken());

        clock.currentTimeMillis = 100;
        assertEquals("some other token", authManager.getToken());
    }

    @Test
    public void testGetTokenChannelIdChanged() throws RequestException, AuthException {
        clock.currentTimeMillis = 0;
        when(mockChannel.getId()).thenReturn("channel id");
        when(mockClient.getToken("channel id"))
                .thenReturn(new Response.Builder<AuthToken>(200)
                        .setResult(new AuthToken("channel id", "some token", 100))
                        .build());

        assertEquals("some token", authManager.getToken());

        when(mockChannel.getId()).thenReturn("other channel id");
        when(mockClient.getToken("other channel id"))
                .thenReturn(new Response.Builder<AuthToken>(200)
                        .setResult(new AuthToken("other channel id", "some other token", 100))
                        .build());

        assertEquals("some other token", authManager.getToken());
    }

    @Test
    public void tokenExpired() throws RequestException, AuthException {
        clock.currentTimeMillis = 0;
        when(mockChannel.getId()).thenReturn("channel id");

        when(mockClient.getToken("channel id"))
                .thenReturn(new Response.Builder<AuthToken>(200)
                        .setResult(new AuthToken("channel id", "some token", 100))
                        .build())
                .thenReturn(new Response.Builder<AuthToken>(200)
                        .setResult(new AuthToken("channel id", "some other token", 200))
                        .build());

        assertEquals("some token", authManager.getToken());
        authManager.tokenExpired("some token");

        assertEquals("some other token", authManager.getToken());
    }

}
