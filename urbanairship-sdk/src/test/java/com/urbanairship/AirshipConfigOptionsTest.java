package com.urbanairship;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * This class tests the parsing of each type of configuration
 * value from a Java-style properties file.
 */
public class AirshipConfigOptionsTest extends BaseTestCase {
    public Context uaContext;
    static final String optionsTestPropertiesFile = "optionstest.properties";
    static final String validateIntegerValuesFile = "validateIntegerValues.properties";

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
        AirshipConfigOptions aco = new AirshipConfigOptions.Builder().applyProperties(uaContext, optionsTestPropertiesFile).build();

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
    }

    /**
     * This test verifies all public property fields have an annotation
     */
    @Test
    public void testPropertyNameAnnotation() {
        AirshipConfigOptions.Builder aco = new AirshipConfigOptions.Builder();
        List<AirshipConfigOptions.Builder> options = new ArrayList<>();
        options.add(aco);
        for (AirshipConfigOptions.Builder option : options) {
            option.applyProperties(uaContext, optionsTestPropertiesFile);
            Class<?> optionsClass = option.getClass();
            List<Field> fields = Arrays.asList(optionsClass.getFields());
            for (Field field : fields) {
                if (!(field.getType() instanceof Class)) {
                    PropertyName propertyAnnotation = (PropertyName) field.getAnnotation(PropertyName.class);
                    assertNotNull(propertyAnnotation);
                    assertEquals(propertyAnnotation.name(), field.getName());
                }
            }
        }
    }

    /**
     * This test loads invalid integer/enum values and verify the property value is set to default
     */
    @Test
    public void testValidateIntegerValues() {
        AirshipConfigOptions.Builder aco = new AirshipConfigOptions.Builder();
        aco.applyProperties(uaContext, validateIntegerValuesFile)
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret");
        assertEquals(Log.DEBUG, aco.build().developmentLogLevel);

        aco.setInProduction(true)
           .applyProperties(uaContext, validateIntegerValuesFile)
           .setProductionAppSecret("appSecret")
           .setProductionAppKey("appKey");
        assertEquals(Log.ERROR, aco.build().productionLogLevel);
    }
}
