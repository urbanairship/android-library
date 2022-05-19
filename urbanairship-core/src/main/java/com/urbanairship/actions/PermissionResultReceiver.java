/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionStatus;

import androidx.annotation.NonNull;

/**
 * Result receiver to receive permission results from the {@link PromptPermissionAction}.
 */
public abstract class PermissionResultReceiver extends ResultReceiver {

    /**
     * Default constructor.
     *
     * @param handler The handler to receive the result callback.
     */
    public PermissionResultReceiver(@NonNull Handler handler) {
        super(handler);
    }

    /**
     * Called when a new permission result is received.
     *
     * @param permission The permission.
     * @param before The status before requesting permission.
     * @param after The resulting status.
     */
    public abstract void onResult(@NonNull Permission permission, @NonNull PermissionStatus before, @NonNull PermissionStatus after);

    @Override
    final protected void onReceiveResult(int resultCode, Bundle resultData) {
        super.onReceiveResult(resultCode, resultData);
        try {
            Permission permission = parsePermission(resultData, PromptPermissionAction.PERMISSION_RESULT_KEY);
            PermissionStatus before = parseStatus(resultData, PromptPermissionAction.BEFORE_PERMISSION_STATUS_RESULT_KEY);
            PermissionStatus after = parseStatus(resultData, PromptPermissionAction.AFTER_PERMISSION_STATUS_RESULT_KEY);
            onResult(permission, before, after);
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse result");
        }
    }

    @NonNull
    public static Permission parsePermission(Bundle bundle, String key) throws JsonException {
        JsonValue value = JsonValue.parseString(bundle.getString(key));
        return Permission.fromJson(value);
    }

    @NonNull
    public static PermissionStatus parseStatus(Bundle bundle, String key) throws JsonException {
        JsonValue value = JsonValue.parseString(bundle.getString(key));
        return PermissionStatus.fromJson(value);
    }

}
