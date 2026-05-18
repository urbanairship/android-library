/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behavior unique to [EagerPreferenceStore] — typed-key round-trips are covered by
 * [PreferenceStoreTest]; here we verify the fallback-load path that drops corrupt rows when the
 * batch query fails.
 */
@RunWith(AndroidJUnit4::class)
public class EagerPreferenceStoreTest {

    private val mockDao = mockk<PreferenceDao>(relaxed = true)
    private val mockDb = mockk<PreferenceDatabase> {
        every { dao } returns mockDao
    }

    @Test
    public fun fallbackLoadDeletesKeyWhenFindRowThrows(): Unit = runTest {
        coEvery { mockDao.queryEagerPreferences() } throws RuntimeException("batch load failed")
        coEvery { mockDao.queryEagerKeys() } returns listOf("bad", "good")
        coEvery { mockDao.findRow("bad") } throws RuntimeException("row read failed")
        coEvery { mockDao.findRow("good") } returns PreferenceData("good", "saved")

        val store = EagerPreferenceStore(mockDb)
        store.loadPreferences()

        coVerify { mockDao.delete("bad") }
        assertEquals("saved", store.get("good"))
    }

    @Test
    public fun fallbackLoadDeletesKeyWhenValueIsNull(): Unit = runTest {
        coEvery { mockDao.queryEagerPreferences() } throws RuntimeException("batch load failed")
        coEvery { mockDao.queryEagerKeys() } returns listOf("empty", "good")
        coEvery { mockDao.findRow("empty") } returns PreferenceData("empty", null)
        coEvery { mockDao.findRow("good") } returns PreferenceData("good", "ok")

        val store = EagerPreferenceStore(mockDb)
        store.loadPreferences()

        coVerify { mockDao.delete("empty") }
        assertEquals("ok", store.get("good"))
        assertNull(store.get("empty"))
    }
}
