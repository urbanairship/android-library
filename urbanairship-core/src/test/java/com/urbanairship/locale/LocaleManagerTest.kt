package com.urbanairship.locale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.util.LocaleCompat
import java.util.Locale
import app.cash.turbine.test
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [LocaleManager]
 */
@RunWith(AndroidJUnit4::class)
public class LocaleManagerTest {

    private var context: Context = ApplicationProvider.getApplicationContext()
    private var localeManager = LocaleManager(context, PreferenceDataStore.inMemoryStore(context))

    @Test
    public fun testGetLocale() {
        val de = LocaleCompat.of("de")
        context.resources.configuration.setLocale(de)

        Assert.assertEquals(de, localeManager.locale)
    }

    @Test
    public fun testLocaleUpdatesFlow(): TestResult = runTest {
        localeManager.localeUpdates.test {
            val en = LocaleCompat.of("en")
            context.resources.configuration.setLocale(en)

            localeManager.onDeviceLocaleChanged()

            Assert.assertEquals(en, awaitItem())
        }
    }
}
