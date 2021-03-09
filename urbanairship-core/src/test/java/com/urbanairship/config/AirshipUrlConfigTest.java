package com.urbanairship.config;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AirshipUrlConfigTest extends BaseTestCase {

    @Test
    public void testEmptyUrls() {
        AirshipUrlConfig urlConfig = AirshipUrlConfig.newBuilder().build();

        assertNull(urlConfig.deviceUrl().build());
        assertNull(urlConfig.walletUrl().build());
        assertNull(urlConfig.analyticsUrl().build());
        assertNull(urlConfig.remoteDataUrl().build());
    }

    @Test
    public void testDeviceUrl() throws MalformedURLException {
        AirshipUrlConfig urlConfig = AirshipUrlConfig.newBuilder()
                                                     .setDeviceUrl("https://test.example.com")
                                                     .build();

        assertEquals(new URL("https://test.example.com"), urlConfig.deviceUrl().build());
    }

    @Test
    public void testAnalyticsUrl() throws MalformedURLException {
        AirshipUrlConfig urlConfig = AirshipUrlConfig.newBuilder()
                                                     .setAnalyticsUrl("https://test.example.com")
                                                     .build();

        assertEquals(new URL("https://test.example.com"), urlConfig.analyticsUrl().build());
    }

    @Test
    public void testWalletUrl() throws MalformedURLException {
        AirshipUrlConfig urlConfig = AirshipUrlConfig.newBuilder()
                                                     .setWalletUrl("https://test.example.com")
                                                     .build();

        assertEquals(new URL("https://test.example.com"), urlConfig.walletUrl().build());
    }

    @Test
    public void testRemoteDataUrl() throws MalformedURLException {
        AirshipUrlConfig urlConfig = AirshipUrlConfig.newBuilder()
                                                     .setRemoteDataUrl("https://test.example.com")
                                                     .build();

        assertEquals(new URL("https://test.example.com"), urlConfig.remoteDataUrl().build());
    }
}
