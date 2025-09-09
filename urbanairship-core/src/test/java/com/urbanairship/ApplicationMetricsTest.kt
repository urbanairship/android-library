/* Copyright Airship and Contributors */
package com.urbanairship

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import org.junit.Test
import org.robolectric.Shadows

public class ApplicationMetricsTest : BaseTestCase() {

    private val activityMonitor = TestActivityMonitor()
    private val dataStore = PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext())
    private val packageManager = Shadows.shadowOf(TestApplication.getApplication().packageManager)
    private val privacyManager = PrivacyManager(dataStore, PrivacyManager.Feature.ALL)

    private val metrics = ApplicationMetrics(
        context = TestApplication.getApplication(),
        preferenceDataStore = dataStore,
        privacyManager = privacyManager,
        activityMonitor = activityMonitor
    )

    /**
     * Test last open returns -1 when no opens
     * have been tracked.
     */
    @Test
    public fun testGetLastOpenNotSet() {
        metrics.init()
        Assert.assertEquals("Last open time should default to -1", -1, metrics.lastOpenTimeMillis)
    }

    /**
     * Test when a foreground broadcast is sent the
     * last open time is updated.
     */
    @Test
    public fun testLastOpenTimeTracking() {
        metrics.init()

        // Foreground the app to update last open time
        activityMonitor.foreground(1000)

        // Make sure the time is greater than 0
        Assert.assertEquals("Last open time should've updated", 1000, metrics.lastOpenTimeMillis)
    }

    @Test
    public fun testGetAppVersionUpdated() {
        dataStore.put("com.urbanairship.application.metrics.APP_VERSION", 1L)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 2
        metrics.init()

        // Version should be counted as updated
        Assert.assertTrue(metrics.appVersionUpdated)

        // Last app version should now be 2
        Assert.assertEquals(
            2L,
            dataStore.getLong("com.urbanairship.application.metrics.APP_VERSION", -1)
        )
    }

    @Test
    public fun testGetAppVersionUpdatedNoLastVersion() {
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 2
        metrics.init()

        // Version should not be counted as updated
        Assert.assertFalse(metrics.appVersionUpdated)

        // Last app version should now be 2
        Assert.assertEquals(
            2L,
            dataStore.getLong("com.urbanairship.application.metrics.APP_VERSION", -1)
        )
    }

    @Test
    public fun testGetAppVersionUpdatedEqualVersions() {
        dataStore.put("com.urbanairship.application.metrics.APP_VERSION", 2L)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 2L
        metrics.init()

        // Version should not be counted as updated
        Assert.assertFalse(metrics.appVersionUpdated)

        // Last app version should remain 2
        Assert.assertEquals(
            2L,
            dataStore.getLong("com.urbanairship.application.metrics.APP_VERSION", -1)
        )
    }

    @Test
    public fun testGetAppVersionUpdatedEarlierVersion() {
        dataStore.put("com.urbanairship.application.metrics.APP_VERSION", 2L)
        val info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().packageName)
        info.longVersionCode = 1L
        metrics.init()

        // Version should not be counted as updated
        Assert.assertFalse(metrics.appVersionUpdated)

        // Last app version should remain 1
        Assert.assertEquals(
            1L,
            dataStore.getLong("com.urbanairship.application.metrics.APP_VERSION", -1)
        )
    }
}
