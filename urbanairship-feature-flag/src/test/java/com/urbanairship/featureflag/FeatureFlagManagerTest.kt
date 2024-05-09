/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestApplication
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import java.util.Locale
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureFlagManagerTest {

    private val context: Context = TestApplication.getApplication()
    private val featureFlagAnalytics: FeatureFlagAnalytics = mockk()

    private val deferredResolver: FlagDeferredResolver = mockk()

    private var channelId = "test-channel"
    private var contactId = "contact-id"

    private val infoProvider: DeviceInfoProvider = mockk {
        coEvery { this@mockk.getPermissionStatuses() } returns mapOf()
        coEvery { this@mockk.snapshot(any()) } returns this@mockk
        every { this@mockk.channelId } answers { this@FeatureFlagManagerTest.channelId }
        coEvery { this@mockk.getStableContactId() } answers { contactId }
        every { this@mockk.appVersion } returns 1
        every { this@mockk.userCutOffDate(context) } returns 1
        every { this@mockk.getUserLocale(context) } returns Locale.US
        every { this@mockk.isNotificationsOptedIn } returns true
    }

    private val audienceMissedSelector = AudienceSelector.newBuilder().setNewUser(true).build()
    private val audienceMatchSelector = AudienceSelector.newBuilder().setNewUser(false).build()

    private val audienceEvaluator: AudienceEvaluator = mockk {
        coEvery { this@mockk.evaluate(audienceMissedSelector, any(), any()) } returns false
        coEvery { this@mockk.evaluate(audienceMatchSelector, any(), any()) } returns true
    }

    private val remoteDataAccess: FeatureFlagRemoteDataAccess = mockk()

    private val featureFlags = FeatureFlagManager(
        context = context,
        dataStore = PreferenceDataStore.inMemoryStore(context),
        audienceEvaluator = audienceEvaluator,
        remoteData = remoteDataAccess,
        infoProvider = infoProvider,
        deferredResolver = deferredResolver,
        featureFlagAnalytics = featureFlagAnalytics,
    )

    @Test
    fun testModuleIsWorking() {
        featureFlags.init()
        assert(featureFlags.isComponentEnabled)
    }

    @Test
    fun testNoFlags(): TestResult = runTest {
        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(emptyList())

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createMissingFlag(
            name = "test-ff"
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testStaticNoVariables(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.StaticPayload()
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            isEligible = true,
            reportingInfo = generateReportingInfo(flagInfo.reportingContext)
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testStaticVariantVariables(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.StaticPayload(
                variables = FeatureFlagVariables.Variant(
                    listOf(
                        VariablesVariant(
                            id = "1",
                            selector = audienceMissedSelector,
                            reportingMetadata = jsonMapOf("reporting" to "variable 1"),
                            data = jsonMapOf("var" to 1)
                        ),
                        VariablesVariant(
                            id = "2",
                            selector = audienceMatchSelector,
                            reportingMetadata = jsonMapOf("reporting" to "variable 2"),
                            data = jsonMapOf("var" to 2)
                        ),
                        VariablesVariant(
                            id = "3",
                            selector = audienceMatchSelector,
                            reportingMetadata = jsonMapOf("reporting" to "variable 3"),
                            data = jsonMapOf("var" to 3)
                        )
                    )
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = jsonMapOf("var" to 2),
            isEligible = true,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "variable 2"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testStaticVariantVariablesMissedLocalAudienceCheck(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            audience = audienceMissedSelector,
            payload = FeatureFlagPayload.StaticPayload(
                variables = FeatureFlagVariables.Variant(
                    listOf(
                        VariablesVariant(
                            id = "1",
                            selector = audienceMissedSelector,
                            reportingMetadata = jsonMapOf("reporting" to "variable 1"),
                            data = jsonMapOf("var" to 1)
                        ),
                        VariablesVariant(
                            id = "2",
                            selector = audienceMatchSelector,
                            reportingMetadata = jsonMapOf("reporting" to "variable 2"),
                            data = jsonMapOf("var" to 2)
                        ),
                        VariablesVariant(
                            id = "3",
                            selector = audienceMatchSelector,
                            reportingMetadata = jsonMapOf("reporting" to "variable 3"),
                            data = jsonMapOf("var" to 3)
                        )
                    )
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = jsonMapOf("var" to 2),
            isEligible = false,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "variable 2"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testStaticFixedVariables(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.StaticPayload(
                variables = FeatureFlagVariables.Fixed(
                    jsonMapOf("var" to 1)
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = jsonMapOf("var" to 1),
            isEligible = true,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "flag"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testStaticFixedVariablesMissedLocalAudienceCheck(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            audience = audienceMissedSelector,
            payload = FeatureFlagPayload.StaticPayload(
                variables = FeatureFlagVariables.Fixed(
                    jsonMapOf("var" to 1)
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = jsonMapOf("var" to 1),
            isEligible = false,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "flag"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testDeferredNoVariables(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.DeferredPayload(url = Uri.parse("example.com/flag"))
        )

        coEvery { deferredResolver.resolve(any(), flagInfo) } returns Result.success(
            DeferredFlag.Found(
                DeferredFlagInfo(
                    isEligible = true,
                    variables = null,
                    reportingMetadata = jsonMapOf("reporting" to "deferred")
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            isEligible = true,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "deferred"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testDeferredVariantVariables(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.DeferredPayload(url = Uri.parse("example.com/flag"))
        )

        coEvery { deferredResolver.resolve(any(), flagInfo) } returns Result.success(
            DeferredFlag.Found(
                DeferredFlagInfo(
                    isEligible = true,
                    variables = FeatureFlagVariables.Variant(
                        listOf(
                            VariablesVariant(
                                id = "1",
                                selector = audienceMissedSelector,
                                reportingMetadata = jsonMapOf("reporting" to "variable 1"),
                                data = jsonMapOf("var" to 1)
                            ),
                            VariablesVariant(
                                id = "2",
                                selector = audienceMatchSelector,
                                reportingMetadata = jsonMapOf("reporting" to "variable 2"),
                                data = jsonMapOf("var" to 2)
                            ),
                            VariablesVariant(
                                id = "3",
                                selector = audienceMissedSelector,
                                reportingMetadata = jsonMapOf("reporting" to "variable 3"),
                                data = jsonMapOf("var" to 3)
                            )
                        )
                    ),
                    reportingMetadata = jsonMapOf("reporting" to "deferred")
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = jsonMapOf("var" to 2),
            isEligible = true,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "variable 2"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testDeferredFixedVariables(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.DeferredPayload(url = Uri.parse("example.com/flag"))
        )

        coEvery { deferredResolver.resolve(any(), flagInfo) } returns Result.success(
            DeferredFlag.Found(
                DeferredFlagInfo(
                    isEligible = true,
                    variables = FeatureFlagVariables.Fixed(
                        jsonMapOf("var" to 1)
                    ),
                    reportingMetadata = jsonMapOf("reporting" to "deferred")
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = jsonMapOf("var" to 1),
            isEligible = true,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "deferred"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testDeferredNotEligible(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.DeferredPayload(url = Uri.parse("example.com/flag"))
        )

        coEvery { deferredResolver.resolve(any(), flagInfo) } returns Result.success(
            DeferredFlag.Found(
                DeferredFlagInfo(
                    isEligible = false,
                    variables = FeatureFlagVariables.Fixed(
                        jsonMapOf("var" to 1)
                    ),
                    reportingMetadata = jsonMapOf("reporting" to "deferred")
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = jsonMapOf("var" to 1),
            isEligible = false,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "deferred"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testDeferredLocalAudienceCheckMiss(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            audience = audienceMissedSelector,
            payload = FeatureFlagPayload.DeferredPayload(url = Uri.parse("example.com/flag"))
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = null,
            isEligible = false,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "flag"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testDeferredNoFlag(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.DeferredPayload(url = Uri.parse("example.com/flag"))
        )

        coEvery { deferredResolver.resolve(any(), flagInfo) } returns Result.success(
            DeferredFlag.NotFound
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createMissingFlag("test-ff")

        assertEquals(expected, flag)
    }

    @Test
    fun testDeferredError(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.DeferredPayload(url = Uri.parse("example.com/flag"))
        )

        coEvery { deferredResolver.resolve(any(), flagInfo) } returns Result.failure(
            Exception("test")
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val result = featureFlags.flag("test-ff")
        assertTrue(result.isFailure)
    }

    @Test
    fun testMultipleFlagsSameName(): TestResult = runTest {
        val flags = listOf(
            FeatureFlagInfo(
                id = "some-id_1",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag1"),
                audience = audienceMissedSelector,
                payload = FeatureFlagPayload.StaticPayload()
            ),
            FeatureFlagInfo(
                id = "some-id_2",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag2"),
                payload = FeatureFlagPayload.StaticPayload()
            ),
            FeatureFlagInfo(
                id = "some-id_3",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag3"),
                audience = audienceMissedSelector,
                payload = FeatureFlagPayload.StaticPayload()
            )
        )


        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(flags)

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            isEligible = true,
            reportingInfo = generateReportingInfo(flags[1].reportingContext)
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testMultipleFlagsSameNameNoMatch(): TestResult = runTest {
        val flags = listOf(
            FeatureFlagInfo(
                id = "some-id_1",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag1"),
                audience = audienceMissedSelector,
                payload = FeatureFlagPayload.StaticPayload()
            ),
            FeatureFlagInfo(
                id = "some-id_2",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                audience = audienceMissedSelector,
                reportingContext = jsonMapOf("reporting" to "flag2"),
                payload = FeatureFlagPayload.StaticPayload()
            ),
            FeatureFlagInfo(
                id = "some-id_3",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag3"),
                audience = audienceMissedSelector,
                payload = FeatureFlagPayload.StaticPayload()
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(flags)

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            isEligible = false,
            reportingInfo = generateReportingInfo(flags[2].reportingContext)
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testMultipleFlagsSameNameDeferred(): TestResult = runTest {
        val flags = listOf(
            FeatureFlagInfo(
                id = "some-id_1",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag1"),
                audience = audienceMissedSelector,
                payload = FeatureFlagPayload.StaticPayload()
            ),
            FeatureFlagInfo(
                id = "some-id_2",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag2"),
                payload = FeatureFlagPayload.DeferredPayload(url = Uri.EMPTY)
            ),
            FeatureFlagInfo(
                id = "some-id_3",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag3"),
                audience = audienceMissedSelector,
                payload = FeatureFlagPayload.StaticPayload()
            )
        )

        coEvery { deferredResolver.resolve(any(), flags[1]) } returns Result.success(
            DeferredFlag.Found(
                DeferredFlagInfo(
                    isEligible = false,
                    variables = FeatureFlagVariables.Fixed(
                        jsonMapOf("var" to 1)
                    ),
                    reportingMetadata = jsonMapOf("reporting" to "deferred")
                )
            )
        )

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns generateRemoteData(flags)

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        val flag = featureFlags.flag("test-ff").getOrThrow()
        val expected = FeatureFlag.createFlag(
            name = "test-ff",
            variables = jsonMapOf("var" to 1),
            isEligible = false,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "deferred"))
        )

        assertEquals(expected, flag)
    }

    @Test
    fun testStaleNotDefined(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.StaticPayload()
        )

        val payload = generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.waitForRemoteDataRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.STALE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns payload

        val result = featureFlags.flag("test-ff")
        assertTrue(result.isSuccess)

        coVerify(exactly = 0) {
            remoteDataAccess.waitForRemoteDataRefresh()
            remoteDataAccess.notifyOutOfDate(payload.remoteDataInfo)
        }
    }

    @Test
    fun testStaleNotAllowed(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.StaticPayload(),
            evaluationOptions = EvaluationOptions(disallowStaleValues = true, null)
        )

        val payload = generateRemoteData(listOf(flagInfo))

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.waitForRemoteDataRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.STALE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns payload

        val result = featureFlags.flag("test-ff")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.waitForRemoteDataRefresh()
        }
    }

    @Test
    fun testStaleNotAllowedSingleFlag(): TestResult = runTest {
        val flags = listOf(
            FeatureFlagInfo(
                id = "some-id_1",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag1"),
                payload = FeatureFlagPayload.StaticPayload()
            ),
            FeatureFlagInfo(
                id = "some-id_3",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag3"),
                audience = audienceMissedSelector,
                payload = FeatureFlagPayload.StaticPayload(),
                evaluationOptions = EvaluationOptions(disallowStaleValues = true, null)
            )
        )

        coEvery { deferredResolver.resolve(any(), flags[1]) } returns Result.success(
            DeferredFlag.Found(
                DeferredFlagInfo(
                    isEligible = false,
                    variables = FeatureFlagVariables.Fixed(
                        jsonMapOf("var" to 1)
                    ),
                    reportingMetadata = jsonMapOf("reporting" to "deferred")
                )
            )
        )

        val payload = generateRemoteData(flags)

        coEvery { remoteDataAccess.waitForRemoteDataRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.STALE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns payload

        val result = featureFlags.flag("test-ff")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.waitForRemoteDataRefresh()
        }
    }

    @Test
    fun testFlagOutOfDateRemoteDataFailedToRefresh(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.waitForRemoteDataRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.OUT_OF_DATE
        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload


        val result = featureFlags.flag("non-existing")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.waitForRemoteDataRefresh()
            remoteDataAccess.notifyOutOfDate(payload.remoteDataInfo)
        }
    }

    @Test
    fun testFlagOutOfDateRemoteDataSuccessRefresh(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.waitForRemoteDataRefresh() } just runs

        every { remoteDataAccess.status } returnsMany  listOf(RemoteData.Status.OUT_OF_DATE, RemoteData.Status.UP_TO_DATE)

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload


        val result = featureFlags.flag("non-existing")
        assertTrue(result.isSuccess)

        coVerify {
            remoteDataAccess.waitForRemoteDataRefresh()
            remoteDataAccess.notifyOutOfDate(payload.remoteDataInfo)
        }
    }

    @Test
    fun testFlagOutOfDateRemoteDataSuccessRefreshToStale(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.waitForRemoteDataRefresh() } just runs

        every { remoteDataAccess.status } returnsMany listOf(RemoteData.Status.OUT_OF_DATE, RemoteData.Status.STALE)

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload

        val result = featureFlags.flag("non-existing")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.waitForRemoteDataRefresh()
            remoteDataAccess.notifyOutOfDate(payload.remoteDataInfo)
        }
    }

    @Test
    fun testStaleDataNoFlag(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.waitForRemoteDataRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.STALE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload

        val result = featureFlags.flag("non-existing")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.waitForRemoteDataRefresh()
            remoteDataAccess.notifyOutOfDate(payload.remoteDataInfo)
        }
    }

    @Test
    fun testUpToDateNoFlag(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        every { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload

        val result = featureFlags.flag("non-existing")
        assertTrue(result.isSuccess)

        val flag = featureFlags.flag("non-existing").getOrThrow()
        val expected = FeatureFlag.createMissingFlag("non-existing")

        assertEquals(expected, flag)
    }

    @Test
    fun testTrackInteraction(): TestResult = runTest {
        every { featureFlagAnalytics.trackInteraction(any()) } just runs

        val flag = FeatureFlag.createFlag("some name", false, generateReportingInfo())
        featureFlags.trackInteraction(flag)

        verify(exactly = 1) { featureFlagAnalytics.trackInteraction(flag) }
    }


    private suspend fun generateReportingInfo(reportingMetadata: JsonMap? = null): FeatureFlag.ReportingInfo {
        return FeatureFlag.ReportingInfo(
            reportingMetadata = reportingMetadata
                ?: jsonMapOf("flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925"),
            contactId = infoProvider.getStableContactId(),
            channelId = infoProvider.channelId
        )
    }

    private fun generateRemoteData(flags: List<FeatureFlagInfo>): RemoteDataFeatureFlagInfo {
        return RemoteDataFeatureFlagInfo(
            flagInfoList = flags,
            remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )
    }
}
