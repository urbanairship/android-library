/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import android.content.Context;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import androidx.core.util.Consumer;
import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class PermissionsManagerTest extends BaseTestCase {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final PermissionsManager permissionsManager = new PermissionsManager(context);
    private final PermissionDelegate mockDelegate = mock(PermissionDelegate.class);
    private final TestConsumer<PermissionStatus> testStatusCallback = new TestConsumer<>();
    private final TestConsumer<Permission> testAirshipEnabler = new TestConsumer<>();

    @Test
    public void testCheckPermissionNoDelegate() {
        permissionsManager.requestPermission(Permission.LOCATION, testStatusCallback);
        shadowMainLooper().runToEndOfTasks();
        assertEquals(PermissionStatus.NOT_DETERMINED, testStatusCallback.result);
    }

    @Test
    public void testRequestPermissionNoDelegate() {
        permissionsManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS, testStatusCallback);
        shadowMainLooper().runToEndOfTasks();
        assertEquals(PermissionStatus.NOT_DETERMINED, testStatusCallback.result);
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
    public void testRequestPermissionAlreadyGranted() {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.GRANTED);
            return null;
        }).when(mockDelegate).checkPermissionStatus(any(), any());

        permissionsManager.requestPermission(Permission.LOCATION, testStatusCallback);
        shadowMainLooper().runToEndOfTasks();
        assertEquals(PermissionStatus.GRANTED, testStatusCallback.result);

        verify(mockDelegate, never()).requestPermission(any(), any());
    }

    @Test
    public void testRequestPermission() {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.DENIED);
            return null;
        }).when(mockDelegate).checkPermissionStatus(any(), any());

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.GRANTED);
            return null;
        }).when(mockDelegate).requestPermission(any(), any());

        permissionsManager.requestPermission(Permission.LOCATION, testStatusCallback);
        shadowMainLooper().runToEndOfTasks();
        assertEquals(PermissionStatus.GRANTED, testStatusCallback.result);
    }

    @Test
    public void testOnEnableAirship() {
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.DENIED);
            return null;
        }).when(mockDelegate).checkPermissionStatus(any(), any());


        permissionsManager.addAirshipEnabler(testAirshipEnabler);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.GRANTED);
            return null;
        }).when(mockDelegate).requestPermission(any(), any());

        permissionsManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS, true);
        shadowMainLooper().runToEndOfTasks();

        assertEquals(Permission.DISPLAY_NOTIFICATIONS, testAirshipEnabler.result);
    }

    @Test
    public void testOnEnableAirshipDenied() {
        permissionsManager.setPermissionDelegate(Permission.DISPLAY_NOTIFICATIONS, mockDelegate);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.DENIED);
            return null;
        }).when(mockDelegate).checkPermissionStatus(any(), any());


        permissionsManager.addAirshipEnabler(testAirshipEnabler);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.DENIED);
            return null;
        }).when(mockDelegate).requestPermission(any(), any());

        permissionsManager.requestPermission(Permission.DISPLAY_NOTIFICATIONS, true);
        shadowMainLooper().runToEndOfTasks();

        assertNull(testAirshipEnabler.result);
    }

    @Test
    public void testOnEnableAirshipNotDetermined() {
        permissionsManager.setPermissionDelegate(Permission.LOCATION, mockDelegate);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.DENIED);
            return null;
        }).when(mockDelegate).checkPermissionStatus(any(), any());

        permissionsManager.addAirshipEnabler(testAirshipEnabler);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionStatus.NOT_DETERMINED);
            return null;
        }).when(mockDelegate).requestPermission(any(), any());

        permissionsManager.requestPermission(Permission.LOCATION, true);
        shadowMainLooper().runToEndOfTasks();

        assertNull(testAirshipEnabler.result);
    }

    private static class TestConsumer<T> implements Consumer<T> {

        T result;

        @Override
        public void accept(T result) {
            this.result = result;
        }

    }

}
