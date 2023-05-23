package com.urbanairship.experiment

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestApplication
import com.urbanairship.experiment.ExperimentManager.Companion.PAYLOAD_TYPE
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import com.urbanairship.reactive.Observable
import com.urbanairship.reactive.Subject
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataPayload
import io.mockk.every
import io.mockk.mockk
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

    private val payloads = Subject.create<RemoteDataPayload>()
    private val remoteData: RemoteData = mockk {
        every { this@mockk.payloadsForType(PAYLOAD_TYPE) } returns payloads
    }

    private lateinit var subject: ExperimentManager
    private var channelId: String? = "default-channel-id"
    private var contactId: String = "default-contact-id"

    @Before
    public fun setUp() {
        subject = ExperimentManager(
            context = context,
            dataStore = dataStore,
            remoteData = remoteData,
            channelIdFetcher = { channelId },
            stableContactIdFetcher = { contactId }
        )
    }

    @Test
    public fun testExperimentManagerParseValidPayloadData(): TestResult = runTest {
        val experimentJson = generateExperimentsPayload("fake-id").build()
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf(PAYLOAD_TYPE to jsonListOf(experimentJson)))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        val experiment = subject.getExperimentWithId("fake-id")

        assertNotNull(experiment)
        val parsed = experiment!!
        assertEquals("fake-id", parsed.id)
        assertEquals("farm_hash", parsed.audienceSelector.hash.algorithm.jsonValue)
        assertEquals("contact", parsed.audienceSelector.hash.property.jsonValue)
        assertEquals(123L, parsed.lastUpdated)
        assertEquals("Holdout", parsed.type.jsonValue)
        assertEquals("Static", parsed.resolutionType.jsonValue)
        assert(parsed.reportingMetadata.equals(experimentJson.require("reporting_metadata")))
    }

    @Test
    public fun testExperimentManagerOmitsInvalidExperiments(): TestResult = runTest {
        val experimentJson = generateExperimentsPayload("fake-id", hashIdentifier = "channel").build()
        val invalid = generateExperimentsPayload("fake-id-2", hashIdentifier = "invalid").build()
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf(PAYLOAD_TYPE to jsonListOf(experimentJson, invalid)))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        val validExperiment = subject.getExperimentWithId("fake-id")
        assertNotNull(validExperiment)
        assertEquals("fake-id", validExperiment!!.id)
        assertEquals("channel", validExperiment!!.audienceSelector.hash.property.jsonValue)

        assertNull(subject.getExperimentWithId("fake-id-2"))
    }

    @Test
    public fun testExperimentManagerParseMultipleExperiments(): TestResult = runTest {
        val experiment1 = generateExperimentsPayload("fake-id").build()
        val experiment2 = generateExperimentsPayload("fake-id-2").build()
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf(PAYLOAD_TYPE to jsonListOf(experiment1, experiment2)))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        assertNotNull(subject.getExperimentWithId("fake-id"))
        assertNotNull(subject.getExperimentWithId("fake-id-2"))
    }

    @Test
    public fun testExperimentManagerHandleNoExperimentsPayload(): TestResult = runTest {
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf())
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)
        assertNull(subject.getExperimentWithId("no-experiment"))
    }

    @Test
    public fun testExperimentManagerHandleInvalidPayload(): TestResult = runTest {
        val experiment = generateExperimentsPayload("fake-id").build()
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf("invalid" to experiment))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        assertNull(subject.getExperimentWithId("fake-id"))
    }

    @Test
    public fun testHoldoutGroupEvaluationWorks(): TestResult = runTest {
        channelId = "channel-id"
        contactId = "some-contact-id"

        val experimentJson = generateExperimentsPayload(
            id = "fake-id",
            hashIdentifier = "channel")
            .build()
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf(PAYLOAD_TYPE to jsonListOf(experimentJson)))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        val result = subject.evaluateGlobalHoldouts(MessageInfo(""))
        assertNotNull(result)
        assertEquals("fake-id", result!!.experimentId)
        assertEquals(contactId, result.contactId)
        assertEquals(channelId, result.channelId)
        assert(result.reportingMetadata.equals(experimentJson.require("reporting_metadata")))
    }

    @Test
    public fun testHoldoutGroupEvaluationRespectHashBuckets(): TestResult = runTest {
        channelId = "channel-id"
        contactId = "some-contact-id"
        val activeContactId = "active-contact-id"

        val unmatchedJson = generateExperimentsPayload(
            id = "unmatched",
            bucketMax = 1239).build()
        val matchedJson = generateExperimentsPayload("matched", bucketMin = 1239).build()
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf(PAYLOAD_TYPE to jsonListOf(unmatchedJson, matchedJson)))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        val result = subject.evaluateGlobalHoldouts(MessageInfo(""), activeContactId)
        assertNotNull(result)
        assertEquals("matched", result!!.experimentId)
        assertEquals(activeContactId, result.contactId)
    }

    @Test
    public fun testHoldoutGroupEvaluationPicksFirstMatchingExperiment(): TestResult = runTest {
        val firstJson = generateExperimentsPayload(id = "first").build()
        val secondJson = generateExperimentsPayload("second").build()
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf(PAYLOAD_TYPE to jsonListOf(firstJson, secondJson)))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        val result = subject.evaluateGlobalHoldouts(MessageInfo(""))
        assertNotNull(result)
        assertEquals("first", result!!.experimentId)
    }

    @Test
    public fun testHoldoutEvaluationRespectsMessageExclusion(): TestResult = runTest {
        val unmatchedJson = generateExperimentsPayload(id = "unmatched").build()
        val matchedJson = generateExperimentsPayload(
            id = "matched",
            messageTypeToExclude = "none")
            .build()
        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf(PAYLOAD_TYPE to jsonListOf(unmatchedJson, matchedJson)))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        val result = subject.evaluateGlobalHoldouts(MessageInfo("Transactional"))
        assertNotNull(result)
        assertEquals("matched", result!!.experimentId)
    }

    @Test
    public fun testHoldoutEvaluationRespectOverridesForHash(): TestResult = runTest {
        val unmatchedJson = generateExperimentsPayload(
            id = "unmatched",
            bucketMax = 2337,
            hashOverrides = JsonMap
                .newBuilder()
                .put(contactId, "overriden")
                .build()
        )
            .build()

        val matchedJson = generateExperimentsPayload(
            id = "matched",
            bucketMax = 2337,
        )
            .build()

        val data = RemoteDataPayload.newBuilder()
            .setType(PAYLOAD_TYPE)
            .setTimeStamp(1L)
            .setMetadata(jsonMapOf())
            .setData(jsonMapOf(PAYLOAD_TYPE to jsonListOf(unmatchedJson, matchedJson)))
            .build()

        every { remoteData.payloadsForType(PAYLOAD_TYPE) } returns Observable.just(data)

        val result = subject.evaluateGlobalHoldouts(MessageInfo(""))
        assertNotNull(result)
        assertEquals("matched", result!!.experimentId)
    }

    private fun generateExperimentsPayload(
        id: String,
        hashIdentifier: String = "contact",
        hashAlgorithm: String = "farm_hash",
        hashOverrides: JsonMap? = null,
        bucketMin: Int = 0,
        bucketMax: Int = 16384,
        messageTypeToExclude: String = "Transactional"
    ): JsonMap.Builder {
        return JsonMap.newBuilder()
            .put("id", id)
            .put("experimentType", "Holdout")
            .put("last_updated", 123L)
            .put("reporting_metadata") {
                JsonMap
                    .newBuilder()
                    .put("experiment_id", id)
                    .build()
                    .toJsonValue()
            }.put("type", "Static")
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
