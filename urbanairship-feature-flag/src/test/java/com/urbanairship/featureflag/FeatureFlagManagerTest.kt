/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.audience.AudienceEvaluator
import com.urbanairship.audience.AudienceResult
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.audience.CompoundAudienceSelector
import com.urbanairship.contacts.StableContactInfo
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import java.util.Locale
import java.util.UUID
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class FeatureFlagManagerTest {

    private val context: Context = TestApplication.getApplication()
    private val featureFlagAnalytics: FeatureFlagAnalytics = mockk()
    private val deferredResolver: FlagDeferredResolver = mockk()
    private val privacyManager: PrivacyManager = mockk {
        every { this@mockk.isEnabled(PrivacyManager.Feature.FEATURE_FLAGS) } returns true
    }

    private var channelId = "test-channel"
    private var contactId = "contact-id"

    private val infoProvider: DeviceInfoProvider = mockk {
        coEvery { this@mockk.getPermissionStatuses() } returns mapOf()
        coEvery { this@mockk.getChannelId() } answers { this@FeatureFlagManagerTest.channelId }
        coEvery { this@mockk.getStableContactInfo() } answers { StableContactInfo(contactId, null) }
        every { this@mockk.appVersionName } returns "1.0.0"
        every { this@mockk.installDateMilliseconds } returns 1
        every { this@mockk.locale } returns Locale.US
        every { this@mockk.isNotificationsOptedIn } returns true
    }

    private val audienceMissedSelector = AudienceSelector.newBuilder().setNewUser(true).build()
    private val audienceMatchSelector = AudienceSelector.newBuilder().setNewUser(false).build()

    private val audienceEvaluator: AudienceEvaluator = mockk {
        coEvery { this@mockk.evaluate(any(), any(), any()) } answers {

            var result = args[0] == null

            val compound: CompoundAudienceSelector = firstArg() ?: return@answers AudienceResult(result)

            if (compound is CompoundAudienceSelector.Atomic) {
                result = result || compound.audience == audienceMatchSelector
            } else if (compound is CompoundAudienceSelector.And) {
                result = result || compound.selectors.any {
                    when(it) {
                        is CompoundAudienceSelector.Atomic -> it.audience == audienceMatchSelector
                        else -> false
                    }
                }
            }

            AudienceResult(result)
        }
    }

    private val remoteDataAccess: FeatureFlagRemoteDataAccess = mockk {
        coEvery { bestEffortRefresh() } just runs
    }
    private val resultCache: FeatureFlagResultCache = mockk {
        coEvery { flag(any()) } returns null
    }

    private val featureFlags = FeatureFlagManager(
        context = context,
        dataStore = PreferenceDataStore.inMemoryStore(context),
        audienceEvaluator = audienceEvaluator,
        remoteData = remoteDataAccess,
        infoProviderFactory = { infoProvider },
        deferredResolver = deferredResolver,
        featureFlagAnalytics = featureFlagAnalytics,
        privacyManager = privacyManager,
        resultCache = resultCache
    )

    @Test
    public fun testNoFlags(): TestResult = runTest {
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
    public fun testFlagDisabledFeatureFlags(): TestResult = runTest {
        every { privacyManager.isEnabled(PrivacyManager.Feature.FEATURE_FLAGS) } returns false
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

        val result = featureFlags.flag("test-ff")
        assertTrue(result.isFailure)
    }

    @Test
    public fun testStaticNoVariables(): TestResult = runTest {
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
    public fun testStaticAudienceMatch(): TestResult = runTest {
        val flagInfo = FeatureFlagInfo(
            id = "some-id",
            created = 0,
            lastUpdated = 0,
            name = "test-ff",
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.StaticPayload(),
            audience = audienceMatchSelector,
            compoundAudienceSelector = FeatureFlagCompoundAudience(
                selector = CompoundAudienceSelector.Atomic(audienceMissedSelector)
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
            reportingInfo = generateReportingInfo(flagInfo.reportingContext)
        )

        assertEquals(expected, flag)
    }

    @Test
    public fun testStaticVariantVariables(): TestResult = runTest {
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
                            compoundAudienceSelector = null,
                            reportingMetadata = jsonMapOf("reporting" to "variable 1"),
                            data = jsonMapOf("var" to 1)
                        ),
                        VariablesVariant(
                            id = "2",
                            selector = audienceMatchSelector,
                            compoundAudienceSelector = null,
                            reportingMetadata = jsonMapOf("reporting" to "variable 2"),
                            data = jsonMapOf("var" to 2)
                        ),
                        VariablesVariant(
                            id = "3",
                            selector = audienceMatchSelector,
                            compoundAudienceSelector = null,
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
    public fun testStaticVariantVariablesMissedLocalAudienceCheck(): TestResult = runTest {
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
                            compoundAudienceSelector = null,
                            reportingMetadata = jsonMapOf("reporting" to "variable 1"),
                            data = jsonMapOf("var" to 1)
                        ),
                        VariablesVariant(
                            id = "2",
                            selector = audienceMatchSelector,
                            compoundAudienceSelector = null,
                            reportingMetadata = jsonMapOf("reporting" to "variable 2"),
                            data = jsonMapOf("var" to 2)
                        ),
                        VariablesVariant(
                            id = "3",
                            selector = audienceMatchSelector,
                            compoundAudienceSelector = null,
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
            variables = null,
            isEligible = false,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "flag"))
        )

        assertEquals(expected, flag)
    }

    @Test
    public fun testStaticFixedVariables(): TestResult = runTest {
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
    public fun testStaticFixedVariablesMissedLocalAudienceCheck(): TestResult = runTest {
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
            variables = null,
            isEligible = false,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "flag"))
        )

        assertEquals(expected, flag)
    }

    @Test
    public fun testDeferredNoVariables(): TestResult = runTest {
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
    public fun testDeferredVariantVariables(): TestResult = runTest {
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
                                compoundAudienceSelector = null,
                                reportingMetadata = jsonMapOf("reporting" to "variable 1"),
                                data = jsonMapOf("var" to 1)
                            ),
                            VariablesVariant(
                                id = "2",
                                selector = audienceMatchSelector,
                                compoundAudienceSelector = null,
                                reportingMetadata = jsonMapOf("reporting" to "variable 2"),
                                data = jsonMapOf("var" to 2)
                            ),
                            VariablesVariant(
                                id = "3",
                                selector = audienceMissedSelector,
                                compoundAudienceSelector = null,
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
    public fun testDeferredFixedVariables(): TestResult = runTest {
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
    public fun testDeferredNotEligible(): TestResult = runTest {
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
            variables = null,
            isEligible = false,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "deferred"))
        )

        assertEquals(expected, flag)
    }

    @Test
    public fun testDeferredLocalAudienceCheckMiss(): TestResult = runTest {
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
    public fun testDeferredNoFlag(): TestResult = runTest {
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
    public fun testDeferredError(): TestResult = runTest {
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
    public fun testMultipleFlagsSameName(): TestResult = runTest {
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
    public fun testMultipleFlagsCompound(): TestResult = runTest {
        val flags = listOf(
            FeatureFlagInfo(
                id = "some-id_1",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag1"),
                audience = null,
                compoundAudienceSelector = FeatureFlagCompoundAudience(
                    selector = CompoundAudienceSelector.Atomic(audienceMatchSelector)
                ),
                payload = FeatureFlagPayload.StaticPayload()
            ),
            FeatureFlagInfo(
                id = "some-id_2",
                created = 0,
                lastUpdated = 0,
                name = "test-ff",
                reportingContext = jsonMapOf("reporting" to "flag2"),
                audience = audienceMatchSelector,
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
            reportingInfo = generateReportingInfo(flags[0].reportingContext)
        )

        assertEquals(expected, flag)
    }

    @Test
    public fun testMultipleFlagsSameNameNoMatch(): TestResult = runTest {
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
    public fun testMultipleFlagsSameNameDeferred(): TestResult = runTest {
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
                payload = FeatureFlagPayload.DeferredPayload(url = Uri.EMPTY)
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

        coEvery { deferredResolver.resolve(any(), flags[2]) } returns Result.success(
            DeferredFlag.Found(
                DeferredFlagInfo(
                    isEligible = true,
                    variables = FeatureFlagVariables.Fixed(
                        jsonMapOf("var" to 2)
                    ),
                    reportingMetadata = jsonMapOf("reporting" to "deferred 2")
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
            variables = jsonMapOf("var" to 2),
            isEligible = true,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "deferred 2"))
        )

        assertEquals(expected, flag)
    }

    @Test
    public fun testStaleNotDefined(): TestResult = runTest {
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

        every { remoteDataAccess.status } returns RemoteData.Status.STALE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns payload

        val result = featureFlags.flag("test-ff")
        assertTrue(result.isSuccess)

        coVerify(exactly = 0) {
            remoteDataAccess.bestEffortRefresh()
            remoteDataAccess.notifyOutOfDate(payload.remoteDataInfo)
        }
    }

    @Test
    public fun testStaleNotAllowed(): TestResult = runTest {
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
        coEvery { remoteDataAccess.bestEffortRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.STALE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns payload

        val result = featureFlags.flag("test-ff")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.bestEffortRefresh()
        }
    }

    @Test
    public fun testStaleNotAllowedSingleFlag(): TestResult = runTest {
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

        coEvery { remoteDataAccess.bestEffortRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.STALE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("test-ff")
        } returns payload

        val result = featureFlags.flag("test-ff")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.bestEffortRefresh()
        }
    }

    @Test
    public fun testFlagOutOfDateRemoteDataFailedToRefresh(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.bestEffortRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.OUT_OF_DATE
        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload


        val result = featureFlags.flag("non-existing")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.bestEffortRefresh()
        }
    }

    @Test
    public fun testFlagOutOfDateRemoteDataSuccessRefresh(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.bestEffortRefresh() } just runs

        every { remoteDataAccess.status } returnsMany  listOf(RemoteData.Status.OUT_OF_DATE, RemoteData.Status.UP_TO_DATE)

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload


        val result = featureFlags.flag("non-existing")
        assertTrue(result.isSuccess)

        coVerify {
            remoteDataAccess.bestEffortRefresh()
        }
    }

    @Test
    public fun testOutOfDateNoFlagsBestEffortRefreshes(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.bestEffortRefresh() } just runs

        every { remoteDataAccess.status } returnsMany listOf(RemoteData.Status.OUT_OF_DATE, RemoteData.Status.STALE)

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload

        val result = featureFlags.flag("non-existing")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.bestEffortRefresh()
        }
    }

    @Test
    public fun testStaleDataNoFlagBestEffortRefreshes(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        coEvery { remoteDataAccess.notifyOutOfDate(any()) } just runs
        coEvery { remoteDataAccess.bestEffortRefresh() } just runs

        every { remoteDataAccess.status } returns RemoteData.Status.STALE

        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("non-existing")
        } returns payload

        val result = featureFlags.flag("non-existing")
        assertTrue(result.isFailure)

        coVerify {
            remoteDataAccess.bestEffortRefresh()
        }
    }

    @Test
    public fun testUpToDateNoFlag(): TestResult = runTest {
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
    public fun testControlFlag(): TestResult = runTest {
        val mismatched = featureFlagInfo(
            name = "mismatched",
            controlOptions = ControlOptions(
                compoundAudience = FeatureFlagCompoundAudience(CompoundAudienceSelector.Atomic(audienceMissedSelector)),
                reportingMetadata = jsonMapOf("never" to "override"),
                controlType = ControlOptions.Type.Flag
            )
        )

        val matched = featureFlagInfo(
            name = "matched",
            controlOptions = ControlOptions(
                compoundAudience = FeatureFlagCompoundAudience(CompoundAudienceSelector.Atomic(audienceMatchSelector)),
                reportingMetadata = jsonMapOf("must" to "override"),
                controlType = ControlOptions.Type.Flag
            )
        )

        coEvery { remoteDataAccess.fetchFlagRemoteInfo(any()) } answers {
            val flag = if ("matched" == firstArg()) {
                matched
            } else {
                mismatched
            }

            generateRemoteData(listOf(flag))
        }

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        assertEquals(FeatureFlag.createFlag(
            name = "mismatched",
            isEligible = true,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "flag")),
        ), featureFlags.flag("mismatched").getOrThrow())

        assertEquals(FeatureFlag.createFlag(
            name = "matched",
            isEligible = false,
            reportingInfo = generateReportingInfo(
                reportingMetadata = jsonMapOf("must" to "override"),
                supersededMetadata = listOf(jsonMapOf("reporting" to "flag"))
            )
        ), featureFlags.flag("matched").getOrThrow())
    }

    @Test
    public fun testControlVariables(): TestResult = runTest {
        val mismatched = featureFlagInfo(
            name = "mismatched",
            controlOptions = ControlOptions(
                compoundAudience = FeatureFlagCompoundAudience(CompoundAudienceSelector.Atomic(audienceMissedSelector)),
                reportingMetadata = jsonMapOf("never" to "override"),
                controlType = ControlOptions.Type.Variables(
                    jsonMapOf("override" to "variables")
                )
            )
        )

        val matched = featureFlagInfo(
            name = "matched",
            controlOptions = ControlOptions(
                compoundAudience = FeatureFlagCompoundAudience(CompoundAudienceSelector.Atomic(audienceMatchSelector)),
                reportingMetadata = jsonMapOf("must" to "override"),
                controlType = ControlOptions.Type.Variables(
                    jsonMapOf("override" to "variables")
                )
            )
        )

        coEvery { remoteDataAccess.bestEffortRefresh() } just runs
        coEvery { remoteDataAccess.fetchFlagRemoteInfo(any()) } answers {
            val flag = if ("matched" == firstArg()) {
                matched
            } else {
                mismatched
            }

            generateRemoteData(listOf(flag))
        }

        coEvery { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE

        assertEquals(FeatureFlag.createFlag(
            name = "mismatched",
            isEligible = true,
            reportingInfo = generateReportingInfo(jsonMapOf("reporting" to "flag")),
            variables = null
        ), featureFlags.flag("mismatched").getOrThrow())

        assertEquals(FeatureFlag.createFlag(
            name = "matched",
            isEligible = true,
            variables = jsonMapOf("override" to "variables"),
            reportingInfo = generateReportingInfo(
                reportingMetadata = jsonMapOf("must" to "override"),
                supersededMetadata = listOf(jsonMapOf("reporting" to "flag"))
            )
        ), featureFlags.flag("matched").getOrThrow())
    }

    @Test
    public fun testTrackInteraction(): TestResult = runTest {
        every { featureFlagAnalytics.trackInteraction(any()) } just runs

        val flag = FeatureFlag.createFlag("some name", false, generateReportingInfo())
        featureFlags.trackInteraction(flag)

        verify(exactly = 1) { featureFlagAnalytics.trackInteraction(flag) }
    }

    @Test
    public fun testTrackInteractionDisabledFeatureFlags(): TestResult = runTest {
        every { privacyManager.isEnabled(PrivacyManager.Feature.FEATURE_FLAGS) } returns false

        val flag = FeatureFlag.createFlag("some name", false, generateReportingInfo())
        featureFlags.trackInteraction(flag)

        verify(exactly = 0) { featureFlagAnalytics.trackInteraction(flag) }
    }

    @Test
    public fun testErrorWithResultCache(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        every { remoteDataAccess.status } returns RemoteData.Status.STALE
        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("some-flag")
        } returns payload

        assertTrue(featureFlags.flag("some-flag").isFailure)

        val cachedResult = FeatureFlag.createFlag("some-flag", false, generateReportingInfo())
        coEvery { resultCache.flag("some-flag") } returns cachedResult

        assertTrue(featureFlags.flag("some-flag").isSuccess)
        assertEquals(cachedResult, featureFlags.flag("some-flag").getOrThrow())

        assertTrue(featureFlags.flag("some-flag", useResultCache = false).isFailure)
    }

    @Test
    public fun testDoesNotExistWithResultCache(): TestResult = runTest {
        val payload = generateRemoteData(listOf())

        every { remoteDataAccess.status } returns RemoteData.Status.UP_TO_DATE
        coEvery {
            remoteDataAccess.fetchFlagRemoteInfo("some-flag")
        } returns payload

        assertTrue(featureFlags.flag("some-flag").isSuccess)
        assertFalse(featureFlags.flag("some-flag").getOrThrow().exists)

        val cachedResult = FeatureFlag.createFlag("some-flag", false, generateReportingInfo())
        coEvery { resultCache.flag("some-flag") } returns cachedResult

        assertTrue(featureFlags.flag("some-flag").isSuccess)
        assertTrue(featureFlags.flag("some-flag").getOrThrow().exists)

        assertTrue(featureFlags.flag("some-flag", useResultCache = false).isSuccess)
        assertFalse(featureFlags.flag("some-flag", useResultCache = false).getOrThrow().exists)
    }

    private suspend fun generateReportingInfo(
        reportingMetadata: JsonMap? = null,
        supersededMetadata: List<JsonMap>? = null
        ): FeatureFlag.ReportingInfo {
        return FeatureFlag.ReportingInfo(
            reportingMetadata = reportingMetadata
                ?: jsonMapOf("flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925"),
            supersededReportingMetadata = supersededMetadata,
            contactId = infoProvider.getStableContactInfo().contactId,
            channelId = infoProvider.getChannelId()
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

    private fun featureFlagInfo(
        name: String,
        controlOptions: ControlOptions? = null
    ): FeatureFlagInfo {
        return FeatureFlagInfo(
            id = UUID.randomUUID().toString(),
            created = 0,
            lastUpdated = 0,
            name = name,
            reportingContext = jsonMapOf("reporting" to "flag"),
            payload = FeatureFlagPayload.StaticPayload(),
            controlOptions = controlOptions
        )
    }
}
