package com.urbanairship.featureflag

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestApplication
import com.urbanairship.remotedata.RemoteData
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirshipFeatureFlagsTest {

    private val context: Context = TestApplication.getApplication()
    private val remoteData: RemoteData = mockk()
    private lateinit var featureFlags: AirshipFeatureFlags

    @Before
    fun setUp() {
        featureFlags = AirshipFeatureFlags(
            context = context,
            dataStore = PreferenceDataStore.inMemoryStore(context),
            remoteData = remoteData
        )
    }

    @Test
    fun testModuleIsWorking() {
        featureFlags.init()
        assert(featureFlags.isComponentEnabled)
    }
}
