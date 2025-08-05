package com.urbanairship

import com.urbanairship.http.RequestException
import org.junit.Assert.assertEquals
import org.junit.Test

class UAirshipPlatformTest {

    @Test
    fun testPlatformValues() {
        assertEquals(1, UAirship.Platform.AMAZON.rawValue)
        assertEquals(2, UAirship.Platform.ANDROID.rawValue)
        assertEquals(-1, UAirship.Platform.UNKNOWN.rawValue)
    }

    @Test
    fun testStringValue() {
        assertEquals("amazon", UAirship.Platform.AMAZON.stringValue)
        assertEquals("android", UAirship.Platform.ANDROID.stringValue)
        assertEquals("unknown", UAirship.Platform.UNKNOWN.stringValue)
    }

    @Test
    fun testDeviceType() {
        assertEquals("amazon", UAirship.Platform.AMAZON.deviceType)
        assertEquals("android", UAirship.Platform.ANDROID.deviceType)
    }

    @Test(expected = RequestException::class)
    fun testDeviceTypeUnknown() {
        // This should throw RequestException as "unknown" is not a valid device type
        UAirship.Platform.UNKNOWN.deviceType
    }

    @Test
    fun testFromRawValue() {
        assertEquals(UAirship.Platform.AMAZON, UAirship.Platform.fromRawValue(1))
        assertEquals(UAirship.Platform.ANDROID, UAirship.Platform.fromRawValue(2))
        assertEquals(UAirship.Platform.UNKNOWN, UAirship.Platform.fromRawValue(-1))
        assertEquals(
            UAirship.Platform.UNKNOWN,
            UAirship.Platform.fromRawValue(100)
        ) // Test with an invalid raw value
    }
}
