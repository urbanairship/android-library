/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.pm.PackageInfo;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowPackageManager;

import androidx.test.core.app.ApplicationProvider;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

public class ApplicationMetricsTest extends BaseTestCase {

    private ApplicationMetrics metrics;
    private TestActivityMonitor activityMonitor;
    private PreferenceDataStore dataStore;
    private ShadowPackageManager packageManager;

    @Before
    public void setup() {
        dataStore = new PreferenceDataStore(ApplicationProvider.getApplicationContext());
        activityMonitor = new TestActivityMonitor();
        packageManager = shadowOf(TestApplication.getApplication().getPackageManager());
        metrics = new ApplicationMetrics(TestApplication.getApplication(), dataStore, activityMonitor);
    }

    /**
     * Test last open returns -1 when no opens
     * have been tracked.
     */
    @Test
    public void testGetLastOpenNotSet() {
        metrics.init();
        assertEquals("Last open time should default to -1", -1, metrics.getLastOpenTimeMillis());
    }

    /**
     * Test when a foreground broadcast is sent the
     * last open time is updated.
     */
    @Test
    public void testLastOpenTimeTracking() {
        metrics.init();

        // Foreground the app to update last open time
        activityMonitor.foreground(1000);

        // Make sure the time is greater than 0
        assertEquals("Last open time should've updated", 1000, metrics.getLastOpenTimeMillis());
    }

    @Test
    public void testGetAppVersionUpdated() {
        dataStore.put("com.urbanairship.application.metrics.APP_VERSION", 1L);
        PackageInfo info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().getPackageName());
        info.setLongVersionCode(2);
        metrics.init();

        // Version should be counted as updated
        assertTrue(metrics.getAppVersionUpdated());

        // Last app version should now be 2
        assertEquals(2L,  dataStore.getLong("com.urbanairship.application.metrics.APP_VERSION", -1));    }

    @Test
    public void testGetAppVersionUpdatedNoLastVersion() {
        PackageInfo info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().getPackageName());
        info.setLongVersionCode(2);
        metrics.init();

        // Version should not be counted as updated
        assertFalse(metrics.getAppVersionUpdated());

        // Last app version should now be 2
        assertEquals(2L,  dataStore.getLong("com.urbanairship.application.metrics.APP_VERSION", -1));
    }

    @Test
    public void testGetAppVersionUpdatedEqualVersions() {
        dataStore.put("com.urbanairship.application.metrics.APP_VERSION", 2L);
        PackageInfo info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().getPackageName());
        info.setLongVersionCode(2L);
        metrics.init();

        // Version should not be counted as updated
        assertFalse(metrics.getAppVersionUpdated());

        // Last app version should remain 2
        assertEquals(2L, dataStore.getLong("com.urbanairship.application.metrics.APP_VERSION", -1));
    }

    @Test
    public void testGetAppVersionUpdatedEarlierVersion() {
        dataStore.put("com.urbanairship.application.metrics.APP_VERSION", 2L);
        PackageInfo info = packageManager.getInternalMutablePackageInfo(TestApplication.getApplication().getPackageName());
        info.setLongVersionCode(1L);
        metrics.init();

        // Version should not be counted as updated
        assertFalse(metrics.getAppVersionUpdated());

        // Last app version should remain 1
        assertEquals(1L,  dataStore.getLong("com.urbanairship.application.metrics.APP_VERSION", -1));    }
}
