/* Copyright Airship and Contributors */

package com.ad4screen.sdk.service.modules.profile;

import com.urbanairship.UAirship;
import com.urbanairship.channel.AttributeEditor;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Device information.
 */
public class DeviceInformation {

    private final AttributeEditor editor;

    /**
     * Default constructor.
     */
    public DeviceInformation() {
        editor = UAirship.shared().getChannel().editAttributes();
    }

    /**
     * Sets an attribute.
     *
     * @param key The key.
     * @param value The value.
     */
    public void set(@Nullable String key, @Nullable String value) {
        if (key != null && !key.isEmpty()) {
            editor.setAttribute(key, value);
        }
    }

    /**
     * Sets an attribute.
     *
     * @param key The key.
     * @param value The value.
     */
    public void set(@Nullable String key, @Nullable Integer value) {
        if (key != null && !key.isEmpty()) {
            editor.setAttribute(key, value);
        }
    }

    /**
     * Sets an attribute.
     *
     * @param key The key.
     * @param value The value.
     */
    public void set(@Nullable String key, @Nullable Long value) {
        if (key != null && !key.isEmpty()) {
            editor.setAttribute(key, value);
        }
    }

    /**
     * Sets an attribute.
     *
     * @param key The key.
     * @param value The value.
     */
    public void set(@Nullable String key, @Nullable Float value) {
        if (key != null && !key.isEmpty()) {
            editor.setAttribute(key, value);
        }
    }

    /**
     * Sets an attribute.
     *
     * @param key The key.
     * @param value The value.
     */
    public void set(@Nullable String key, @Nullable Double value) {
        if (key != null && !key.isEmpty()) {
            editor.setAttribute(key, value);
        }
    }

    /**
     * Sets an attribute.
     *
     * @param key The key.
     * @param value The value.
     */
    public void set(@Nullable String key, @Nullable Date value) {
        if (key != null && !key.isEmpty()) {
            editor.setAttribute(key, value);
        }
    }

    /**
     * Deletes an attribute.
     *
     * @param key The key.
     */
    public void delete(@NonNull String key) {
        editor.removeAttribute(key);
    }

    /**
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AttributeEditor getEditor() {
        return this.editor;
    }

}
