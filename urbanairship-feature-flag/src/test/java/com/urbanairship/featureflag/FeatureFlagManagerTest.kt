/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestApplication
import com.urbanairship.analytics.Analytics
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.experiment.ResolutionType
import com.urbanairship.experiment.TimeCriteria
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataPayload
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Clock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureFlagManagerTest {

    private val payloadType = "feature_flags"

    private val context: Context = TestApplication.getApplication()
    private val remoteData: RemoteData = mockk()
    private val analytics: Analytics = mockk()
    private val deferredResolver: FlagDeferredResolver = mockk()

    private lateinit var featureFlags: FeatureFlagManager
    private val infoProvider: DeviceInfoProvider = mockk()

    private var currentTime = 2L
    private var channelId = "test-channel"
    private var contactId = "contact-id"

    @Before
    fun setUp() {
        val clock: Clock = mockk()
        every { clock.currentTimeMillis() } answers { currentTime }

        featureFlags = FeatureFlagManager(
            context = context,
            dataStore = PreferenceDataStore.inMemoryStore(context),
            remoteData = remoteData,
            analytics = analytics,
            infoProvider = infoProvider,
            deferredResolver = deferredResolver,
            clock = clock
        )

        coEvery { infoProvider.getPermissionStatuses() } returns mapOf()
        coEvery { infoProvider.snapshot(any()) } returns infoProvider
        every { infoProvider.channelId } answers { channelId }
        coEvery { infoProvider.getStableContactId() } answers { contactId }
        every { infoProvider.appVersion } returns 1
        every { infoProvider.userCutOffDate(context) } returns 1
    }

    @Test
    fun testModuleIsWorking() {
        featureFlags.init()
        assert(featureFlags.isComponentEnabled)
    }

    @Test
    fun testFlagsEvaluationWorks(): TestResult = runTest {

        val audience = AudienceSelector.fromJson(
            jsonMapOf(
                "hash" to jsonMapOf(
                    "audience_hash" to jsonMapOf(
                        "hash_prefix" to "27f26d85-0550-4df5-85f0-7022fa7a5925:",
                        "num_hash_buckets" to 16384,
                        "hash_identifier" to "contact",
                        "hash_algorithm" to "farm_hash"
                    ),
                    "audience_subset" to jsonMapOf(
                        "min_hash_bucket" to 0, "max_hash_bucket" to 10090
                    ),
                )
            ).toJsonValue()
        )

        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        "test-id", "test-ff", audience = audience
                    )
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val flag = featureFlags.flag("test-ff").getOrThrow()

        val expected = FeatureFlag.createFlag("test-ff", true, createReportingInfo())
        assert(expected == flag)
    }

    @Test
    public fun testFeatureFlagWaitsToRefreshesRemoteData(): TestResult = runTest {

        nicelyMockStatusRefresh()
        coEvery { remoteData.payloads(payloadType) } returns listOf()

        featureFlags.flag("non-existing")

        coVerify { remoteData.waitForRefresh(RemoteDataSource.APP, 15000) }
    }

    @Test
    fun testNoFlags(): TestResult = runTest {
        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf()

        val flag = featureFlags.flag("delusional").getOrThrow()

        val expected = FeatureFlag.createMissingFlag("delusional")
        assert(expected == flag)
    }

    @Test
    fun testFlagNoAudience(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        "test-id", "no-audience-flag"
                    )
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val flag = featureFlags.flag("no-audience-flag").getOrThrow()
        val expected = FeatureFlag.createFlag("no-audience-flag", true, createReportingInfo())

        assert(expected == flag)
    }

    @Test
    fun testFlagAudienceNotMatch(): TestResult = runTest {
        val audience = generateAudience(true)

        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        "test-id", "unmatched", audience = audience
                    )
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val flag = featureFlags.flag("unmatched").getOrThrow()
        val expected = FeatureFlag.createFlag("unmatched", false, createReportingInfo())
        assert(expected == flag)
    }

    @Test
    fun testMultipleFeatureFlagsWithSameName(): TestResult = runTest {

        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        "test-id", "same-name", audience = generateAudience(true)
                    ), generateFeatureFlagPayload(
                        flagId = "test-id-2",
                        flagName = "same-name",
                        audience = generateAudience(false),
                        variablesType = FeatureFlagVariablesType.VARIANTS,
                        variables = listOf(
                            VariablesVariant(
                                "fake",
                                generateAudience(false),
                                jsonMapOf("reporting" to "data"),
                                jsonMapOf("sample" to "data")
                            )
                        )
                    )
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val flag = featureFlags.flag("same-name").getOrThrow()
        val expected = FeatureFlag.createFlag(
            "same-name",
            true,
            createReportingInfo(jsonMapOf("reporting" to "data")),
            jsonMapOf("sample" to "data")
        )

        assert(expected == flag)
    }

    @Test
    fun testMultipleFeatureFlagsWithSameNameNoMatch(): TestResult = runTest {

        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        flagId = "test-id",
                        flagName = "same-name",
                        audience = generateAudience(true),
                        reportingMetadata = jsonMapOf("flag" to "first")
                    ), generateFeatureFlagPayload(
                        flagId = "test-id-2",
                        flagName = "same-name",
                        audience = generateAudience(true),
                        reportingMetadata = jsonMapOf("flag" to "second"),
                        variablesType = FeatureFlagVariablesType.VARIANTS,
                        variables = listOf(
                            VariablesVariant(
                                "fake",
                                generateAudience(false),
                                jsonMapOf("reporting" to "data"),
                                jsonMapOf("sample" to "data")
                            )
                        )
                    )
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val flag = featureFlags.flag("same-name").getOrThrow()
        val expected = FeatureFlag.createFlag(
            "same-name", false, createReportingInfo(jsonMapOf("flag" to "second"))
        )

        assert(expected == flag)
    }

    @Test
    fun testVariantVariablesNotMatch(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        "test-id", "same-name", audience = generateAudience(true)
                    ), generateFeatureFlagPayload(
                        flagId = "test-id-2",
                        flagName = "same-name",
                        audience = generateAudience(false),
                        variablesType = FeatureFlagVariablesType.VARIANTS,
                        variables = listOf(
                            VariablesVariant(
                                "fake",
                                generateAudience(true),
                                jsonMapOf("reporting" to "data"),
                                jsonMapOf("sample" to "data")
                            )
                        )
                    )
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val flag = featureFlags.flag("same-name").getOrThrow()
        val expected = FeatureFlag.createFlag(
            "same-name", true, createReportingInfo(), null
        )

        assert(expected == flag)
    }

    @Test
    fun testDeferredIgnored(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        "test-id", "deferred", resolutionType = ResolutionType.DEFERRED
                    ),
                )
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val actual = featureFlags.flag("deferred").getOrThrow()
        val expected = FeatureFlag.createMissingFlag("deferred")
        assert(expected == actual)
    }

    @Test
    fun featureFlagRespectsTimeCriteria(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        flagId = "test-id", flagName = "timed", timeCriteria = TimeCriteria(1, 3)
                    ),
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        val expectedReportingInfo = createReportingInfo()

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.UP_TO_DATE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        // not started
        currentTime = 1
        var flag = featureFlags.flag("timed").getOrThrow()
        assert(
            FeatureFlag.createMissingFlag("timed") == flag
        )

        // started
        currentTime = 2
        flag = featureFlags.flag("timed").getOrThrow()
        assert(
            FeatureFlag.createFlag("timed", true, expectedReportingInfo) == flag
        )

        // outdated
        currentTime = 4
        flag = featureFlags.flag("timed").getOrThrow()

        assert(
            FeatureFlag.createMissingFlag("timed") == flag
        )
    }

    @Test
    fun testStaleNotDefined(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        flagId = "test-id", flagName = "stale"
                    ),
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.waitForRefresh(any(), any()) } just runs
        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.STALE
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val actual = featureFlags.flag("stale").getOrThrow()
        assert(
            FeatureFlag.createFlag("stale", true, createReportingInfo()) == actual
        )
    }

    @Test
    fun testStaleAllowed(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        flagId = "test-id",
                        flagName = "stale",
                        evaluationOptions = EvaluationOptions(false, null)
                    ),
                )
            ), remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.STALE
        coEvery { remoteData.waitForRefresh(any(), any()) } just runs
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val actual = featureFlags.flag("stale").getOrThrow()
        assert(
            FeatureFlag.createFlag("stale", true, createReportingInfo()) == actual
        )
    }

    @Test(expected = FeatureFlagException::class)
    fun testStaleNotAllowed(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        flagId = "test-id",
                        flagName = "stale",
                        evaluationOptions = EvaluationOptions(true, null)
                    ),
                )
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.STALE
        coEvery { remoteData.waitForRefresh(any(), any()) } just runs
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        featureFlags.flag("stale").getOrThrow()
    }

    @Test(expected = FeatureFlagException::class)
    fun testStaleNotAllowedMultipleFlags(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = payloadType, timestamp = 1L, data = jsonMapOf(
                payloadType to jsonListOf(
                    generateFeatureFlagPayload(
                        flagId = "test-id",
                        flagName = "stale",
                        evaluationOptions = EvaluationOptions(false, null)
                    ),
                    generateFeatureFlagPayload(
                        flagId = "test-id-2",
                        flagName = "stale",
                        evaluationOptions = EvaluationOptions(true, null)
                    ),
                )
            )
        )

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.STALE
        coEvery { remoteData.waitForRefresh(any(), any()) } just runs
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        featureFlags.flag("stale").getOrThrow()
    }

    @Test(expected = FeatureFlagException::class)
    fun testOutdatedRemoteThrows(): TestResult = runTest {
        val data = RemoteDataPayload(type = payloadType, timestamp = 1L, data = jsonMapOf())

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } returns RemoteData.Status.OUT_OF_DATE
        coEvery { remoteData.waitForRefresh(any(), any()) } just runs
        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        featureFlags.flag("stale").getOrThrow()
    }

    @Test
    fun testTrackInteraction(): TestResult = runTest {
        every { analytics.addEvent(any()) } just runs

        val flag = FeatureFlag.createFlag("some-flag", true, createReportingInfo())
        featureFlags.trackInteraction(flag)

        verify {
            analytics.addEvent(withArg {
                // The event has a different time stamp so we are just comparing the data
                assert(it.eventData == FeatureFlagInteractionEvent(flag).data)
            })
        }
    }

    @Test
    fun testTrackInteractionFlagDoesNotExist(): TestResult = runTest {
        val flag = FeatureFlag(false, false, JsonMap.EMPTY_MAP)
        featureFlags.trackInteraction(flag)

        verify(exactly = 0) { analytics.addEvent(any()) }
    }

    @Test
    fun testTrackInteractionMissingReportingInfo(): TestResult = runTest {
        val flag = FeatureFlag.createMissingFlag("some-flag")
        featureFlags.trackInteraction(flag)

        verify(exactly = 0) { analytics.addEvent(any()) }
    }

    private fun generateAudience(isNewUser: Boolean): AudienceSelector {
        return AudienceSelector.fromJson(
            jsonMapOf(
                "new_user" to isNewUser, "hash" to jsonMapOf(
                    "audience_hash" to jsonMapOf(
                        "hash_prefix" to "27f26d85-0550-4df5-85f0-7022fa7a5925:",
                        "num_hash_buckets" to 16384,
                        "hash_identifier" to "contact",
                        "hash_algorithm" to "farm_hash"
                    ),
                    "audience_subset" to jsonMapOf(
                        "min_hash_bucket" to 0, "max_hash_bucket" to 10090
                    ),
                )
            ).toJsonValue()
        )
    }

    private fun nicelyMockStatusRefresh() {
        val statuses =
            RemoteDataSource.values().associateWith { RemoteData.Status.STALE }.toMutableMap()

        coEvery { remoteData.status(eq(RemoteDataSource.APP)) } answers { statuses[firstArg()]!! }
        coEvery { remoteData.waitForRefresh(eq(RemoteDataSource.APP), eq(15000)) } just runs
    }

    private suspend fun createReportingInfo(reportingMetadata: JsonMap? = null): FeatureFlag.ReportingInfo {
        return FeatureFlag.ReportingInfo(
            reportingMetadata = reportingMetadata
                ?: jsonMapOf("flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925"),
            contactId = infoProvider.getStableContactId(),
            channelId = infoProvider.channelId
        )
    }

    private fun generateFeatureFlagPayload(
        flagId: String,
        flagName: String,
        resolutionType: ResolutionType = ResolutionType.STATIC,
        audience: AudienceSelector? = null,
        variablesType: FeatureFlagVariablesType = FeatureFlagVariablesType.FIXED,
        variables: List<VariablesVariant>? = null,
        reportingMetadata: JsonMap? = null,
        timeCriteria: TimeCriteria? = null,
        evaluationOptions: EvaluationOptions? = null
    ): JsonMap {
        return jsonMapOf("flag_id" to flagId,
            "created" to "2023-05-23T19:07:34.000",
            "last_updated" to "2023-05-23T19:07:35.000",
            "platforms" to jsonListOf("android"),
            "flag" to jsonMapOf("name" to flagName,
                "type" to resolutionType.jsonValue,
                "reporting_metadata" to (reportingMetadata
                    ?: jsonMapOf("flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925")),
                "audience_selector" to audience,
                "time_criteria" to timeCriteria,
                "variables" to jsonMapOf("type" to variablesType.jsonValue,
                    "variants" to variables?.map { it.toJsonValue() }?.let { JsonList(it) }),
                "evaluation_options" to evaluationOptions
            )
        )
    }
}
