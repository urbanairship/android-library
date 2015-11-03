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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * This class tests the parsing of each type of configuration
 * value from a Java-style properties file.
 */
public class AirshipConfigOptionsTest extends BaseTestCase {
    public Context uaContext;
    static final String optionsTestPropertiesFile = "optionstest.properties";
    static final String validateIntegerValuesFile = "validateIntegerValues.properties";

    public class GenericOptions extends AirshipConfigOptions {
        @PropertyName(name = "intValue")
        public int intValue = 1;

        @PropertyName(name = "maxIntValue")
        public int maxIntValue = 10;

        @PropertyName(name = "minIntValue")
        public int minIntValue = -10;

        @PropertyName(name = "longValue")
        public long longValue = 1;

        @PropertyName(name = "maxLongValue")
        public long maxLongValue = 100;

        @PropertyName(name = "minLongValue")
        public long minLongValue = -100;

        @PropertyName(name = "invalidLongValue")
        public long invalidLongValue = -1000;

        @PropertyName(name = "enumStringValue")
        @ConstantClass(name = "android.util.Log")
        public int enumStringValue = Log.DEBUG;

        @PropertyName(name = "lowerCaseEnumStringValue")
        @ConstantClass(name = "android.util.Log")
        public int lowerCaseEnumStringValue = Log.DEBUG;

        @PropertyName(name = "mixedCaseEnumStringValue")
        @ConstantClass(name = "android.util.Log")
        public int mixedCaseEnumStringValue = Log.DEBUG;

        @PropertyName(name = "invalidEnumStringValue")
        @ConstantClass(name = "android.util.Log")
        public int invalidEnumStringValue = Log.DEBUG;

        @PropertyName(name = "enumIntValue")
        @ConstantClass(name = "android.util.Log")
        public int enumIntValue = Log.DEBUG;

        @PropertyName(name = "noAnnotationString")
        public int noAnnotationString = 5;

        @PropertyName(name = "booleanValue")
        public boolean booleanValue = false;

        @PropertyName(name = "stringValue")
        public String stringValue = "";

        @Override
        public boolean isValid() {
            return false;
        }
    }

    GenericOptions o = new GenericOptions();

    @Before
    public void setUp() throws Exception {
        Application app = RuntimeEnvironment.application;
        this.uaContext = app.getApplicationContext();
    }

    /**
     * This test verifies the loadFromProperties method can parse different types
     */
    @Test
    public void testLoadFromProperties() {
        o.loadFromProperties(uaContext, optionsTestPropertiesFile);
        assertEquals(123456789, o.intValue);
        assertEquals(2147483647, o.maxIntValue);
        assertEquals(-2147483648, o.minIntValue);
        assertEquals(1234567890, o.longValue);
        assertEquals(-1000, o.invalidLongValue);
        assertEquals(Log.VERBOSE, o.enumStringValue);
        assertEquals(Log.VERBOSE, o.lowerCaseEnumStringValue);
        assertEquals(Log.VERBOSE, o.mixedCaseEnumStringValue);
        assertEquals(Log.DEBUG, o.invalidEnumStringValue);
        assertEquals(Log.WARN, o.enumIntValue);
        assertEquals(5, o.noAnnotationString);
        assertEquals(true, o.booleanValue);
        assertEquals("ABCDEFGHIJK", o.stringValue);
    }

    /**
     * This test verifies all public property fields have an annotation
     */
    @Test
    public void testPropertyNameAnnotation() {
        AirshipConfigOptions aco = new AirshipConfigOptions();
        List<AirshipConfigOptions> options = new ArrayList<>();
        options.add(aco);
        for (AirshipConfigOptions option : options) {
            option.loadFromProperties(uaContext, optionsTestPropertiesFile);
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
        AirshipConfigOptions aco = new AirshipConfigOptions();
        aco.loadFromProperties(uaContext, validateIntegerValuesFile);
        aco.isValid();
        assertEquals(Log.DEBUG, aco.developmentLogLevel);
        aco.inProduction = true;
        aco.isValid();
        assertEquals(Log.ERROR, aco.productionLogLevel);
    }
}
