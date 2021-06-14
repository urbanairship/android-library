package com.urbanairship.preferencecenter

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.job.JobDispatcher
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.remotedata.RemoteData
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class PreferenceCenterTest {

    companion object {
        const val PREF_CENTER_ID = "pref-center-id"

        val CONFIG = PreferenceCenterConfig(id = PREF_CENTER_ID)
    }

    private val context: Context = TestApplication.getApplication()
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val privacyManager = PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL)

    private val remoteData: RemoteData = mock()
    private val jobDispatcher: JobDispatcher = mock()
    private val onOpenListener: PreferenceCenter.OnOpenListener = mock()

    private lateinit var prefCenter: PreferenceCenter

    @Before
    fun setUp() {
        prefCenter = PreferenceCenter(context, dataStore, privacyManager, remoteData, jobDispatcher)
    }

    @Test
    fun testOnOpenListener() {
        prefCenter.openListener = onOpenListener
        verify(onOpenListener, never()).onOpenPreferenceCenter(any())

        prefCenter.open(PREF_CENTER_ID)
        verify(onOpenListener).onOpenPreferenceCenter(eq(PREF_CENTER_ID))
    }

    @Test
    fun testGetConfig() {
        val pendingResult = prefCenter.getConfig(PREF_CENTER_ID)
        assertEquals(CONFIG, pendingResult.result)
    }
}
