package com.urbanairship

import com.urbanairship.http.RequestException
import org.junit.Assert.assertEquals
import org.junit.Test

public class AirshipPlatformTest {

    @Test
    public fun testPlatformValues() {
        assertEquals(1, Platform.AMAZON.rawValue)
        assertEquals(2, Platform.ANDROID.rawValue)
        assertEquals(-1, Platform.UNKNOWN.rawValue)
    }

    @Test
    public fun testStringValue() {
        assertEquals("amazon", Platform.AMAZON.stringValue)
        assertEquals("android", Platform.ANDROID.stringValue)
        assertEquals("unknown", Platform.UNKNOWN.stringValue)
    }

    @Test
    public fun testDeviceType() {
        assertEquals("amazon", Platform.AMAZON.deviceType)
        assertEquals("android", Platform.ANDROID.deviceType)
    }

    @Test(expected = RequestException::class)
    public fun testDeviceTypeUnknown() {
        // This should throw RequestException as "unknown" is not a valid device type
        Platform.UNKNOWN.deviceType
    }

    @Test
    public fun testFromRawValue() {
        assertEquals(Platform.AMAZON, Platform.fromRawValue(1))
        assertEquals(Platform.ANDROID, Platform.fromRawValue(2))
        assertEquals(Platform.UNKNOWN, Platform.fromRawValue(-1))
        assertEquals(
            Platform.UNKNOWN,
            Platform.fromRawValue(100)
        ) // Test with an invalid raw value
    }
}
