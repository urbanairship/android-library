package com.urbanairship.audience

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PendingResult
import com.urbanairship.PrivacyManager
import com.urbanairship.ShadowAirshipExecutorsLegacy
import com.urbanairship.TestApplication
import com.urbanairship.UAirship
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMatcher
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.util.UAStringUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Arrays
import junit.framework.TestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@Config(
    sdk = [28],
    shadows = [ShadowAirshipExecutorsLegacy::class],
    application = TestApplication::class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4::class)
public class AudienceSelectorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var infoProvider: DeviceInfoProvider
    private var notificationStatus = true
    private lateinit var channelTags: MutableSet<String>
    private var channelId = "channel-id"
    private var version: Long = 1
    private var contactIdGetter: suspend () -> String = { "contact-id" }
    private lateinit var permissions: MutableMap<Permission, PermissionStatus>
    private lateinit var privacyFeatures: MutableMap<Int, Boolean>

    @Before
    public fun setUp() {
        permissions = mutableMapOf()
        privacyFeatures = mutableMapOf()
        channelTags = mutableSetOf()

        val permissionManager: PermissionsManager = mockk()
        coEvery { permissionManager.configuredPermissions } returns permissions.keys

        every { permissionManager.checkPermissionStatus(any()) } answers {
            val key: Permission = firstArg()
            val value = permissions[key] ?: PermissionStatus.NOT_DETERMINED
            val result = PendingResult<PermissionStatus>()
            result.result = value
            result
        }

        infoProvider = DeviceInfoProviderImpl(
            { notificationStatus }, { privacyFeatures[it] ?: false }, { channelTags },
            { channelId }, { version }, permissionManager, contactIdGetter, "android")
    }

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
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM)
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
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM)
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

        TestCase.assertTrue(checkAudience(requireOptIn))
        TestCase.assertFalse(checkAudience(requireOptOut))

        notificationStatus = false
        TestCase.assertFalse(checkAudience(requireOptIn))
        TestCase.assertTrue(checkAudience(requireOptOut))
    }

    @Test
    public fun testLocationOptInRequired(): TestResult = runTest {
        val requiresOptIn = AudienceSelector.newBuilder().setLocationOptIn(true).build()
        TestCase.assertFalse(checkAudience(requiresOptIn))

        permissions.put(Permission.LOCATION, PermissionStatus.DENIED)
        TestCase.assertFalse(checkAudience(requiresOptIn))

        permissions.put(Permission.LOCATION, PermissionStatus.GRANTED)
        TestCase.assertTrue(checkAudience(requiresOptIn))

        permissions.put(Permission.LOCATION, PermissionStatus.NOT_DETERMINED)
        TestCase.assertFalse(checkAudience(requiresOptIn))
    }

    @Test
    public fun testPermissionsPredicate(): TestResult = runTest {
        val predicate = JsonPredicate.newBuilder()
            .addMatcher(JsonMatcher.newBuilder()
                .setKey(Permission.DISPLAY_NOTIFICATIONS.getValue())
                .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("granted")))
                .build())
            .build()

        val audience = AudienceSelector.newBuilder().setPermissionsPredicate(predicate).build()

        // Not set
        TestCase.assertFalse(checkAudience(audience))

        permissions[Permission.DISPLAY_NOTIFICATIONS] = PermissionStatus.DENIED
        TestCase.assertFalse(checkAudience(audience))

        permissions[Permission.DISPLAY_NOTIFICATIONS] = PermissionStatus.GRANTED
        TestCase.assertTrue(checkAudience(audience))

        permissions[Permission.DISPLAY_NOTIFICATIONS] = PermissionStatus.NOT_DETERMINED
        TestCase.assertFalse(checkAudience(audience))
    }

    @Test
    public fun testRequiresAnalyticsTrue(): TestResult = runTest {
        val audienceOptIn = AudienceSelector.newBuilder().setRequiresAnalytics(true).build()
        val audienceOptOut = AudienceSelector.newBuilder().setRequiresAnalytics(false).build()
        TestCase.assertFalse(checkAudience(audienceOptIn))
        TestCase.assertTrue(checkAudience(audienceOptOut))

        privacyFeatures[PrivacyManager.FEATURE_ANALYTICS] = true
        TestCase.assertTrue(checkAudience(audienceOptIn))
        TestCase.assertTrue(checkAudience(audienceOptOut))

        privacyFeatures[PrivacyManager.FEATURE_ANALYTICS] = false
        TestCase.assertFalse(checkAudience(audienceOptIn))
        TestCase.assertTrue(checkAudience(audienceOptOut))
    }

    @Test
    public fun testNewUser(): TestResult = runTest {
        val requiresNewUser = AudienceSelector.newBuilder().setNewUser(true).build()
        val requiresExistingUser = AudienceSelector.newBuilder().setNewUser(false).build()

        val packageManager = Shadows.shadowOf(TestApplication.getApplication().packageManager)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.firstInstallTime = 2

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

        channelId = "test channel"

        TestCase.assertTrue(checkAudience(withTestDevice))
        TestCase.assertFalse(checkAudience(otherTestDevice))
    }

    @Test
    public fun testTagSelector(): TestResult = runTest {
        privacyFeatures[PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES] = true
        val audience = AudienceSelector.newBuilder().setTagSelector(DeviceTagSelector.tag("expected")).build()

        TestCase.assertFalse(checkAudience(audience))

        channelTags.add("expected")
        TestCase.assertTrue(checkAudience(audience))
    }

    @Test
    public fun testLocales(): TestResult = runTest {
        /*
         * No easy way to set the locale in robolectric from the default `en-US`.
         * https://github.com/robolectric/robolectric/issues/3282
         */
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

        version = 1
        TestCase.assertTrue(checkAudience(audience))

        version = 2
        TestCase.assertTrue(checkAudience(audience))

        version = 3
        TestCase.assertFalse(checkAudience(audience))
    }

    @Test
    public fun testDeviceTypes(): TestResult = runTest {
        val audience = AudienceSelector.newBuilder().setDeviceTypes(listOf("ios", "android")).build()
        assert(checkAudience(audience))
    }

    @Test
    public fun testDeviceTypesNoAndroid(): TestResult = runTest {
        val audience = AudienceSelector.newBuilder().setDeviceTypes(listOf("ios")).build()
        assertFalse(checkAudience(audience))
    }

    @Test
    public fun testDeviceTypesEmpty(): TestResult = runTest {
        val audience = AudienceSelector.newBuilder().setDeviceTypes(listOf()).build()
        assertFalse(checkAudience(audience))
    }

    private suspend fun checkAudience(audience: AudienceSelector, timestamp: Long = 0, contactId: String? = null): Boolean {
        return audience.evaluate(context, timestamp, infoProvider, contactId)
    }
}
