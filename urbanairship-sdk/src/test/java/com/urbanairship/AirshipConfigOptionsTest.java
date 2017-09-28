/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This class tests the parsing of each type of configuration
 * value from a Java-style properties file.
 */
public class AirshipConfigOptionsTest extends BaseTestCase {
    public Context uaContext;
    static final String TEST_PROPERTIES_FILE = "valid.properties";
    static final String INVALID_PROPERTIES_FILE = "invalid.properties";

    @Before
    public void setUp() throws Exception {
        Application app = RuntimeEnvironment.application;
        this.uaContext = app.getApplicationContext();
    }

    /**
     * This test verifies the applyProperties method can parse different types
     */
    @Test
    public void testLoadFromProperties() {
        AirshipConfigOptions aco = new AirshipConfigOptions.Builder().applyProperties(uaContext, TEST_PROPERTIES_FILE).build();

        assertEquals("prodAppKey", aco.productionAppKey);
        assertEquals("prodAppSecret", aco.productionAppSecret);
        assertEquals("devAppKey", aco.developmentAppKey);
        assertEquals("devAppSecret", aco.developmentAppSecret);
        assertEquals("https://test.host.url.com/", aco.hostURL);
        assertEquals("https://test.analytics.url.com/", aco.analyticsServer);
        assertEquals("https://test.landingpage.url.com/", aco.landingPageContentURL);
        assertEquals("id", aco.gcmSender);
        assertArrayEquals(new String[] { "GCM_TRANSPORT" }, aco.allowedTransports);
        assertEquals("https://first.whitelist.url.com/", aco.whitelist[0]);
        assertEquals("https://second.whitelist.url.com/", aco.whitelist[1]);
        assertTrue(aco.inProduction);
        assertFalse(aco.analyticsEnabled);
        assertEquals(2700, aco.backgroundReportingIntervalMS);
        assertTrue(aco.clearNamedUser);
        assertEquals(Log.VERBOSE, aco.developmentLogLevel);
        assertEquals(Log.VERBOSE, aco.productionLogLevel);
        assertFalse(aco.autoLaunchApplication);
        assertTrue(aco.channelCreationDelayEnabled);
        assertFalse(aco.channelCaptureEnabled);
        assertEquals(aco.productionAppKey, aco.getAppKey());
        assertEquals(aco.productionAppSecret, aco.getAppSecret());
        assertEquals(Log.VERBOSE, aco.getLoggerLevel());
        assertEquals(R.drawable.ua_ic_urbanairship_notification, aco.notificationIcon);
        assertEquals(Color.parseColor("#ff0000"), aco.notificationAccentColor);
        assertEquals("https://test.wallet.url.com/", aco.walletUrl);
        assertEquals("test_channel", aco.notificationChannel);

    }

    /**
     * This test loads invalid values and verify the property value is set to default
     */
    @Test
    public void testInvalidOptions() {
        AirshipConfigOptions aco = new AirshipConfigOptions.Builder()
                .applyProperties(uaContext, INVALID_PROPERTIES_FILE)
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setProductionAppSecret("appSecret")
                .setProductionAppKey("appKey")
                .build();

        assertEquals(Log.DEBUG, aco.developmentLogLevel);
        assertEquals(Log.ERROR, aco.productionLogLevel);

        assertEquals(0, aco.notificationAccentColor);
        assertEquals(0, aco.notificationIcon);
    }

    @Test
    public void testDevelopmentFcmSenderId() {
        AirshipConfigOptions aco = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setProductionAppSecret("appSecret")
                .setProductionAppKey("appKey")
                .setInProduction(false)
                .setDevelopmentFcmSenderId("dev fcm sender ID")
                .setProductionFcmSenderId("prod fcm sender ID")
                .setFcmSenderId("fcm sender ID")
                .setGcmSender("deprecated gcm sender ID")
                .build();

        assertEquals("dev fcm sender ID", aco.getFcmSenderId());
    }

    @Test
    public void testProductionFcmSenderId() {
        AirshipConfigOptions aco = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setProductionAppSecret("appSecret")
                .setProductionAppKey("appKey")
                .setInProduction(true)
                .setDevelopmentFcmSenderId("dev fcm sender ID")
                .setProductionFcmSenderId("prod fcm sender ID")
                .setFcmSenderId("fcm sender ID")
                .setGcmSender("deprecated gcm sender ID")
                .build();

        assertEquals("prod fcm sender ID", aco.getFcmSenderId());
    }

    @Test
    public void testCommonFcmSenderId() {
        AirshipConfigOptions aco = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setProductionAppSecret("appSecret")
                .setProductionAppKey("appKey")
                .setInProduction(true)
                .setDevelopmentFcmSenderId("dev fcm sender ID")
                .setFcmSenderId("fcm sender ID")
                .setGcmSender("deprecated gcm sender ID")
                .build();

        assertEquals("fcm sender ID", aco.getFcmSenderId());


    }

    @Test
    public void testGcmSenderId() {
        AirshipConfigOptions aco = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setProductionAppSecret("appSecret")
                .setProductionAppKey("appKey")
                .setInProduction(true)
                .setDevelopmentFcmSenderId("dev fcm sender ID")
                .setGcmSender("deprecated gcm sender ID")
                .build();

        assertEquals("deprecated gcm sender ID", aco.getFcmSenderId());
    }
}
