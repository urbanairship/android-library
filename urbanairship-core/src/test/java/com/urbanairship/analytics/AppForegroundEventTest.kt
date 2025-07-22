/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.UAirship
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AppForegroundEventTest {

    private val event = AppForegroundEvent(1000)

    @Test
    public fun testTimezone() {
        EventTestUtils.validateEventValue(event, Event.TIME_ZONE_KEY, event.timezone)
    }

    @Test
    public fun testDaylightSavingsTime() {
        EventTestUtils.validateEventValue(
            event, Event.DAYLIGHT_SAVINGS_KEY, event.isDaylightSavingsTime
        )
    }

    @Test
    public fun testOsVersion() {
        EventTestUtils.validateEventValue(event, Event.OS_VERSION_KEY, Build.VERSION.RELEASE)
    }

    @Test
    public fun testLibVersion() {
        EventTestUtils.validateEventValue(event, Event.LIB_VERSION_KEY, UAirship.getVersion())
    }

    @Test
    public fun testPackageVersion() {
        EventTestUtils.validateEventValue(
            event, Event.PACKAGE_VERSION_KEY, UAirship.getPackageInfo()!!.versionName
        )
    }

    @Test
    public fun testPushId() {
        val conversionData = ConversionData("send id", null, null)
        Assert.assertEquals(
            event.getEventData(conversionData).require(Event.PUSH_ID_KEY).requireString(),
            "send id"
        )
    }

    @Test
    public fun testPushMetadata() {
        val conversionData = ConversionData(null, "metadata", null)
        Assert.assertEquals(
            event.getEventData(conversionData).require(Event.METADATA_KEY).requireString(),
            "metadata"
        )
    }

    /**
     * Tests that the last metadata is included in the app foreground event
     * data
     */
    @Test
    public fun testLastSendMetadata() {
        val conversionData = ConversionData(null, "metadata", "last metadata")
        Assert.assertEquals(
            event.getEventData(conversionData).require(Event.LAST_METADATA_KEY).requireString(),
            "last metadata"
        )
    }
}
