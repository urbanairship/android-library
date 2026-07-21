/* Copyright Airship and Contributors */
package com.urbanairship.automation.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.BaseTestCase
import com.urbanairship.PrivacyManager
import com.urbanairship.TestApplication
import com.urbanairship.preferences.AsyncPrefKey
import com.urbanairship.preferences.PreferenceStore
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.robolectric.Shadows

private val APP_VERSION_KEY = AsyncPrefKey.long("com.urbanairship.application.metrics.APP_VERSION")
private val APP_VERSION_NAME_KEY = AsyncPrefKey.string("com.urbanairship.application.metrics.APP_VERSION_NAME")

public class ApplicationMetricsTest : BaseTestCase() {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val dataStore = PreferenceStore.inMemoryStore(ApplicationProvider.getApplicationContext())
    private val packageManager = Shadows.shadowOf(context.packageManager)
    private val privacyManager = PrivacyManager(dataStore, PrivacyManager.Feature.ALL)

    @Test
    public fun testGetAppVersionUpdated(): TestResult = runTest {
        dataStore.put(APP_VERSION_KEY, 1L)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 2

        val metrics = ApplicationMetrics(
            context = context,
            dataStore = dataStore,
            privacyManager = privacyManager,
        )

        // Version should be counted as updated
        Assert.assertTrue(metrics.isAppVersionUpdated())

        // Last app version should now be 2
        Assert.assertEquals(2L, dataStore.get(APP_VERSION_KEY) ?: -1L)
    }

    @Test
    public fun testPreviousVersionExposedOnUpgrade(): TestResult = runTest {
        dataStore.put(APP_VERSION_KEY, 1L)
        dataStore.put(APP_VERSION_NAME_KEY, "1.0.0")
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 2
        info.versionName = "2.0.0"

        val metrics = ApplicationMetrics(
            context = context,
            dataStore = dataStore,
            privacyManager = privacyManager,
        )

        Assert.assertTrue(metrics.isAppVersionUpdated())
        Assert.assertEquals(1L, metrics.previousAppVersion)
        Assert.assertEquals("1.0.0", metrics.previousAppVersionName)

        // Current version name should now be persisted for the next upgrade
        Assert.assertEquals("2.0.0", dataStore.get(APP_VERSION_NAME_KEY))
    }

    @Test
    public fun testPreviousVersionNullWhenNoUpgrade(): TestResult = runTest {
        dataStore.put(APP_VERSION_KEY, 2L)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 2

        val metrics = ApplicationMetrics(
            context = context,
            dataStore = dataStore,
            privacyManager = privacyManager,
        )

        Assert.assertFalse(metrics.isAppVersionUpdated())
        Assert.assertNull(metrics.previousAppVersion)
        Assert.assertNull(metrics.previousAppVersionName)
    }

    @Test
    public fun testGetAppVersionUpdatedNoLastVersion(): TestResult = runTest {
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 2

        val first = ApplicationMetrics(
            context = context,
            dataStore = dataStore,
            privacyManager = privacyManager,
        )

        // No prior version persisted → not counted as an update.
        Assert.assertFalse(first.isAppVersionUpdated())

        // first.isAppVersionUpdated() awaited init, so the new app version is now persisted.
        val second = ApplicationMetrics(
            context = context,
            dataStore = dataStore,
            privacyManager = privacyManager,
        )

        Assert.assertFalse(second.isAppVersionUpdated())

        // Last app version should now be 2
        Assert.assertEquals(2L, dataStore.get(APP_VERSION_KEY) ?: -1L)
    }

    @Test
    public fun testGetAppVersionUpdatedEqualVersions(): TestResult = runTest {
        dataStore.put(APP_VERSION_KEY, 2L)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 2L

        val metrics = ApplicationMetrics(
            context = context,
            dataStore = dataStore,
            privacyManager = privacyManager,
        )

        Assert.assertFalse(metrics.isAppVersionUpdated())

        Assert.assertEquals(2L, dataStore.get(APP_VERSION_KEY) ?: -1L)
    }

    @Test
    public fun testGetAppVersionUpdatedEarlierVersion(): TestResult = runTest {
        dataStore.put(APP_VERSION_KEY, 2L)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 1L

        val metrics = ApplicationMetrics(
            context = context,
            dataStore = dataStore,
            privacyManager = privacyManager,
        )

        Assert.assertFalse(metrics.isAppVersionUpdated())

        Assert.assertEquals(1L, dataStore.get(APP_VERSION_KEY) ?: -1L)
    }
}
