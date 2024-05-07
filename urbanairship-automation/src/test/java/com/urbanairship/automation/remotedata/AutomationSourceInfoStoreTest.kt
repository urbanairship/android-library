package com.urbanairship.automation.remotedata

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestClock
import com.urbanairship.automation.remotedata.AutomationSourceInfo
import com.urbanairship.automation.remotedata.AutomationSourceInfoStore
import com.urbanairship.remotedata.RemoteDataSource
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationSourceInfoStoreTest {
    private val dataStore = PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext())
    private val infoStore = AutomationSourceInfoStore(dataStore)
    private val clock = TestClock()

    @Test
    public fun testAppStoreIgnoreContactID() {
        val sourceInfo = AutomationSourceInfo(null, clock.currentTimeMillis(), "17.9.9")
        infoStore.setSourceInfo(sourceInfo, RemoteDataSource.APP, "foo")

        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.APP, null))
        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.APP, "foo"))
        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.APP, UUID.randomUUID().toString()))
    }

    @Test
    public fun testContactStoreRespectsContactID() {
        val sourceInfo = AutomationSourceInfo(null, clock.currentTimeMillis(), "17.9.9")
        infoStore.setSourceInfo(sourceInfo, RemoteDataSource.CONTACT, "foo")

        assertNull(infoStore.getSourceInfo(RemoteDataSource.CONTACT, null))
        assertNull(infoStore.getSourceInfo(RemoteDataSource.CONTACT, UUID.randomUUID().toString()))
        assertEquals(sourceInfo, infoStore.getSourceInfo(RemoteDataSource.CONTACT, "foo"))
    }
}
