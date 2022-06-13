/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import android.app.Activity;
import android.content.Context;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestActivityMonitor;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.core.util.Consumer;
import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PermissionsManagerTest extends BaseTestCase {

    private TestActivityMonitor activityMonitor = new TestActivityMonitor();
    private final Context context = ApplicationProvider.getApplicationContext();
    private final PermissionsManager permissionsManager = PermissionsManager.newPermissionsManager(context, activityMonitor);
    private final PermissionDelegate mockDelegate = mock(PermissionDelegate.class);
    private final TestConsumer<PermissionStatus> testStatusCallback = new TestConsumer<>();
    private final TestConsumer<PermissionRequestResult> testRequestCallback = new TestConsumer<>();

    private final TestConsumer<Permission> testAirshipEnabler = new TestConsumer<>();
    private final OnPermissionStatusChangedListener mockStatusListener = mock(OnPermissionStatusChangedListener.class);

    private PermissionStatus mockDelegateStatus = PermissionStatus.NOT_DETERMINED;

    @Before
    public void setup() {
        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(mockDelegateStatus);
            return null;
        }).when(mockDelegate).checkPermissionStatus(any(), any());

        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(new PermissionRequestResult(mockDelegateStatus, false));
            return null;
        }).when(mockDelegate).requestPermission(any(), any());
    }

    @Test
    public void testCheckPermissionNoDelegate() {
        permissionsManager.requestPermission(Permission.LOCATION, testRequestCallback);
        shadowMainLooper().idle();
        assertEquals(PermissionStatus.NOT_DETERMINED, testRequestCallback.lastResult.getPermissionStatus());
    }

    @Test
    public void testRequestPermissionNoDelegate() {
        permissionsManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS, testRequestCallback);
        shadowMainLooper().idle();
        assertEquals(PermissionStatus.NOT_DETERMINED, testRequestCallback.lastResult.getPermissionStatus());
    }

    @Test
    public void testConfiguredPermissions() {
        Set<Permission> expected = new HashSet<>();
        assertEquals(expected, permissionsManager.getConfiguredPermissions());

        expected.add(Permission.LOCATION);
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);
        assertEquals(expected, permissionsManager.getConfiguredPermissions());

        expected.add(Permission.DISPLAY_NOTIFICATIONS);
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate);
        assertEquals(expected, permissionsManager.getConfiguredPermissions());
    }

    @Test
    public void testRequestPermission() {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);
        this.mockDelegateStatus = PermissionStatus.GRANTED;
        permissionsManager.requestPermission(Permission.LOCATION, testRequestCallback);
        shadowMainLooper().idle();
        assertEquals(PermissionStatus.GRANTED, testRequestCallback.lastResult.getPermissionStatus());
    }

    @Test
    public void testOnEnableAirship() {
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate);
        this.mockDelegateStatus = PermissionStatus.GRANTED;

        permissionsManager.addAirshipEnabler(testAirshipEnabler);
        permissionsManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS, true);
        shadowMainLooper().idle();

        assertEquals(Permission.DISPLAY_NOTIFICATIONS, testAirshipEnabler.lastResult);
    }

    @Test
    public void testOnEnableAirshipDenied() {
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate);
        this.mockDelegateStatus = PermissionStatus.DENIED;

        permissionsManager.addAirshipEnabler(testAirshipEnabler);

        permissionsManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS, true);
        shadowMainLooper().idle();

        assertNull(testAirshipEnabler.lastResult);
    }

    @Test
    public void testOnEnableAirshipNotDetermined() {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);
        this.mockDelegateStatus = PermissionStatus.NOT_DETERMINED;
        permissionsManager.addAirshipEnabler(testAirshipEnabler);

        permissionsManager.requestPermission(Permission.LOCATION, true);
        shadowMainLooper().idle();

        assertNull(testAirshipEnabler.lastResult);
    }

    @Test
    public void testStatusChangeCheckOnRequest() {
        this.mockDelegateStatus = PermissionStatus.GRANTED;
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);
        shadowMainLooper().idle();

        permissionsManager.addOnPermissionStatusChangedListener(mockStatusListener);

        permissionsManager.requestPermission(Permission.LOCATION);
        shadowMainLooper().idle();

        verify(mockStatusListener, never()).onPermissionStatusChanged(any(), any());

        this.mockDelegateStatus = PermissionStatus.DENIED;
        permissionsManager.requestPermission(Permission.LOCATION);
        shadowMainLooper().idle();

        verify(mockStatusListener).onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.DENIED);
    }

    @Test
    public void testStatusChangeCheckOnCheck() {
        this.mockDelegateStatus = PermissionStatus.DENIED;
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);
        shadowMainLooper().idle();

        permissionsManager.addOnPermissionStatusChangedListener(mockStatusListener);

        permissionsManager.checkPermissionStatus(Permission.LOCATION);
        shadowMainLooper().idle();

        verify(mockStatusListener, never()).onPermissionStatusChanged(any(), any());

        this.mockDelegateStatus = PermissionStatus.GRANTED;
        permissionsManager.checkPermissionStatus(Permission.LOCATION);
        shadowMainLooper().idle();

        verify(mockStatusListener).onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.GRANTED);
    }

    @Test
    public void testStatusChangeCheckOnActivityResume() {
        this.mockDelegateStatus = PermissionStatus.DENIED;
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);
        shadowMainLooper().idle();

        permissionsManager.addOnPermissionStatusChangedListener(mockStatusListener);

        this.mockDelegateStatus = PermissionStatus.NOT_DETERMINED;
        this.activityMonitor.resumeActivity(new Activity());
        shadowMainLooper().idle();

        verify(mockStatusListener).onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.NOT_DETERMINED);
    }

    @Test
    public void testDedupeRequests() {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);
        this.mockDelegateStatus = PermissionStatus.GRANTED;

        permissionsManager.requestPermission(Permission.LOCATION, testRequestCallback);
        permissionsManager.requestPermission(Permission.LOCATION, testRequestCallback);
        permissionsManager.requestPermission(Permission.LOCATION, testRequestCallback);
        permissionsManager.requestPermission(Permission.LOCATION, testRequestCallback);
        permissionsManager.requestPermission(Permission.LOCATION, testRequestCallback);
        permissionsManager.requestPermission(Permission.LOCATION, testRequestCallback);

        shadowMainLooper().idle();
        assertEquals(PermissionStatus.GRANTED, testRequestCallback.lastResult.getPermissionStatus());
        assertEquals(6, testRequestCallback.results.size());
        verify(mockDelegate, times(1)).requestPermission(any(), any());
    }

    @Test
    public void testDedupeChecks() {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);
        this.mockDelegateStatus = PermissionStatus.GRANTED;

        permissionsManager.checkPermissionStatus(Permission.LOCATION, testStatusCallback);
        permissionsManager.checkPermissionStatus(Permission.LOCATION, testStatusCallback);
        permissionsManager.checkPermissionStatus(Permission.LOCATION, testStatusCallback);
        permissionsManager.checkPermissionStatus(Permission.LOCATION, testStatusCallback);
        permissionsManager.checkPermissionStatus(Permission.LOCATION, testStatusCallback);
        permissionsManager.checkPermissionStatus(Permission.LOCATION, testStatusCallback);

        shadowMainLooper().idle();
        assertEquals(PermissionStatus.GRANTED, testStatusCallback.lastResult);
        assertEquals(6, testStatusCallback.results.size());
        verify(mockDelegate, times(1)).checkPermissionStatus(any(), any());
    }


    private static class TestConsumer<T> implements Consumer<T> {
        T lastResult;
        List<T> results = new ArrayList<>();

        @Override
        public void accept(T result) {
            this.lastResult = result;
            this.results.add(result);
        }

    }

}
