package com.urbanairship.experiment

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestApplication
import com.urbanairship.audience.DeviceInfoProviderImpl
import com.urbanairship.experiment.ExperimentManager.Companion.PAYLOAD_TYPE
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataPayload
import com.urbanairship.util.Clock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ExperimentManagerTest {

    private val context: Context = TestApplication.getApplication()
    private val dataStore = PreferenceDataStore.inMemoryStore(context)

    private val remoteData: RemoteData = mockk()

    private lateinit var subject: ExperimentManager
    private var channelId: String? = "default-channel-id"
    private var contactId: String = "default-contact-id"
    private val messageInfo = MessageInfo("", null)
    private var currentTime = 1L

    @Before
    public fun setUp() {
        val permissionManager: PermissionsManager = mockk()
        every { permissionManager.configuredPermissions } returns emptySet()
        val infoProvider = DeviceInfoProviderImpl(
            notificationStatusFetcher = { true },
            privacyFeatureFetcher = { true },
            channelTagsFetcher = { emptySet() },
            channelIdFetcher = { channelId },
            versionFetcher = { 1 },
            permissionsManager = permissionManager,
            contactIdFetcher = { contactId },
            platform = "android",
            mockk()
        )

        val clock: Clock = mockk()
        every { clock.currentTimeMillis() } answers { currentTime }

        subject = ExperimentManager(
            context = context,
            dataStore = dataStore,
            remoteData = remoteData,
            infoProvider = infoProvider,
            clock = clock
        )
    }

    @Test
    public fun testExperimentManagerParseValidPayloadData(): TestResult = runTest {
        val experimentJson = generateExperimentsPayload("fake-id").build()
        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(experimentJson))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        val experiment = subject.getExperimentWithId(messageInfo, "fake-id")

        assertNotNull(experiment)
        val parsed = experiment!!
        assertEquals("fake-id", parsed.id)
        assertEquals("farm_hash", parsed.audience.hashSelector!!.hash.algorithm.jsonValue)
        assertEquals("contact", parsed.audience.hashSelector.hash.property.jsonValue)
        assertEquals(1684868854000L, parsed.lastUpdated)
        assertEquals("holdout", parsed.type.jsonValue)
        assertEquals("static", parsed.resolutionType.jsonValue)
        assert(parsed.reportingMetadata.equals(extractReportingMetadata(experimentJson)))
    }

    @Test
    public fun testExperimentManagerOmitsInvalidExperiments(): TestResult = runTest {
        val experimentJson = generateExperimentsPayload("fake-id", hashIdentifier = "channel").build()
        val invalid = generateExperimentsPayload("fake-id-2", hashIdentifier = "invalid").build()
        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(experimentJson, invalid))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        val validExperiment = subject.getExperimentWithId(messageInfo, "fake-id")
        assertNotNull(validExperiment)
        assertEquals("fake-id", validExperiment!!.id)
        assertEquals("channel", validExperiment.audience.hashSelector!!.hash.property.jsonValue)

        assertNull(subject.getExperimentWithId(messageInfo, "fake-id-2"))
    }

    @Test
    public fun testExperimentManagerParseMultipleExperiments(): TestResult = runTest {
        val experiment1 = generateExperimentsPayload("fake-id").build()
        val experiment2 = generateExperimentsPayload("fake-id-2").build()
        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(experiment1, experiment2))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        assertNotNull(subject.getExperimentWithId(messageInfo, "fake-id"))
        assertNotNull(subject.getExperimentWithId(messageInfo, "fake-id-2"))
    }

    @Test
    public fun testExperimentManagerHandleNoExperimentsPayload(): TestResult = runTest {
        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf()
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        assertNull(subject.getExperimentWithId(messageInfo, "no-experiment"))
    }

    @Test
    public fun testExperimentManagerHandleInvalidPayload(): TestResult = runTest {
        val experiment = generateExperimentsPayload("fake-id").build()
        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf("invalid" to experiment)
        )
        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        assertNull(subject.getExperimentWithId(messageInfo, "fake-id"))
    }

    @Test
    public fun testHoldoutGroupEvaluationWorks(): TestResult = runTest {
        channelId = "channel-id"
        contactId = "some-contact-id"

        val experimentJson = generateExperimentsPayload(
            id = "fake-id",
            hashIdentifier = "channel")
            .build()

        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(experimentJson))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        val result = subject.evaluateExperiments(messageInfo)!!
        assertEquals("fake-id", result.matchedExperimentId)
        assertTrue(result.isMatching)
        assertEquals(contactId, result.contactId)
        assertEquals(channelId, result.channelId)

        assert(result.allEvaluatedExperimentsMetadata.contains(extractReportingMetadata(experimentJson)))
    }

    @Test
    public fun testHoldoutGroupEvaluationRespectHashBuckets(): TestResult = runTest {
        channelId = "channel-id"
        contactId = "some-contact-id"
        val activeContactId = "active-contact-id"

        val unmatchedJson = generateExperimentsPayload(
            id = "unmatched",
            bucketMax = 1238
        ).build()
        val matchedJson = generateExperimentsPayload("matched", bucketMin = 1239).build()

        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(unmatchedJson, matchedJson))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        val result = subject.evaluateExperiments(messageInfo, activeContactId)!!
        assertTrue(result.isMatching)
        assertEquals("matched", result.matchedExperimentId)
        assertEquals(activeContactId, result.contactId)
        assert(result.allEvaluatedExperimentsMetadata
            .containsAll(listOf(
                extractReportingMetadata(unmatchedJson),
                extractReportingMetadata(matchedJson))))
    }

    @Test
    public fun testHoldoutGroupEvaluationPicksFirstMatchingExperiment(): TestResult = runTest {
        val firstJson = generateExperimentsPayload(id = "first").build()
        val secondJson = generateExperimentsPayload("second").build()

        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(firstJson, secondJson))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        val result = subject.evaluateExperiments(messageInfo)!!
        assertTrue(result.isMatching)
        assertEquals("first", result.matchedExperimentId)
        assertTrue(result.allEvaluatedExperimentsMetadata.size == 1)
        assert(result.allEvaluatedExperimentsMetadata.contains(extractReportingMetadata(firstJson)))
    }

    @Test
    public fun testHoldoutEvaluationRespectsMessageExclusion(): TestResult = runTest {
        val unmatchedJson = generateExperimentsPayload(id = "unmatched").build()
        val matchedJson = generateExperimentsPayload(
            id = "matched",
            messageTypeToExclude = "none")
            .build()

        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(unmatchedJson, matchedJson))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        val result = subject.evaluateExperiments(MessageInfo("transactional", null))!!
        assert(result.isMatching)
        assertEquals("matched", result.matchedExperimentId)
    }

    @Test
    public fun testHoldoutEvaluationRespectOverridesForHash(): TestResult = runTest {
        val unmatchedJson = generateExperimentsPayload(
            id = "unmatched",
            bucketMax = 2336,
            hashOverrides = JsonMap
                .newBuilder()
                .put(contactId, "overriden")
                .build()
        ).build()

        val matchedJson = generateExperimentsPayload(
            id = "matched",
            bucketMax = 2337,
        ).build()

        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(unmatchedJson, matchedJson))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)

        val result = subject.evaluateExperiments(messageInfo)!!
        assert(result.isMatching)
        assertEquals("matched", result.matchedExperimentId)
    }

    @Test
    public fun testResultExcludesInactiveExperiments(): TestResult = runTest {
        val unmatchedJson = generateExperimentsPayload(
            id = "unmatched",
            timeCriteria = TimeCriteria(start = 2L, end = Date().time + 4L)
        )
            .build()

        val matchedJson = generateExperimentsPayload(
            id = "matched",
            timeCriteria = TimeCriteria(start = 1L, end = 2L)
        )
            .build()

        val data = RemoteDataPayload(
            type = PAYLOAD_TYPE,
            timestamp = 1L,
            data = jsonMapOf(PAYLOAD_TYPE to jsonListOf(unmatchedJson, matchedJson))
        )

        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(data)
        currentTime = 2

        val result = subject.evaluateExperiments(messageInfo)!!
        assert(result.isMatching)
        assertEquals("matched", result.matchedExperimentId)
    }

    private fun generateExperimentsPayload(
        id: String,
        hashIdentifier: String = "contact",
        hashAlgorithm: String = "farm_hash",
        hashOverrides: JsonMap? = null,
        bucketMin: Int = 0,
        bucketMax: Int = 16384,
        messageTypeToExclude: String = "transactional",
        timeCriteria: TimeCriteria? = null
    ): JsonMap.Builder {
        return JsonMap.newBuilder()
            .put("experiment_id", id)
            .put("created", "2023-05-23T19:07:34.000")
            .put("last_updated", "2023-05-23T19:07:34.000")
            .put("experiment_definition", JsonMap
                .newBuilder()
                .put("experiment_type", "holdout")
                .put("reporting_metadata") {
                    JsonMap
                        .newBuilder()
                        .put("experiment_id", id)
                        .build()
                        .toJsonValue()
                }
                .put("type", "static")
                .put("audience_selector") {
                    JsonMap
                        .newBuilder()
                        .put("hash") {
                            JsonMap
                                .newBuilder()
                                .put("audience_hash", generateAudienceHash(
                                    identifier = hashIdentifier,
                                    algorithm = hashAlgorithm,
                                    overrides = hashOverrides
                                ))
                                .put("audience_subset", generateBucket(bucketMin, bucketMax))
                                .build()
                                .toJsonValue()
                        }
                        .build()
                        .toJsonValue()
                }
                .put("message_exclusions") {
                    JsonList(
                        listOf(
                            JsonMap
                                .newBuilder()
                                .put("message_type") {
                                    JsonMap
                                        .newBuilder()
                                        .put("value") {
                                            JsonMap
                                                .newBuilder()
                                                .put("equals", messageTypeToExclude)
                                                .build()
                                                .toJsonValue()
                                        }
                                        .build()
                                        .toJsonValue()
                                }
                                .build()
                                .toJsonValue()
                        )
                    )
                        .toJsonValue()
                }
                .put("time_criteria", timeCriteria)
                .build()
                .toJsonValue()
            )
    }

    private fun extractReportingMetadata(payload: JsonMap): JsonMap {
        return payload
            .require("experiment_definition")
            .optMap()
            .require("reporting_metadata")
            .optMap()
    }

    private fun generateAudienceHash(identifier: String, algorithm: String, overrides: JsonMap?): JsonValue {
        val result = JsonMap.newBuilder()
            .put("hash_prefix", "e66a2371-fecf-41de-9238-cb6c28a86cec:")
            .put("num_hash_buckets", 16384)
            .put("hash_identifier", identifier)
            .put("hash_algorithm", algorithm)

        if (overrides != null) {
            result.put("hash_identifier_overrides", overrides.toJsonValue())
        }

        return result
            .build()
            .toJsonValue()
    }

    private fun generateBucket(min: Int, max: Int): JsonValue {
        return JsonMap.newBuilder()
            .put("min_hash_bucket", min)
            .put("max_hash_bucket", max)
            .build()
            .toJsonValue()
    }
}
