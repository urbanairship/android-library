package com.urbanairship.locale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import java.util.Locale
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [LocaleManager]
 */
@RunWith(AndroidJUnit4::class)
class LocaleManagerTest {

    private var context: Context = ApplicationProvider.getApplicationContext()
    private var localeManager = LocaleManager(context, PreferenceDataStore.inMemoryStore(context))

    @Test
    fun testGetLocale() {
        val de = Locale("de")
        context.resources.configuration.setLocale(de)

        Assert.assertEquals(de, localeManager.locale)
    }

    @Test
    fun testNotifyLocaleChanged() {
        val en = Locale("en")
        context.resources.configuration.setLocale(en)

        val listener: LocaleChangedListener = mockk(relaxed = true)
        localeManager.addListener(listener)

        localeManager.onDeviceLocaleChanged()

        verify { listener.onLocaleChanged(en) }
    }
}
