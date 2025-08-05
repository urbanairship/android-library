package com.urbanairship.audience

import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestApplication
import com.urbanairship.UAirship
import com.urbanairship.cache.AirshipCache
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMatcher
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.util.UAStringUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Arrays
import java.util.Locale
import junit.framework.TestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AudienceSelectorTest {

    private val infoProvider: DeviceInfoProvider = mockk()
    private val hashChecker = HashChecker(AirshipCache(
        context = ApplicationProvider.getApplicationContext(),
        runtimeConfig = TestAirshipRuntimeConfig(),
        isPersistent = false
    ))

    @Test
    @Throws(JsonException::class)
    public fun testJson() {
        val original =
            AudienceSelector.newBuilder()
                .addLanguageTag("en-US")
                .setNewUser(true)
                .setLocationOptIn(false)
                .setNotificationsOptIn(true)
                .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                .setTagSelector(DeviceTagSelector.tag("some tag")).addTestDevice("cool story")
                .setMissBehavior(AudienceSelector.MissBehavior.CANCEL).build()
        val fromJson = AudienceSelector.fromJson(original.toJsonValue())
        assertEquals(original, fromJson)
        assertEquals(original.hashCode(), fromJson.hashCode())
    }

    @Test
    public fun testAndroidVersionMatcher() {
        TestApplication.getApplication().setPlatform(UAirship.Platform.ANDROID)
        val audience = AudienceSelector.newBuilder()
                .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                .build()

        val predicate = JsonPredicate.newBuilder()
            .addMatcher(JsonMatcher.newBuilder()
                .setKey("version")
                .setScope("android")
                .setValueMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
                .build())
            .build()

        assertEquals(predicate, audience.versionPredicate)
    }

    @Test
    public fun testAmazonVersionMatcher() {
        TestApplication.getApplication().setPlatform(UAirship.Platform.AMAZON)
        val audience = AudienceSelector.newBuilder()
            .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
            .build()

        val predicate = JsonPredicate.newBuilder()
            .addMatcher(JsonMatcher.newBuilder()
                .setKey("version")
                .setScope("amazon")
                .setValueMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0)).build()
        ).build()

        assertEquals(predicate, audience.versionPredicate)
    }

    @Test(expected = NullPointerException::class)
    @Throws(JsonException::class)
    public fun testNotValidMissBehavior() {
        val original = AudienceSelector.newBuilder()
            .addLanguageTag("en-US")
            .setNewUser(true)
            .setLocationOptIn(false)
            .setNotificationsOptIn(true)
            .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 100.0))
            .setTagSelector(DeviceTagSelector.tag("some tag")).addTestDevice("cool story")
            .setMissBehavior(AudienceSelector.MissBehavior.parse("bad behavior")!!).build()

        AudienceSelector.fromJson(original.toJsonValue())
    }

    @Test
    public fun testEmptyAudience(): TestResult = runTest {
        val audience = AudienceSelector.newBuilder().build()
        TestCase.assertTrue(checkAudience(audience))
    }

    @Test
    public fun testNotificationOptIn(): TestResult = runTest {

        val requireOptIn = AudienceSelector.newBuilder().setNotificationsOptIn(true).build()
        val requireOptOut = AudienceSelector.newBuilder().setNotificationsOptIn(false).build()

        every { infoProvider.isNotificationsOptedIn } returns true

        TestCase.assertTrue(checkAudience(requireOptIn))
        TestCase.assertFalse(checkAudience(requireOptOut))

        every { infoProvider.isNotificationsOptedIn } returns false
        TestCase.assertFalse(checkAudience(requireOptIn))
        TestCase.assertTrue(checkAudience(requireOptOut))
    }

    @Test
    public fun testLocationOptInRequired(): TestResult = runTest {
        coEvery { infoProvider.getPermissionStatuses() } returns emptyMap()

        val requiresOptIn = AudienceSelector.newBuilder().setLocationOptIn(true).build()
        TestCase.assertFalse(checkAudience(requiresOptIn))

        coEvery { infoProvider.getPermissionStatuses() } returns mapOf(Permission.LOCATION to PermissionStatus.DENIED)

        TestCase.assertFalse(checkAudience(requiresOptIn))

        coEvery { infoProvider.getPermissionStatuses() } returns mapOf(Permission.LOCATION to PermissionStatus.GRANTED)
        TestCase.assertTrue(checkAudience(requiresOptIn))

        coEvery { infoProvider.getPermissionStatuses() } returns mapOf(Permission.LOCATION to PermissionStatus.NOT_DETERMINED)
        TestCase.assertFalse(checkAudience(requiresOptIn))
    }

    @Test
    public fun testPermissionsPredicate(): TestResult = runTest {
        val predicate = JsonPredicate.newBuilder()
            .addMatcher(JsonMatcher.newBuilder()
                .setKey(Permission.DISPLAY_NOTIFICATIONS.value)
                .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("granted")))
                .build())
            .build()

        val audience = AudienceSelector.newBuilder().setPermissionsPredicate(predicate).build()

        // Not set
        coEvery { infoProvider.getPermissionStatuses() } returns emptyMap()
        TestCase.assertFalse(checkAudience(audience))

        coEvery { infoProvider.getPermissionStatuses() } returns mapOf(Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.DENIED)

        TestCase.assertFalse(checkAudience(audience))

        coEvery { infoProvider.getPermissionStatuses() } returns mapOf(Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.GRANTED)
        TestCase.assertTrue(checkAudience(audience))

        coEvery { infoProvider.getPermissionStatuses() } returns mapOf(Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.NOT_DETERMINED)
        TestCase.assertFalse(checkAudience(audience))
    }

    @Test
    public fun testRequiresAnalyticsTrue(): TestResult = runTest {
        every { infoProvider.analyticsEnabled } returns false

        val audienceOptIn = AudienceSelector.newBuilder().setRequiresAnalytics(true).build()
        val audienceOptOut = AudienceSelector.newBuilder().setRequiresAnalytics(false).build()
        TestCase.assertFalse(checkAudience(audienceOptIn))
        TestCase.assertTrue(checkAudience(audienceOptOut))

        every { infoProvider.analyticsEnabled } returns true
        TestCase.assertTrue(checkAudience(audienceOptIn))
        TestCase.assertTrue(checkAudience(audienceOptOut))

        every { infoProvider.analyticsEnabled } returns false
        TestCase.assertFalse(checkAudience(audienceOptIn))
        TestCase.assertTrue(checkAudience(audienceOptOut))
    }

    @Test
    public fun testNewUser(): TestResult = runTest {
        val requiresNewUser = AudienceSelector.newBuilder().setNewUser(true).build()
        val requiresExistingUser = AudienceSelector.newBuilder().setNewUser(false).build()

        every { infoProvider.installDateMilliseconds } returns 2

        TestCase.assertFalse(checkAudience(requiresNewUser, timestamp = 3))
        TestCase.assertTrue(checkAudience(requiresExistingUser, timestamp = 3))

        TestCase.assertTrue(checkAudience(requiresNewUser, timestamp = 1))
        TestCase.assertFalse(checkAudience(requiresExistingUser, timestamp = 1))
    }

    @Test
    public fun testTestDevices(): TestResult = runTest {

        val bytes = Arrays.copyOf(UAStringUtil.sha256Digest("test channel"), 16)
        val testDevice = Base64.encodeToString(bytes, Base64.DEFAULT)

        val withTestDevice = AudienceSelector.newBuilder().addTestDevice(testDevice).build()
        val otherTestDevice = AudienceSelector.newBuilder().addTestDevice(UAStringUtil.sha256("some other channel")!!).build()

        coEvery { infoProvider.getChannelId() } returns "test channel"
        every { infoProvider.channelCreated } returns true

        TestCase.assertTrue(checkAudience(withTestDevice))
        TestCase.assertFalse(checkAudience(otherTestDevice))
    }

    @Test
    public fun testTagSelector(): TestResult = runTest {
        every { infoProvider.channelTags } returns emptySet()

        val audience = AudienceSelector.newBuilder().setTagSelector(DeviceTagSelector.tag("expected")).build()

        TestCase.assertFalse(checkAudience(audience))

        every { infoProvider.channelTags } returns setOf("expected")

        TestCase.assertTrue(checkAudience(audience))
    }

    @Test
    public fun testLocales(): TestResult = runTest {

        every { infoProvider.locale } returns Locale.forLanguageTag("en-US")

        var audience = AudienceSelector.newBuilder().addLanguageTag("en-US").build()

        TestCase.assertTrue(checkAudience(audience))

        audience = AudienceSelector.newBuilder().addLanguageTag("en-").build()
        TestCase.assertTrue(checkAudience(audience))

        audience = AudienceSelector.newBuilder().addLanguageTag("en_").build()
        TestCase.assertTrue(checkAudience(audience))

        audience = AudienceSelector.newBuilder().addLanguageTag("en").build()
        TestCase.assertTrue(checkAudience(audience))

        audience = AudienceSelector.newBuilder().addLanguageTag("fr").build()

        TestCase.assertFalse(checkAudience(audience))
    }

    @Test
    public fun testAppVersion(): TestResult = runTest {
        val audience = AudienceSelector.newBuilder()
            .setVersionMatcher(ValueMatcher.newNumberRangeMatcher(1.0, 2.0))
            .build()

        every { infoProvider.appVersionCode } returns 1

        TestCase.assertTrue(checkAudience(audience))

        every { infoProvider.appVersionCode } returns 2
        TestCase.assertTrue(checkAudience(audience))

        every { infoProvider.appVersionCode } returns 3
        TestCase.assertFalse(checkAudience(audience))
    }

    @Test
    public fun testDeviceTypes(): TestResult = runTest {
        every { infoProvider.platform } returns "android"
        val audience = AudienceSelector.newBuilder().setDeviceTypes(listOf("ios", "android")).build()
        assert(checkAudience(audience))
    }

    @Test
    public fun testDeviceTypesNoAndroid(): TestResult = runTest {
        every { infoProvider.platform } returns "android"
        val audience = AudienceSelector.newBuilder().setDeviceTypes(listOf("ios")).build()
        assertFalse(checkAudience(audience))
    }

    @Test
    public fun testDeviceTypesEmpty(): TestResult = runTest {
        every { infoProvider.platform } returns "android"
        val audience = AudienceSelector.newBuilder().setDeviceTypes(listOf()).build()
        assertFalse(checkAudience(audience))
    }

    private suspend fun checkAudience(audience: AudienceSelector, timestamp: Long = 0): Boolean {
        return audience.evaluate(timestamp, infoProvider, hashChecker).isMatch
    }
}
