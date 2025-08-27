package com.urbanairship

import com.urbanairship.http.RequestException
import org.junit.Assert.assertEquals
import org.junit.Test

class AirshipPlatformTest {

    @Test
    fun testPlatformValues() {
        assertEquals(1, Airship.Platform.AMAZON.rawValue)
        assertEquals(2, Airship.Platform.ANDROID.rawValue)
        assertEquals(-1, Airship.Platform.UNKNOWN.rawValue)
    }

    @Test
    fun testStringValue() {
        assertEquals("amazon", Airship.Platform.AMAZON.stringValue)
        assertEquals("android", Airship.Platform.ANDROID.stringValue)
        assertEquals("unknown", Airship.Platform.UNKNOWN.stringValue)
    }

    @Test
    fun testDeviceType() {
        assertEquals("amazon", Airship.Platform.AMAZON.deviceType)
        assertEquals("android", Airship.Platform.ANDROID.deviceType)
    }

    @Test(expected = RequestException::class)
    fun testDeviceTypeUnknown() {
        // This should throw RequestException as "unknown" is not a valid device type
        Airship.Platform.UNKNOWN.deviceType
    }

    @Test
    fun testFromRawValue() {
        assertEquals(Airship.Platform.AMAZON, Airship.Platform.fromRawValue(1))
        assertEquals(Airship.Platform.ANDROID, Airship.Platform.fromRawValue(2))
        assertEquals(Airship.Platform.UNKNOWN, Airship.Platform.fromRawValue(-1))
        assertEquals(
            Airship.Platform.UNKNOWN,
            Airship.Platform.fromRawValue(100)
        ) // Test with an invalid raw value
    }
}
