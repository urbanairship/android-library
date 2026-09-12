package com.urbanairship.automation.remotedata

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.remotedata.RemoteDataSource
import java.time.Instant
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationSourceInfoStoreTest {
    private val dataStore = PreferenceStore.inMemoryStore(ApplicationProvider.getApplicationContext())
    private val infoStore = AutomationSourceInfoStore(dataStore)
    private val clock = TestClock()

    @Test
    public fun testAppStoreIgnoreContactID(): TestResult = runTest {
        val sourceInfo = AutomationSourceInfo(null, clock.now(), "17.9.9")
        infoStore.setSourceInfo(sourceInfo, RemoteDataSource.APP, "foo")

        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.APP, null))
        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.APP, "foo"))
        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.APP, UUID.randomUUID().toString()))
    }

    @Test
    public fun testContactStoreRespectsContactID(): TestResult = runTest {
        val sourceInfo = AutomationSourceInfo(null, clock.now(), "17.9.9")
        infoStore.setSourceInfo(sourceInfo, RemoteDataSource.CONTACT, "foo")

        assertNull(infoStore.getSourceInfo(RemoteDataSource.CONTACT, null))
        assertNull(infoStore.getSourceInfo(RemoteDataSource.CONTACT, UUID.randomUUID().toString()))
        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.CONTACT, "foo"))
    }

    @Test
    public fun testFailedSchedulesRoundTrip(): TestResult = runTest {
        val sourceInfo = AutomationSourceInfo(
            remoteDataInfo = null,
            payloadTimestamp = clock.now(),
            airshipSDKVersion = "17.9.9",
            failedSchedules = listOf(
                FailedScheduleRecord("foo", 100L, "18.0.0"),
                FailedScheduleRecord("bar", 200L, null)
            )
        )

        infoStore.setSourceInfo(sourceInfo, RemoteDataSource.APP, null)

        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.APP, null))
    }

    @Test
    public fun testNoFailedSchedulesRoundTripsAsNull(): TestResult = runTest {
        val sourceInfo = AutomationSourceInfo(null, clock.now(), "17.9.9")

        infoStore.setSourceInfo(sourceInfo, RemoteDataSource.APP, null)

        assertNull(infoStore.getSourceInfo(RemoteDataSource.APP, null)?.failedSchedules)
    }

    @Test
    public fun testMalformedFailedScheduleDoesNotDiscardSourceInfo(): TestResult = runTest {
        val json = jsonMapOf(
            "payloadTimestamp" to 100L,
            "airshipSDKVersion" to "17.9.9",
            "failedSchedules" to jsonListOf(
                jsonMapOf("identifier" to "foo", "createdDate" to 100L),
                // Missing the required identifier.
                jsonMapOf("createdDate" to 200L)
            )
        ).toJsonValue()

        val parsed = AutomationSourceInfo.fromJson(json)

        assertEquals(
            listOf(FailedScheduleRecord("foo", 100L, null)),
            parsed?.failedSchedules
        )
        assertEquals(Instant.ofEpochMilli(100L), parsed?.payloadTimestamp)
    }
}
