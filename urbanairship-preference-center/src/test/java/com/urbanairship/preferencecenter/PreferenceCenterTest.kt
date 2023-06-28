package com.urbanairship.preferencecenter

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import com.urbanairship.preferencecenter.PreferenceCenter.Companion.KEY_PREFERENCE_FORMS
import com.urbanairship.preferencecenter.PreferenceCenter.Companion.PAYLOAD_TYPE
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.PreferenceCenterPayload
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataPayload
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceCenterTest {

    companion object {
        private val EMPTY_PAYLOADS = RemoteDataPayload.emptyPayload(PAYLOAD_TYPE)

        private const val ID_1 = "id-1"
        private const val ID_2 = "id-2"

        private val PREFERENCE_FORM_1 = PreferenceCenterConfig(ID_1, emptyList(), CommonDisplay.EMPTY)
        private val PREFERENCE_FORM_2 = PreferenceCenterConfig(ID_2, emptyList(), CommonDisplay.EMPTY)

        private val FORM_1_PAYLOAD = PreferenceCenterPayload(PREFERENCE_FORM_1)
        private val FORM_2_PAYLOAD = PreferenceCenterPayload(PREFERENCE_FORM_2)

        private val SINGLE_FORM_PAYLOAD = RemoteDataPayload(
            PAYLOAD_TYPE,
            1L,
            jsonMapOf(KEY_PREFERENCE_FORMS to jsonListOf(FORM_1_PAYLOAD.toJson()))
        )

        private val MULTI_FORM_PAYLOAD = RemoteDataPayload(
            PAYLOAD_TYPE,
            1L,
            jsonMapOf(KEY_PREFERENCE_FORMS to jsonListOf(FORM_1_PAYLOAD.toJson(), FORM_2_PAYLOAD.toJson()))
        )
    }

    private val context: Context = TestApplication.getApplication()
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val privacyManager = PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL)

    private val remoteData: RemoteData = mockk()

    private val onOpenListener: PreferenceCenter.OnOpenListener = mockk(relaxed = true)

    private val prefCenter: PreferenceCenter = PreferenceCenter(
        context, dataStore, privacyManager, remoteData
    )

    @Test
    fun testOnOpenListener(): TestResult = runTest {
        prefCenter.openListener = onOpenListener
        verify(exactly = 0) { onOpenListener.onOpenPreferenceCenter(any()) }

        prefCenter.open(ID_1)
        verify(exactly = 1) { onOpenListener.onOpenPreferenceCenter(ID_1) }
    }

    @Test
    fun testGetConfigWithEmptyRemoteDataPayload(): TestResult = runTest {
        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(EMPTY_PAYLOADS)
        assertEquals(null, prefCenter.getConfig(ID_1))
    }

    @Test
    fun testGetConfigWithSingleRemoteDataPayload(): TestResult = runTest {
        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(SINGLE_FORM_PAYLOAD)
        assertEquals(PREFERENCE_FORM_1, prefCenter.getConfig(ID_1))
    }

    @Test
    fun testGetJsonConfigWithSingleRemoteDataPayload(): TestResult = runTest {
        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(SINGLE_FORM_PAYLOAD)
        assertEquals(PREFERENCE_FORM_1.toJson(), prefCenter.getJsonConfig(ID_1))
    }

    @Test
    fun testGetConfigWithMultipleRemoteDataPayloads(): TestResult = runTest {
        coEvery { remoteData.payloads(PAYLOAD_TYPE) } returns listOf(MULTI_FORM_PAYLOAD)
        assertEquals(PREFERENCE_FORM_1, prefCenter.getConfig(ID_1))
        assertEquals(PREFERENCE_FORM_2, prefCenter.getConfig(ID_2))
    }

    @Test
    fun testDeepLink() {
        val deepLink = Uri.parse("uairship://preferences/some-preference")
        prefCenter.openListener = onOpenListener

        assertTrue(prefCenter.onAirshipDeepLink(deepLink))
        verify { onOpenListener.onOpenPreferenceCenter("some-preference") }
    }

    @Test
    fun testDeepLinkTrailingSlash() {
        val deepLink = Uri.parse("uairship://preferences/some-preference/")
        prefCenter.openListener = onOpenListener

        assertTrue(prefCenter.onAirshipDeepLink(deepLink))
        verify { onOpenListener.onOpenPreferenceCenter("some-preference") }
    }

    @Test
    fun testInvalidDeepLinks() {
        prefCenter.openListener = onOpenListener

        val wrongHost = Uri.parse("uairship://what/some-preference/")
        assertFalse(prefCenter.onAirshipDeepLink(wrongHost))

        val missingId = Uri.parse("uairship://preferences/")
        assertFalse(prefCenter.onAirshipDeepLink(missingId))

        val tooManyArgs = Uri.parse("uairship://preferences/what/what")
        assertFalse(prefCenter.onAirshipDeepLink(tooManyArgs))

        verify(exactly = 0) { onOpenListener.onOpenPreferenceCenter(any()) }
    }
}
