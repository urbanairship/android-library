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

    @Before
    public fun setUp() {
        subject = ExperimentManager(context, dataStore, remoteData)
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
        assertEquals("contact", parsed.audienceSelector.hash.identifiersDomain.jsonValue)
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
        assertEquals("channel", validExperiment!!.audienceSelector.hash.identifiersDomain.jsonValue)

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

    private fun generateExperimentsPayload(
        id: String,
        hashIdentifier: String = "contact",
        hashAlgorithm: String = "farm_hash"
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
                                algorithm = hashAlgorithm
                            ))
                            .put("audience_subset", generateBucket())
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
                                            .put("equals", "Transactional")
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
            .put("evaluation_options") {
                JsonMap
                    .newBuilder()
                    .put("disallow_stale_value", true)
                    .put("disallow_stale_contact", true)
                    .put("ttl", 22)
                    .build()
                    .toJsonValue()
            }
    }

    private fun generateAudienceHash(identifier: String, algorithm: String): JsonValue {
        return JsonMap.newBuilder()
            .put("hash_prefix", "e66a2371-fecf-41de-9238-cb6c28a86cec:")
            .put("num_hash_buckets", 16384)
            .put("hash_identifier", identifier)
            .put("hash_algorithm", algorithm)
            .build()
            .toJsonValue()
    }

    private fun generateBucket(min: Int = 0, max: Int = 16384): JsonValue {
        return JsonMap.newBuilder()
            .put("min_hash_bucket", min)
            .put("max_hash_bucket", max)
            .build()
            .toJsonValue()
    }
}
