/* Copyright Airship and Contributors */

package com.urbanairship.javascript;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

/**
 * The Airship JavaScript Environment.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JavaScriptEnvironment {

    private final List<String> getters;

    private JavaScriptEnvironment(@NonNull Builder builder) {
        this.getters = new ArrayList<>(builder.getters);
    }

    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    @WorkerThread
    public String getJavaScript(@NonNull Context context) {

        /*
         * The native bridge will prototype _UAirship, so inject any additional
         * functionality under _UAirship and the final UAirship object will have
         * access to it.
         */
        StringBuilder sb = new StringBuilder().append("var _UAirship = {};");

        for (String getter : getters) {
            sb.append(getter);
        }

        try {
            sb.append(readNativeBridge(context));
        } catch (IOException e) {
            Logger.error("Failed to read native bridge.");
            return "";
        }

        return sb.toString();
    }

    /**
     * Helper method to read the native bridge from resources.
     *
     * @return The native bridge.
     * @throws IOException if output steam read or write operations fail.
     */
    @WorkerThread
    private static String readNativeBridge(@NonNull final Context context) throws IOException {
        InputStream input = context.getResources().openRawResource(R.raw.ua_native_bridge);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            byte[] buffer = new byte[1024];
            int length;

            while ((length = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }

            return outputStream.toString();
        } finally {
            try {
                input.close();
                outputStream.close();
            } catch (Exception e) {
                Logger.debug(e, "Failed to close streams");
            }
        }
    }

    public static class Builder {

        private final List<String> getters = new ArrayList<>();

        private Builder() {}

        @NonNull
        public Builder addGetter(@NonNull String functionName, @Nullable String value) {
            return addGetter(functionName, JsonValue.wrapOpt(value));
        }

        @NonNull
        public Builder addGetter(@NonNull String functionName, long value) {
            return addGetter(functionName, JsonValue.wrapOpt(value));
        }

        @NonNull
        public Builder addGetter(@NonNull String functionName, @Nullable JsonSerializable value) {
            JsonValue json = value == null ? JsonValue.NULL : value.toJsonValue();
            String getter = String.format(Locale.ROOT, "_UAirship.%s = function(){return %s;};", functionName, json.toString());
            getters.add(getter);
            return this;
        }

        @NonNull
        public JavaScriptEnvironment build() {
            return new JavaScriptEnvironment(this);
        }

    }

}
