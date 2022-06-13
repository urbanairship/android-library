/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonMap;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionRequestResult;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsManager;

import org.junit.Test;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PromptPermissionActionTest extends BaseTestCase {

    private final PermissionsManager mockPermissionManager = mock(PermissionsManager.class);
    private final PromptPermissionAction action = new PromptPermissionAction(() -> mockPermissionManager);

    @Test
    public void testAcceptsArgumentsSituations() {
        int[] acceptedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_AUTOMATION
        };

        int[] rejectedSituations = new int[] {
                Action.SITUATION_PUSH_RECEIVED,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON
        };

        JsonMap value = JsonMap.newBuilder()
                               .put(PromptPermissionAction.PERMISSION_ARG_KEY, Permission.LOCATION)
                               .build();

        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, value);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }

        for (@Action.Situation int situation : rejectedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, value);
            assertFalse("Should reject arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    @Test
    public void testPermissions() {
        doAnswer(invocation -> {
            Consumer<PermissionStatus> consumer = invocation.getArgument(1);
            consumer.accept(PermissionStatus.DENIED);
            return null;
        }).when(mockPermissionManager).checkPermissionStatus(any(), any());


        for (Permission permission : Permission.values()) {
            JsonMap value = JsonMap.newBuilder()
                                   .put(PromptPermissionAction.PERMISSION_ARG_KEY, permission)
                                   .build();

            ActionArguments actionArguments = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, value);
            action.perform(actionArguments);

            verify(mockPermissionManager).checkPermissionStatus(eq(permission), any());
            verify(mockPermissionManager).requestPermission(eq(permission), eq(false), any());
        }
    }

    @Test
    public void testEnableAirshipUsage() {
        doAnswer(invocation -> {
            Consumer<PermissionStatus> consumer = invocation.getArgument(1);
            consumer.accept(PermissionStatus.DENIED);
            return null;
        }).when(mockPermissionManager).checkPermissionStatus(any(), any());

        JsonMap value = JsonMap.newBuilder()
                               .put(PromptPermissionAction.PERMISSION_ARG_KEY, Permission.DISPLAY_NOTIFICATIONS)
                               .put(PromptPermissionAction.ENABLE_AIRSHIP_USAGE_ARG_KEY, true)
                               .build();

        ActionArguments actionArguments = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, value);
        action.perform(actionArguments);

        verify(mockPermissionManager).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), eq(true), any());
    }

    @Test
    public void testResult() {
        doAnswer(invocation -> {
            Consumer<PermissionStatus> consumer = invocation.getArgument(1);
            consumer.accept(PermissionStatus.DENIED);
            return null;
        }).when(mockPermissionManager).checkPermissionStatus(eq(Permission.DISPLAY_NOTIFICATIONS), any());

        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> consumer = invocation.getArgument(2);
            consumer.accept(PermissionRequestResult.granted());
            return null;
        }).when(mockPermissionManager).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), eq(false), any());

        Handler handler = new Handler(Looper.getMainLooper());
        TestReceiver resultReceiver = new TestReceiver(handler);

        JsonMap value = JsonMap.newBuilder()
                               .put(PromptPermissionAction.PERMISSION_ARG_KEY, Permission.DISPLAY_NOTIFICATIONS)
                               .build();

        Bundle metadata = new Bundle();
        metadata.putParcelable(PromptPermissionAction.RECEIVER_METADATA, resultReceiver);

        ActionArguments actionArguments = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, value, metadata);
        action.perform(actionArguments);

        shadowMainLooper().runToEndOfTasks();

        assertEquals(PermissionStatus.DENIED, resultReceiver.before);
        assertEquals(PermissionStatus.GRANTED, resultReceiver.after);
        assertEquals(Permission.DISPLAY_NOTIFICATIONS, resultReceiver.permission);
    }

    static class TestReceiver extends PermissionResultReceiver {

        Permission permission;
        PermissionStatus before;
        PermissionStatus after;


        public TestReceiver(Handler handler) {
            super(handler);
        }

        @Override
        public void onResult(@NonNull Permission permission, @NonNull PermissionStatus before, @NonNull PermissionStatus after) {
            this.permission = permission;
            this.before = before;
            this.after = after;
        }

    }
}
