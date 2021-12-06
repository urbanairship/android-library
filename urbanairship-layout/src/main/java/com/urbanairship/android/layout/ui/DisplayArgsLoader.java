/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * A display args loader will store a reference to the
 * layout args and restore the instance when it to when
 * loading from a parcel without actually placing the args
 * in a parcel.
 *
 * This only works in a single process.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DisplayArgsLoader implements Parcelable {
    private final static Map<String, DisplayArgs> cached = new HashMap<>();

    private final String id;

    private DisplayArgsLoader(@Nullable String id) {
        this.id = id;
    }

    private DisplayArgsLoader(Parcel in) {
        this.id = in.readString();
    }

    public static final Creator<DisplayArgsLoader> CREATOR = new Creator<DisplayArgsLoader>() {
        @Override
        public DisplayArgsLoader createFromParcel(Parcel in) {
            return new DisplayArgsLoader(in);
        }

        @Override
        public DisplayArgsLoader[] newArray(int size) {
            return new DisplayArgsLoader[size];
        }
    };

    /**
     * Creates a new layout args loader. The args will be cached, to remove them from
     * the cache call `dispose` on the instance.
     *
     * @param args The layout args.
     * @return A layout args loader.
     */
    public static DisplayArgsLoader newLoader(@NonNull DisplayArgs args) {
        String loaderId = UUID.randomUUID().toString();
        cached.put(loaderId, args);
        return new DisplayArgsLoader(loaderId);
    }

    public void dispose() {
        cached.remove(id);
    }

    @NonNull
    public DisplayArgs getLayoutArgs() throws LoadException {
        DisplayArgs args = cached.get(id);
        if (args == null) {
            throw new LoadException("Layout args no longer available");
        }

        return args;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
    }

    public static class LoadException extends Exception {
        public LoadException(String message) {
            super(message);
        }
    }
}
