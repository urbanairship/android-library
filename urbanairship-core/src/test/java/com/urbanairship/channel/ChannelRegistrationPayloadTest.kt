/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.push.PushProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ChannelRegistrationPayloadTest {

    private val testOptIn = true
    private val testBackgroundEnabled = true
    private val testDeviceType = ChannelRegistrationPayload.DeviceType.ANDROID
    private val testPushAddress = "gcmRegistrationId"
    private val testUserId = "fakeUserId"
    private val testSetTags = true
    private var testTags = setOf("tagOne", "tagTwo")
    private val testLanguage = "test_language"
    private val testTimezone = "test_timezone"
    private val testCountry = "test_country"

    private lateinit var payload: ChannelRegistrationPayload

    /**
     * Test that minimized payload doesn't include creation-specific data such as the APID and user id.
     */
    @Test
    public fun testMinimizedPayloadIgnoresCreationSpecificData() {
        payload = ChannelRegistrationPayload.Builder()
            .setUserId(testUserId)
            .build()

        val newPayload = ChannelRegistrationPayload.Builder(payload).build()
        val minPayload = newPayload.minimizedPayload(payload)

        assertNull(minPayload.userId)
    }

    /**
     * Test that the minimized payload includes optional fields if changed
     */
    @Test
    public fun testMinimizedPayloadIncludesOptionalFieldsIfChanged() {
        payload = ChannelRegistrationPayload.Builder()
            .setTags(testSetTags, testTags)
            .setLanguage(testLanguage)
            .setTimezone(testTimezone)
            .setCountry(testCountry)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .build()

        val newPayload = ChannelRegistrationPayload.Builder(payload)
            .setLanguage("newLanguage")
            .setTimezone("newTimezone")
            .setCountry("newCountry")
            .setTags(true, setOf("new", "tags"))
            .setLocationSettings(false)
            .setAppVersion("234")
            .setApiVersion(234)
            .setSdkVersion("2.3.4")
            .setDeviceModel("Other device model")
            .build()

        val minPayload = newPayload.minimizedPayload(payload)

        val expectedTagChanges = jsonMapOf(
            "add" to JsonValue.wrap(setOf("new", "tags")),
            "remove" to JsonValue.wrap(setOf("tagOne", "tagTwo"))
        )

        assertEquals("newLanguage", minPayload.language)
        assertEquals("newTimezone", minPayload.timezone)
        assertEquals("newCountry", minPayload.country)
        assertTrue(minPayload.setTags)
        assertEquals(HashSet(mutableListOf("new", "tags")), minPayload.tags)
        assertEquals(expectedTagChanges, minPayload.tagChanges)
        assertEquals(false, minPayload.locationSettings)
        assertEquals("234", minPayload.appVersion)
        assertEquals(234, minPayload.apiVersion as Any?)
        assertEquals("2.3.4", minPayload.sdkVersion)
        assertEquals("Other device model", minPayload.deviceModel)
    }

    /**
     * Test that the minimized payload ignores optional fields if unchanged
     */
    @Test
    public fun testMinimizedPayloadIgnoresOptionalFieldsIfUnchanged() {
        payload = ChannelRegistrationPayload.Builder()
            .setTags(testSetTags, testTags)
            .setLanguage(testLanguage)
            .setTimezone(testTimezone)
            .setCountry(testCountry)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .build()

        val newPayload = ChannelRegistrationPayload.Builder(payload).build()
        val minPayload = newPayload.minimizedPayload(payload)

        assertNull(minPayload.language)
        assertNull(minPayload.timezone)
        assertNull(minPayload.country)
        assertFalse(minPayload.setTags)
        assertNull(minPayload.tags)
        assertNull(minPayload.tagChanges)
        assertNull(minPayload.locationSettings)
        assertNull(minPayload.appVersion)
        assertNull(minPayload.sdkVersion)
        assertNull(minPayload.apiVersion)
        assertNull(minPayload.deviceModel)
    }

    /**
     * Test that the minimized payload contains all required fields
     */
    @Test
    public fun testMinimizedPayloadContainsRequiredFields() {
        payload = ChannelRegistrationPayload.Builder()
            .setOptIn(testOptIn)
            .setBackgroundEnabled(testBackgroundEnabled)
            .setDeviceType(testDeviceType)
            .setPushAddress(testPushAddress)
            .build()

        val newPayload = ChannelRegistrationPayload.Builder(payload)
            .setOptIn(!payload.optIn)
            .setBackgroundEnabled(!payload.backgroundEnabled)
            .build()
        val minPayload = newPayload.minimizedPayload(payload)

        assertEquals(minPayload.optIn, newPayload.optIn)
        assertEquals(minPayload.backgroundEnabled, newPayload.backgroundEnabled)
        assertEquals(minPayload.deviceType, newPayload.deviceType)
        assertEquals(minPayload.pushAddress, newPayload.pushAddress)
    }

    /**
     * Test that when the last payload is null, the minimized payload is unchanged
     */
    @Test
    public fun testMinimizedPayloadWhenLastIsNull() {
        payload = ChannelRegistrationPayload.Builder()
            .setOptIn(testOptIn)
            .setBackgroundEnabled(testBackgroundEnabled)
            .setDeviceType(testDeviceType)
            .setPushAddress(testPushAddress)
            .setTags(testSetTags, testTags)
            .setUserId(testUserId)
            .setLanguage(testLanguage)
            .setTimezone(testTimezone)
            .setCountry(testCountry)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .build()

        val minPayload = payload.minimizedPayload(null)

        assertEquals(payload, minPayload)
    }

    /**
     * Test that the json has the full expected payload when analytics is enabled.
     */
    @Test
    public fun testAsJsonFullPayloadAnalyticsEnabled() {
        payload = ChannelRegistrationPayload.Builder()
            .setOptIn(testOptIn)
            .setBackgroundEnabled(testBackgroundEnabled)
            .setDeviceType(testDeviceType)
            .setPushAddress(testPushAddress)
            .setTags(testSetTags, testTags)
            .setUserId(testUserId)
            .setLanguage(testLanguage)
            .setTimezone(testTimezone)
            .setCountry(testCountry)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .build()

        val body = payload.toJsonValue().map

        // Top level fields
        val identityHints = body?.optionalMap(ChannelRegistrationPayload.IDENTITY_HINTS_KEY)
        val channel = body?.optionalMap(ChannelRegistrationPayload.CHANNEL_KEY)

        // Identity items
        assertEquals(
            "User ID should be fakeUserId.",
            identityHints?.get(ChannelRegistrationPayload.USER_ID_KEY)?.string,
            testUserId
        )


        // Channel specific items
        assertEquals(
            "Device type should be android.",
            testDeviceType,
            channel?.get(ChannelRegistrationPayload.DEVICE_TYPE_KEY)?.let(ChannelRegistrationPayload.DeviceType::fromJson)

        )
        assertEquals(
            "Opt in should be true.",
            channel?.get(ChannelRegistrationPayload.OPT_IN_KEY)?.getBoolean(!testOptIn),
            testOptIn
        )
        assertEquals(
            "Background flag should be true.",
            channel?.get(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY)?.getBoolean(!testBackgroundEnabled),
            testBackgroundEnabled
        )
        assertEquals(
            "Push address should be gcmRegistrationId.",
            channel?.get(ChannelRegistrationPayload.PUSH_ADDRESS_KEY)?.string,
            testPushAddress
        )
        assertEquals(
            "Set tags should be true.",
            channel?.get(ChannelRegistrationPayload.SET_TAGS_KEY)?.getBoolean(!testSetTags),
            testSetTags
        )
        assertTrue(
            "Tags should be present in payload",
            channel?.containsKey(ChannelRegistrationPayload.TAGS_KEY) == true
        )

        assertTrue(
            "Timezone should be in payload",
            channel?.containsKey(ChannelRegistrationPayload.TIMEZONE_KEY) == true
        )
        assertTrue(
            "Language should be in payload",
            channel?.containsKey(ChannelRegistrationPayload.LANGUAGE_KEY) == true
        )
        assertTrue(
            "Country should be in payload",
            channel?.containsKey(ChannelRegistrationPayload.COUNTRY_KEY) == true
        )

        // Check the tags within channel item
        assertEquals(
            testTags,
            channel?.get(ChannelRegistrationPayload.TAGS_KEY)?.list?.map { it.requireString() }?.toSet()
        )

        assertEquals(
            channel?.get(ChannelRegistrationPayload.LOCATION_SETTINGS_KEY)?.getBoolean(false), true
        )

        assertEquals("123", channel?.requireField<String>(ChannelRegistrationPayload.APP_VERSION_KEY))

        assertEquals(
            123,
            channel?.requireField<Int>(ChannelRegistrationPayload.API_VERSION_KEY)
        )

        assertEquals(
            "1.2.3",
            channel?.requireField<String>(ChannelRegistrationPayload.SDK_VERSION_KEY)
        )

        assertEquals(
            "Device model",
            channel?.requireField<String>(ChannelRegistrationPayload.DEVICE_MODEL_KEY)
        )
    }

    /**
     * Test when tags are empty.
     */
    @Test
    public fun testAsJsonEmptyTags() {
        // Create payload with empty tags
        val payload = ChannelRegistrationPayload.Builder()
            .setTags(testSetTags, emptySet())
            .build()
        val channel = payload.toJsonValue()
            .map
            ?.optionalMap(ChannelRegistrationPayload.CHANNEL_KEY)

        // Verify setTags is true in order for tags to be present in payload
        assertEquals(
            "Set tags should be true.",
            channel?.get(ChannelRegistrationPayload.SET_TAGS_KEY)?.getBoolean(!testSetTags),
            testSetTags
        )

        // Verify tags are present, but empty
        val tags = channel?.optionalList(ChannelRegistrationPayload.TAGS_KEY)
        assertTrue("Tags size should be 0.", tags?.isEmpty == true)
    }

    /**
     * Test that tags are not sent when setTags is false.
     */
    @Test
    public fun testAsJsonNoTags() {
        // Create payload with setTags is false
        val payload = ChannelRegistrationPayload.Builder()
            .setTags(false, testTags)
            .build()
        val channel = payload.toJsonValue().requireMap().optionalMap(ChannelRegistrationPayload.CHANNEL_KEY)

        // Verify setTags is present and is false
        assertEquals(
            "Set tags should be false.",
            channel?.opt(ChannelRegistrationPayload.SET_TAGS_KEY)?.getBoolean(true),
            false
        )

        // Verify tags are not present
        assertTrue(
            "Tags should not be present in payload.",
            channel?.containsKey(ChannelRegistrationPayload.TAGS_KEY) == false
        )
    }

    /**
     * Test that an empty identity hints section is not included.
     */
    @Test
    public fun testAsJsonEmptyIdentityHints() {
        // Create empty payload
        val payload = ChannelRegistrationPayload.Builder().build()
        val body = payload.toJsonValue().map

        // Verify the identity hints section is not included
        assertTrue(
            "Identity hints should not be present in payload.",
            body?.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY) == false
        )
    }

    /**
     * Test that an empty user ID is not included in the identity hints.
     */
    @Test
    public fun testAsJsonEmptyUserId() {
        // Create payload with empty userId
        val payload = ChannelRegistrationPayload.Builder()
            .setUserId("")
            .build()
        val body = payload.toJsonValue().map

        // Verify the identity hints section is not included
        assertTrue(
            "Identity hints should not be present in payload.",
            body?.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY) == false
        )
    }

    /**
     * Test an empty builder.
     */
    @Test
    public fun testEmptyBuilder() {
        // Create an empty payload
        val payload = ChannelRegistrationPayload.Builder().build()
        val body = payload.toJsonValue().map

        // Top level fields
        assertTrue(
            "Channel should be present in payload.",
            body?.containsKey(ChannelRegistrationPayload.CHANNEL_KEY) == true
        )
        assertTrue(
            "Identity hints should not be present in payload.",
            body?.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY) == false
        )

        // Channel specific items
        val channel = body?.optionalMap(ChannelRegistrationPayload.CHANNEL_KEY)
        assertTrue(
            "Opt in should be present in payload.",
            channel?.containsKey(ChannelRegistrationPayload.OPT_IN_KEY) == true
        )
        assertEquals(
            "Opt in should be false.",
            channel?.get(ChannelRegistrationPayload.OPT_IN_KEY)?.getBoolean(true),
            false
        )
        assertTrue(
            "Background flag should be present in payload.",
            channel?.containsKey(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY) == true
        )
        assertEquals(
            "Background flag should be false.",
            channel?.get(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY)?.getBoolean(true),
            false
        )
        assertTrue(
            "Push address should not be present in payload.",
            channel?.containsKey(ChannelRegistrationPayload.PUSH_ADDRESS_KEY) == false
        )
        assertTrue(
            "Set tags should be present in payload.",
            channel?.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY) == true
        )
        assertEquals(
            "Set tags should be false.",
            channel?.get(ChannelRegistrationPayload.SET_TAGS_KEY)?.getBoolean(true),
            false
        )
        assertTrue(
            "Tags should not be present in payload.",
            channel?.containsKey(ChannelRegistrationPayload.TAGS_KEY) == false
        )
    }

    /**
     * Test when payload is equal to itself
     */
    @Test
    public fun testPayloadEqualToItself() {
        payload = ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .build()

        assertEquals("Payload should be equal to itself.", payload, payload)
    }

    /**
     * Test when payloads are the same
     */
    @Test
    public fun testPayloadsEqual() {
        payload = ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .build()

        val payload2 = ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setLocationSettings(true)
                .setAppVersion("123")
                .setApiVersion(123)
                .setSdkVersion("1.2.3")
                .setDeviceModel("Device model")
                .build()

        assertEquals("Payloads should match.", payload, payload2)
        assertEquals(
            "The hashCode for the payloads should match.",
            payload.hashCode().toLong(),
            payload2.hashCode().toLong()
        )
    }

    /**
     * Test when payloads are not equal
     */
    @Test
    public fun testPayloadsNotEqual() {
        payload = ChannelRegistrationPayload.Builder()
            .setOptIn(testOptIn)
            .setDeviceType(testDeviceType)
            .setPushAddress(testPushAddress)
            .setTags(testSetTags, testTags)
            .setUserId(testUserId)
            .setBackgroundEnabled(testBackgroundEnabled)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .build()

        val emptyPayload = ChannelRegistrationPayload.Builder()
            .setOptIn(false)
            .setDeviceType(testDeviceType)
            .setPushAddress(null)
            .setTags(false, null)
            .setUserId(null)
            .setLocationSettings(false)
            .setAppVersion("234")
            .setApiVersion(234)
            .setSdkVersion("2.3.4")
            .setDeviceModel("Other device model")
            .setBackgroundEnabled(!testBackgroundEnabled)
            .build()

        assertFalse("Payloads should not match.", payload == emptyPayload)
        assertFalse(
            "The hashCode for the payloads should not match.",
            payload.hashCode() == emptyPayload.hashCode()
        )
    }

    /**
     * Test empty payloads are equal
     */
    @Test
    public fun testEmptyPayloadsEqual() {
        val payload1 = ChannelRegistrationPayload.Builder().build()
        val payload2 = ChannelRegistrationPayload.Builder().build()

        assertEquals("Payloads should match.", payload1, payload2)
        assertEquals(
            "The hashCode for the payloads should match.",
            payload1.hashCode().toLong(),
            payload2.hashCode().toLong()
        )
    }

    /**
     * Test payload created from JSON
     */
    @Test
    public fun testCreateFromJSON() {
        payload = ChannelRegistrationPayload.Builder()
            .setOptIn(testOptIn)
            .setDeviceType(testDeviceType)
            .setPushAddress(testPushAddress)
            .setTags(testSetTags, testTags)
            .setUserId(testUserId)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .build()

        val jsonPayload = ChannelRegistrationPayload.fromJson(payload.toJsonValue())
        assertEquals("Payloads should match.", payload, jsonPayload)
        assertEquals(
            "Payloads should match.", payload.hashCode().toLong(), jsonPayload.hashCode().toLong()
        )
    }

    /**
     * Test payload created from empty JSON
     */
    @Test(expected = JsonException::class)
    public fun testCreateFromEmptyJSON() {
        ChannelRegistrationPayload.fromJson(JsonValue.NULL)
    }

    @Test
    public fun testFromJsonNoTags() {
        payload = ChannelRegistrationPayload.Builder()
            .setOptIn(testOptIn)
            .setDeviceType(testDeviceType)
            .setPushAddress(testPushAddress)
            .setUserId(testUserId)
            .build()

        val jsonPayload = ChannelRegistrationPayload.fromJson(payload.toJsonValue())
        assertEquals("Payloads should match.", payload, jsonPayload)
        assertEquals(
            "Payloads should match.", payload.hashCode().toLong(), jsonPayload.hashCode().toLong()
        )
    }

    @Test
    public fun testFromJsonEmptyAlias() {
        payload = ChannelRegistrationPayload.Builder()
            .setOptIn(testOptIn)
            .setDeviceType(testDeviceType)
            .setPushAddress(testPushAddress)
            .setUserId(testUserId)
            .build()

        val jsonPayload = ChannelRegistrationPayload.fromJson(payload.toJsonValue())
        assertEquals("Payloads should match.", payload, jsonPayload)
        assertEquals(
            "Payloads should match.", payload.hashCode().toLong(), jsonPayload.hashCode().toLong()
        )
    }

    /**
     * Test payload created from JSON with Tag Changes
     */
    @Test
    @Throws(JsonException::class)
    public fun testCreateFromJSONWithTagChanges() {
        payload = ChannelRegistrationPayload.Builder()
            .setOptIn(testOptIn)
            .setDeviceType(testDeviceType)
            .setPushAddress(testPushAddress)
            .setTags(testSetTags, testTags)
            .setUserId(testUserId)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .build()

        val newPayload = ChannelRegistrationPayload.Builder(payload)
            .setLanguage("newLanguage")
            .setTimezone("newTimezone")
            .setCountry("newCountry")
            .setTags(true, setOf("new", "tags"))
            .setLocationSettings(false)
            .setAppVersion("234")
            .setApiVersion(234)
            .setSdkVersion("2.3.4")
            .setDeviceModel("Other device model")
            .build()

        val minPayload = newPayload.minimizedPayload(payload)

        val jsonPayload = ChannelRegistrationPayload.fromJson(minPayload.toJsonValue())
        assertEquals("Payloads should match.", minPayload, jsonPayload)
        assertEquals(
            "Payloads should match.",
            minPayload.hashCode().toLong(),
            jsonPayload.hashCode().toLong()
        )
    }

    @Test
    public fun testDeliveryTypeAndroid() {
        payload = ChannelRegistrationPayload.Builder()
            .setDeviceType(ChannelRegistrationPayload.DeviceType.ANDROID)
            .setDeliveryType(PushProvider.DeliveryType.HMS)
            .build()

        val expected = jsonMapOf(
            "channel" to jsonMapOf(
                "set_tags" to false,
                "device_type" to "android",
                "opt_in" to false,
                "background" to false,
                "is_activity" to false,
                "android" to jsonMapOf("delivery_type" to "hms")
            )
        )

        assertEquals(expected, payload.toJsonValue())
    }

    @Test
    public fun testDeliveryTypeAmazon() {
        payload = ChannelRegistrationPayload.Builder()
            .setDeviceType(ChannelRegistrationPayload.DeviceType.AMAZON)
            .setDeliveryType(PushProvider.DeliveryType.ADM).build()

        val expected = jsonMapOf(
            "channel" to jsonMapOf(
                "set_tags" to false,
                "device_type" to "amazon",
                "opt_in" to false,
                "background" to false,
                "is_activity" to false
            )
        )

        assertEquals(expected, payload.toJsonValue())
    }

    /**
     * Test that the minimized payload includes all attribute fields if named user changes.
     */
    @Test
    public fun testMinimizedPayloadNamedUserChanges() {
        payload = ChannelRegistrationPayload.Builder()
            .setTags(true, testTags)
            .setLanguage(testLanguage)
            .setTimezone(testTimezone)
            .setCountry(testCountry)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .setContactId("contact id")
            .build()

        val newPayload = ChannelRegistrationPayload.Builder(payload)
            .setContactId("different contact id")
            .build()

        val minPayload = newPayload.minimizedPayload(payload)

        val expected = ChannelRegistrationPayload.Builder(payload)
            .setTags(false, null)
            .setContactId("different contact id")
            .build()

        assertEquals(expected, minPayload)
    }

    /**
     * Test that the minimized payload removes attribute fields if named user changes but is null.
     */
    @Test
    public fun testMinimizePayloadIfNamedUserIsNull() {
        payload = ChannelRegistrationPayload.Builder()
            .setTags(true, testTags)
            .setLanguage(testLanguage)
            .setTimezone(testTimezone)
            .setCountry(testCountry)
            .setLocationSettings(true)
            .setAppVersion("123")
            .setApiVersion(123)
            .setSdkVersion("1.2.3")
            .setDeviceModel("Device model")
            .setContactId("contact id")
            .setUserId("some-user")
            .build()

        val newPayload = ChannelRegistrationPayload.Builder(payload)
            .setContactId(null)
            .build()

        val minPayload = newPayload.minimizedPayload(payload)

        val expected = ChannelRegistrationPayload.Builder(payload)
            .setTags(false, null)
            .setContactId(null)
            .setDeviceType(null)
            .setLanguage(null)
            .setTimezone(null)
            .setCountry(null)
            .setLocationSettings(null)
            .setAppVersion(null)
            .setApiVersion(null)
            .setSdkVersion(null)
            .setDeviceModel(null)
            .setUserId(null)
            .build()

        assertEquals(expected, minPayload)
    }

    @Test
    public fun testEqualsIgnoreActive() {
        val active = ChannelRegistrationPayload.Builder()
            .setIsActive(true)
            .build()

        val inActive = ChannelRegistrationPayload.Builder()
            .setIsActive(false)
            .build()

        assertNotEquals(active, inActive)
        assertTrue(active.equals(inActive, false))
        assertFalse(active.equals(inActive, true))
    }
}
