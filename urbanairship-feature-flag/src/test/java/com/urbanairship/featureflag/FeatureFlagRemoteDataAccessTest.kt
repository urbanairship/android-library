package com.urbanairship.featureflag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataPayload
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.DateUtils
import java.util.UUID
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureFlagRemoteDataAccessTest {
    private val remoteData: RemoteData = mockk()
    private val clock = TestClock().apply { currentTimeMillis = currentTimeMillis() }
    private val remoteDataAccess = FeatureFlagRemoteDataAccess(remoteData, clock)

    private val payloadType = "feature_flags"

    @Test
    fun testFeatureFlag(): TestResult = runTest {
        val json = """
       {
           "feature_flags":[
              {
                 "flag_id":"27f26d85-0550-4df5-85f0-7022fa7a5925",
                 "created":"2023-07-10T18:10:46.203",
                 "last_updated":"2023-07-10T18:10:46.203",
                 "flag":{
                    "name":"cool_flag",
                    "type":"static",
                    "reporting_metadata":{
                       "flag_id":"27f26d85-0550-4df5-85f0-7022fa7a5925"
                    }
                 }
              },
              {
                "something": "invalid"
              }
           ]
        }
        """

        val data = RemoteDataPayload(
            type = payloadType,
            timestamp = 1L,
            data = JsonValue.parseString(json).requireMap(),
            remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url/${UUID.randomUUID()}",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val result = remoteDataAccess.fetchFlagRemoteInfo("cool_flag")
        val expected = RemoteDataFeatureFlagInfo(
            flagInfoList = listOf(
                FeatureFlagInfo(
                    id = "27f26d85-0550-4df5-85f0-7022fa7a5925",
                    created = DateUtils.parseIso8601("2023-07-10T18:10:46.203"),
                    lastUpdated = DateUtils.parseIso8601("2023-07-10T18:10:46.203"),
                    name = "cool_flag",
                    reportingContext = jsonMapOf( "flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925"),
                    payload = FeatureFlagPayload.StaticPayload()
                )
            ),
            remoteDataInfo = data.remoteDataInfo
        )

        assertEquals(expected, result)
    }

    @Test
    fun testIgnoreInvalidFlags(): TestResult = runTest {
        val json = """
        {
           "feature_flags":[
              {
                 "flag_id":"27f26d85-0550-4df5-85f0-7022fa7a5925",
                 "created":"2023-07-10T18:10:46.203",
                 "last_updated":"2023-07-10T18:10:46.203",
                 "platforms":[
                    "android"
                 ],
                 "flag":{
                    "name":"cool_flag",
                    "type":"static",
                    "reporting_metadata":{
                       "flag_id":"27f26d85-0550-4df5-85f0-7022fa7a5925"
                    }
                 }
              }
           ]
        }
        """

        val data = RemoteDataPayload(
            type = payloadType,
            timestamp = 1L,
            data = JsonValue.parseString(json).requireMap(),
            remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url/${UUID.randomUUID()}",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val result = remoteDataAccess.fetchFlagRemoteInfo("cool_flag")
        val expected = RemoteDataFeatureFlagInfo(
            flagInfoList = listOf(
                FeatureFlagInfo(
                    id = "27f26d85-0550-4df5-85f0-7022fa7a5925",
                    created = DateUtils.parseIso8601("2023-07-10T18:10:46.203"),
                    lastUpdated = DateUtils.parseIso8601("2023-07-10T18:10:46.203"),
                    name = "cool_flag",
                    reportingContext = jsonMapOf( "flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925"),
                    payload = FeatureFlagPayload.StaticPayload()
                )
            ),
            remoteDataInfo = data.remoteDataInfo
        )

        assertEquals(expected, result)
    }

    @Test
    fun testIgnoreContact(): TestResult = runTest {
        val json = """
        {
           "feature_flags":[
              {
                 "flag_id":"27f26d85-0550-4df5-85f0-7022fa7a5925",
                 "created":"2023-07-10T18:10:46.203",
                 "last_updated":"2023-07-10T18:10:46.203",
                 "platforms":[
                    "android"
                 ],
                 "flag":{
                    "name":"cool_flag",
                    "type":"static",
                    "reporting_metadata":{
                       "flag_id":"27f26d85-0550-4df5-85f0-7022fa7a5925"
                    }
                 }
              }
           ]
        }
        """

        val data = RemoteDataPayload(
            type = payloadType,
            timestamp = 1L,
            data = JsonValue.parseString(json).requireMap(),
            remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url/${UUID.randomUUID()}",
                lastModified = null,
                source = RemoteDataSource.CONTACT,
            )
        )

        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        val result = remoteDataAccess.fetchFlagRemoteInfo("cool_flag")
        val expected = RemoteDataFeatureFlagInfo(
            flagInfoList = emptyList<FeatureFlagInfo>(),
            remoteDataInfo = null
        )

        assertEquals(expected, result)
    }

    @Test
    fun testIgnoreInactive(): TestResult = runTest {

        val json = """
        {
           "feature_flags":[
              {
                 "flag_id":"27f26d85-0550-4df5-85f0-7022fa7a5925",
                 "created":"2023-07-10T18:10:46.203",
                 "last_updated":"2023-07-10T18:10:46.203",
                 "flag":{
                    "name":"cool_flag",
                    "type":"static",
                    "reporting_metadata":{
                       "flag_id":"27f26d85-0550-4df5-85f0-7022fa7a5925"
                    },
                    "time_criteria": {
                      "start_timestamp": ${clock.currentTimeMillis},
                      "end_timestamp": ${clock.currentTimeMillis + 5000}
                    }
                 }
              }
           ]
        }
        """

        val data = RemoteDataPayload(
            type = payloadType,
            timestamp = 1L,
            data = JsonValue.parseString(json).requireMap(),
            remoteDataInfo = RemoteDataInfo(
                url = "https://sample.url/${UUID.randomUUID()}",
                lastModified = null,
                source = RemoteDataSource.APP,
            )
        )

        coEvery { remoteData.payloads(payloadType) } returns listOf(data)

        assertFalse(remoteDataAccess.fetchFlagRemoteInfo("cool_flag").flagInfoList.isEmpty())

        clock.currentTimeMillis -= 1
        assertTrue(remoteDataAccess.fetchFlagRemoteInfo("cool_flag").flagInfoList.isEmpty())

        clock.currentTimeMillis += 1
        assertFalse(remoteDataAccess.fetchFlagRemoteInfo("cool_flag").flagInfoList.isEmpty())

        clock.currentTimeMillis += 5000
        assertFalse(remoteDataAccess.fetchFlagRemoteInfo("cool_flag").flagInfoList.isEmpty())

        clock.currentTimeMillis += 1
        assertTrue(remoteDataAccess.fetchFlagRemoteInfo("cool_flag").flagInfoList.isEmpty())
    }
}
