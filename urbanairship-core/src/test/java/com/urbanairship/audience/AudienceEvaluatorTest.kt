/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.cache.AirshipCache
import com.urbanairship.contacts.StableContactInfo
import com.urbanairship.json.JsonMatcher
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AudienceEvaluatorTest {

    private val deviceInfo: DeviceInfoProvider = mockk(relaxed = true)
    private val audienceChecker = AudienceEvaluator(
        cache = AirshipCache(
            context = ApplicationProvider.getApplicationContext(),
            runtimeConfig = TestAirshipRuntimeConfig(),
            isPersistent = false
        )
    )

    private val stickyHash = AudienceHashSelector(
        hash = AudienceHash(
            prefix = "e66a2371-fecf-41de-9238-cb6c28a86cec:",
            property = HashIdentifiers.CONTACT,
            algorithm = HashAlgorithm.FARM,
            seed = 100,
            numberOfHashBuckets = 16384,
            overrides = null
        ),
        bucket = BucketSubset(min = 11600U, max = 13000U),
        sticky = AudienceSticky(
            id = "sticky ID",
            reportingMetadata = JsonValue.wrap("sticky reporting"),
            lastAccessTtl = 100.milliseconds
        )
    )

    private val stickyHashInverse = AudienceHashSelector(
        hash = AudienceHash(
            prefix = "e66a2371-fecf-41de-9238-cb6c28a86cec:",
            property = HashIdentifiers.CONTACT,
            algorithm = HashAlgorithm.FARM,
            seed = 100,
            numberOfHashBuckets = 16384,
            overrides = null
        ),
        bucket = BucketSubset(min = 0U, max = 11600U),
        sticky = AudienceSticky(
            id = "sticky ID",
            reportingMetadata = JsonValue.wrap("inverse sticky reporting"),
            lastAccessTtl = 100.milliseconds
        )
    )

    @Test
    public fun testEmptyAudience(): TestResult = runTest {
        assert(AudienceSelector.newBuilder().build(), isMatch = true)
    }

    @Test
    public fun testNewUserCondition(): TestResult = runTest {
        every { deviceInfo.installDateMilliseconds } returns 100L
        val audienceSelector = AudienceSelector.newBuilder().setNewUser(true).build()

        assert(
            audienceSelector = audienceSelector,
            newUserEvaluationDate = 100,
            isMatch = true
        )

        assert(
            audienceSelector = audienceSelector,
            newUserEvaluationDate = 99,
            isMatch = true
        )

        assert(
            audienceSelector = audienceSelector,
            newUserEvaluationDate = 101,
            isMatch = false
        )
    }

    @Test
    public fun testNotifiicationOptIn(): TestResult = runTest {
        var isOptedIn = false

        every { deviceInfo.isNotificationsOptedIn } answers { isOptedIn }

        val audienceSelector = AudienceSelector.newBuilder().setNotificationsOptIn(true).build()
        assert(audienceSelector = audienceSelector, isMatch = false)

        isOptedIn = true
        assert(audienceSelector = audienceSelector, isMatch = true)
    }

    @Test
    public fun testNotifiicationOptOut(): TestResult = runTest {
        var isOptedIn = true
        every { deviceInfo.isNotificationsOptedIn } answers { isOptedIn }

        val audienceSelector = AudienceSelector.newBuilder().setNotificationsOptIn(false).build()
        assert(audienceSelector, isMatch = false)

        isOptedIn = false
        assert(audienceSelector, isMatch = true)
    }

    @Test
    public fun testRequireAnalyticsTrue(): TestResult = runTest {
        var isAnalyticsEnabled = true
        every { deviceInfo.analyticsEnabled } answers { isAnalyticsEnabled }

        val audienceSelector = AudienceSelector.newBuilder().setRequiresAnalytics(true).build()
        assert(audienceSelector, isMatch = true)

        isAnalyticsEnabled = false
        assert(audienceSelector, isMatch = false)
    }

    @Test
    public fun testRequireAnalyticsFalse(): TestResult = runTest {
        var isAnalyticsEnabled = true
        every { deviceInfo.analyticsEnabled } answers { isAnalyticsEnabled }

        val audienceSelector = AudienceSelector.newBuilder().setRequiresAnalytics(false).build()
        assert(audienceSelector, isMatch = true)

        isAnalyticsEnabled = false
        assert(audienceSelector, isMatch = true)
    }

    @Test
    public fun testLocale(): TestResult = runTest {
        var locale = Locale.forLanguageTag("de")
        every { deviceInfo.locale } answers { locale }

        val audienceSelector = AudienceSelector.newBuilder()
            .addLanguageTag("fr")
            .addLanguageTag("en-CA")
            .build()

        assert(audienceSelector, isMatch = false)

        locale = Locale("en-GB")
        assert(audienceSelector, isMatch = false)

        locale = Locale("en")
        assert(audienceSelector, isMatch = false)

        locale = Locale.forLanguageTag("fr-FR")
        assert(audienceSelector, isMatch = true)

        locale = Locale.forLanguageTag("en-CA")
        assert(audienceSelector, isMatch = true)

        locale = Locale.forLanguageTag("en-CA-POSIX")
        assert(audienceSelector, isMatch = true)
    }

    @Test
    public fun testTags(): TestResult = runTest {
        var tags: Set<String> = emptySet()
        every { deviceInfo.channelTags } answers { tags }

        val audience = AudienceSelector
            .newBuilder()
            .setTagSelector(
                DeviceTagSelector.and(
                    DeviceTagSelector.tag("bar"),
                    DeviceTagSelector.tag("foo")
                )
            )
            .build()

        assert(audience, isMatch = false)

        tags = setOf("foo")
        assert(audience, isMatch = false)

        tags = setOf("foo", "bar")
        assert(audience, isMatch = true)
    }

    @Test
    public fun testTestDevices(): TestResult = runTest {
        var channelId = ""

        every { deviceInfo.channelCreated } returns true
        coEvery { deviceInfo.getChannelId() } answers { channelId }

        val audience = AudienceSelector
            .newBuilder()
            .addTestDevice("obIvSbh47TjjqfCrPatbXQ==\n") // test channel
            .build()

        assert(audience, isMatch = false)

        channelId = "wrong channnel"
        assert(audience, isMatch = false)

        channelId = "test channel"
        assert(audience, isMatch = true)
    }

    @Test
    public fun testVersion(): TestResult = runTest {

        var appVersion = 0L
        every { deviceInfo.appVersionCode } answers { appVersion }

        val audience = AudienceSelector
            .newBuilder()
            .setVersionPredicate(
                JsonPredicate.newBuilder()
                    .addMatcher(
                        JsonMatcher.newBuilder()
                            .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(3)))
                            .setScope(listOf("android", "version"))
                            .build()
                    )
                    .build()
            )
            .build()

        assert(audience, isMatch = false)

        appVersion = 1L
        assert(audience, isMatch = false)

        appVersion = 3L
        assert(audience, isMatch = true)
    }

    @Test
    public fun testPermissions(): TestResult = runTest {
        var permissions: Map<Permission, PermissionStatus> = mapOf()
        coEvery { deviceInfo.getPermissionStatuses() } answers { permissions }

        val audience = AudienceSelector
            .newBuilder()
            .setPermissionsPredicate(
                JsonPredicate
                    .newBuilder()
                    .addMatcher(
                        JsonMatcher
                            .newBuilder()
                            .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("granted")))
                            .setScope("display_notifications")
                            .build())
                    .build()
            )
            .build()

        assert(audience, isMatch = false)

        permissions = mapOf(Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.DENIED)
        assert(audience, isMatch = false)

        permissions = mapOf(Permission.DISPLAY_NOTIFICATIONS to PermissionStatus.GRANTED)
        assert(audience, isMatch = true)
    }

    @Test
    public fun testLocationOptIn(): TestResult = runTest {
        var permissions: Map<Permission, PermissionStatus> = mapOf()
        coEvery { deviceInfo.getPermissionStatuses() } answers { permissions }

        val audience = AudienceSelector.newBuilder().setLocationOptIn(true).build()
        assert(audience, isMatch = false)

        permissions = mapOf(Permission.LOCATION to PermissionStatus.DENIED)
        assert(audience, isMatch = false)

        permissions = mapOf(Permission.LOCATION to PermissionStatus.GRANTED)
        assert(audience, isMatch = true)
    }

    @Test
    public fun testLocationOptOut(): TestResult = runTest {
        var permissions: Map<Permission, PermissionStatus> = mapOf()
        coEvery { deviceInfo.getPermissionStatuses() } answers { permissions }

        val audience = AudienceSelector.newBuilder().setLocationOptIn(false).build()
        assert(audience, isMatch = true)

        permissions = mapOf(Permission.LOCATION to PermissionStatus.DENIED)
        assert(audience, isMatch = true)

        permissions = mapOf(Permission.LOCATION to PermissionStatus.GRANTED)
        assert(audience, isMatch = false)
    }

    @Test
    public fun testContactHash(): TestResult = runTest {
        var contactId = "not a match"
        coEvery { deviceInfo.getChannelId() } returns "not a match"
        coEvery { deviceInfo.getStableContactInfo() } answers { StableContactInfo(contactId, null) }

        val hash = AudienceHashSelector(
            hash = AudienceHash(
                prefix = "e66a2371-fecf-41de-9238-cb6c28a86cec:",
                property = HashIdentifiers.CONTACT,
                algorithm = HashAlgorithm.FARM,
                seed = 100,
                numberOfHashBuckets = 16384,
                overrides = null
            ),
            bucket = BucketSubset(min = 11600U, max = 13000U),
        )

        val audience = AudienceSelector.newBuilder().setAudienceHashSelector(hash).build()
        assert(audience, isMatch = false)

        contactId = "match"
        assert(audience, isMatch = true)
    }

    @Test
    public fun testChannelHash(): TestResult = runTest {
        val contactId = "not a match"
        var channelId = "not a match"
        coEvery { deviceInfo.getChannelId() } answers { channelId }
        coEvery { deviceInfo.getStableContactInfo() } answers { StableContactInfo(contactId, null) }

        val hash = AudienceHashSelector(
            hash = AudienceHash(
                prefix = "e66a2371-fecf-41de-9238-cb6c28a86cec:",
                property = HashIdentifiers.CHANNEL,
                algorithm = HashAlgorithm.FARM,
                seed = 100,
                numberOfHashBuckets = 16384,
                overrides = null
            ),
            bucket = BucketSubset(min = 11600U, max = 13000U),
        )

        val audience = AudienceSelector.newBuilder().setAudienceHashSelector(hash).build()
        assert(audience, isMatch = false)

        channelId = "match"
        assert(audience, isMatch = true)
    }

    @Test
    public fun testDeviceTypes(): TestResult = runTest {
        every { deviceInfo.platform } returns "android"

        val audienceMatch = AudienceSelector.newBuilder().setDeviceTypes(listOf("android", "ios")).build()
        assert(audienceMatch, isMatch = true)

        val audienceMismatch = AudienceSelector.newBuilder().setDeviceTypes(listOf("web", "ios")).build()
        assert(audienceMismatch, isMatch = false)
    }

    @Test
    public fun testEmtpyDeviceTypes(): TestResult = runTest {
        every { deviceInfo.platform } returns "android"

        val audience = AudienceSelector.newBuilder().setDeviceTypes(listOf()).build()
        assert(audience, isMatch = false)
    }

    @Test
    public fun testStickyHash(): TestResult = runTest(timeout = 5.minutes) {
        var contactId = "not a match"

        coEvery { deviceInfo.getChannelId() } returns UUID.randomUUID().toString()
        coEvery { deviceInfo.getStableContactInfo() } answers { StableContactInfo(contactId, null) }

        val audience = AudienceSelector.newBuilder().setAudienceHashSelector(stickyHash).build()

        val expectedMetadata = stickyHash.sticky!!.reportingMetadata!!
        assert(audience, isMatch = false, reportingMetadata = listOf(expectedMetadata))

        contactId = "match"
        assert(audience, isMatch = true, reportingMetadata = listOf(expectedMetadata))

        // Update sticky hash to swap matches
        val updatedAudience = AudienceSelector.newBuilder().setAudienceHashSelector(stickyHashInverse).build()

        // Should be the same results
        contactId = "not a match"
        assert(updatedAudience, isMatch = false, reportingMetadata = listOf(expectedMetadata))

        contactId = "match"
        assert(updatedAudience, isMatch = true, reportingMetadata = listOf(expectedMetadata))

        // New contacts should reevaluate
        contactId = "also is a match"
        assert(updatedAudience, isMatch = true, reportingMetadata = listOf(stickyHashInverse.sticky!!.reportingMetadata!!))
    }

    @Test
    public fun testORMatch(): TestResult = runTest {
        every { deviceInfo.analyticsEnabled } returns false

        val audience = CompoundAudienceSelector.Or(
            listOf(
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setRequiresAnalytics(false).build()),
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setRequiresAnalytics(true).build()),
            )
        )

        assert(audience, isMatch = true)
    }

    @Test
    public fun testORMiss(): TestResult = runTest {
        every { deviceInfo.analyticsEnabled } returns false
        every { deviceInfo.isNotificationsOptedIn } returns false

        val audience = CompoundAudienceSelector.Or(
            listOf(
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setRequiresAnalytics(true).build()),
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setNotificationsOptIn(true).build())
            )
        )

        assert(audience, isMatch = false)
    }

    @Test
    public fun testEmptyOR(): TestResult = runTest {
        val audience = CompoundAudienceSelector.Or(emptyList())
        assert(audience, isMatch = false)
    }

    @Test
    public fun testANDMatch(): TestResult = runTest {
        every { deviceInfo.analyticsEnabled } returns true
        every { deviceInfo.isNotificationsOptedIn } returns true

        val audience = CompoundAudienceSelector.And(
            listOf(
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setRequiresAnalytics(true).build()),
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setNotificationsOptIn(true).build())
            )
        )

        assert(audience, isMatch = true)
    }

    @Test
    public fun testANDMiss(): TestResult = runTest {
        every { deviceInfo.analyticsEnabled } returns false
        every { deviceInfo.isNotificationsOptedIn } returns true

        val audience = CompoundAudienceSelector.And(
            listOf(
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setRequiresAnalytics(true).build()),
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setNotificationsOptIn(true).build())
            )
        )

        assert(audience, isMatch = false)
    }

    @Test
    public fun testEmptyAND(): TestResult = runTest {
        val audience = CompoundAudienceSelector.And(emptyList())
        assert(audience, isMatch = true)
    }

    @Test
    public fun testNOT(): TestResult = runTest {
        every { deviceInfo.analyticsEnabled } returns false
        every { deviceInfo.isNotificationsOptedIn } returns true

        val audience = CompoundAudienceSelector.Not(
            CompoundAudienceSelector.And(
                listOf(
                    CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setRequiresAnalytics(true).build()),
                    CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setNotificationsOptIn(true).build())
                )
            )
        )

        assert(audience, isMatch = true)
    }

    @Test
    public fun testStickyHashShortCircuitOR(): TestResult = runTest {

        coEvery { deviceInfo.getChannelId() } returns UUID.randomUUID().toString()
        coEvery { deviceInfo.getStableContactInfo() } returns StableContactInfo("match", null)

        val stickyHashDiffID = stickyHash.copyWith(
            sticky = AudienceSticky(
                id = UUID.randomUUID().toString(),
                reportingMetadata = JsonValue.wrap(UUID.randomUUID().toString()),
                lastAccessTtl = 100.milliseconds
            )
        )

        // short circuits, only get the first one
        val audience = CompoundAudienceSelector.Or(
            listOf(
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setAudienceHashSelector(stickyHash).build()),
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setAudienceHashSelector(stickyHashDiffID).build())
            )
        )

        assert(audience, isMatch = true, reportingMetadata = listOf(stickyHash.sticky!!.reportingMetadata!!))
    }

    @Test
    public fun testStickyHashShortCircuitAND(): TestResult = runTest {
        coEvery { deviceInfo.getChannelId() } returns UUID.randomUUID().toString()
        coEvery { deviceInfo.getStableContactInfo() } returns StableContactInfo("match", null)

        val stickyHashDiffID = stickyHash.copyWith(
            sticky = AudienceSticky(
                id = UUID.randomUUID().toString(),
                reportingMetadata = JsonValue.wrap(UUID.randomUUID().toString()),
                lastAccessTtl = 100.milliseconds
            )
        )

        // short circuits, only get the first one
        val audience = CompoundAudienceSelector.And(
            listOf(
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setAudienceHashSelector(stickyHashInverse).build()),
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setAudienceHashSelector(stickyHashDiffID).build())
            )
        )

        assert(audience, isMatch = false, reportingMetadata = listOf(stickyHashInverse.sticky!!.reportingMetadata!!))
    }

    @Test
    public fun testStickyHashMultiple(): TestResult = runTest {
        coEvery { deviceInfo.getChannelId() } returns UUID.randomUUID().toString()
        coEvery { deviceInfo.getStableContactInfo() } returns StableContactInfo("match", null)

        val stickyHashDiffID = stickyHash.copyWith(
            sticky = AudienceSticky(
                id = UUID.randomUUID().toString(),
                reportingMetadata = JsonValue.wrap(UUID.randomUUID().toString()),
                lastAccessTtl = 100.milliseconds
            )
        )

        // short circuits, only get the first one
        val audience = CompoundAudienceSelector.And(
            listOf(
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setAudienceHashSelector(stickyHash).build()),
                CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setAudienceHashSelector(stickyHashDiffID).build())
            )
        )

        assert(audience,
            isMatch = true,
            reportingMetadata = listOf(
                stickyHash.sticky!!.reportingMetadata!!,
                stickyHashDiffID.sticky!!.reportingMetadata!!
        ))
    }

    private suspend fun assert(
        audienceSelector: AudienceSelector,
        newUserEvaluationDate: Long = 0,
        isMatch: Boolean,
        reportingMetadata: List<JsonValue>? = null
    ) {
        assert(
            compoundAudienceSelector = CompoundAudienceSelector.Atomic(audienceSelector),
            newUserEvaluationDate = newUserEvaluationDate,
            isMatch = isMatch,
            reportingMetadata = reportingMetadata
        )
    }

    private suspend fun assert(
        compoundAudienceSelector: CompoundAudienceSelector,
        newUserEvaluationDate: Long = 0,
        isMatch: Boolean,
        reportingMetadata: List<JsonValue>? = null
    ) {
        val result = audienceChecker.evaluate(compoundAudienceSelector, newUserEvaluationDate, deviceInfo)

        assertEquals(isMatch, result.isMatch)
        assertEquals(reportingMetadata, result.reportingMetadata)
    }
}

private fun AudienceHashSelector.copyWith(sticky: AudienceSticky): AudienceHashSelector {
    return AudienceHashSelector(this.hash, this.bucket, sticky)
}
