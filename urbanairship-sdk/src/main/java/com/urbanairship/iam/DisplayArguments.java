/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Arguments passed into {@link InAppMessageAdapter#display(Activity, DisplayArguments)}.
 */
public class DisplayArguments implements Parcelable {

    private final InAppMessage message;
    private final Bundle assets;
    private final boolean isRedisplay;
    private final DisplayHandler displayHandler;

    /**
     * Default constructor.
     *
     * @param message The in-app message
     * @param assets The in-app message's assets.
     * @param isRedisplay If the in-app message is being redisplayed.
     * @param displayHandler The display handler.
     */
    DisplayArguments(@NonNull InAppMessage message, @NonNull Bundle assets, boolean isRedisplay, @NonNull DisplayHandler displayHandler) {
        this.message = message;
        this.assets = assets;
        this.isRedisplay = isRedisplay;
        this.displayHandler = displayHandler;
    }

    private DisplayArguments(Parcel in) {
        message = in.readParcelable(InAppMessage.class.getClassLoader());
        assets = in.readBundle(getClass().getClassLoader());
        isRedisplay = in.readByte() != 0;
        displayHandler = in.readParcelable(DisplayHandler.class.getClassLoader());
    }

    /**
     * Gets the in-app message.
     *
     * @return The in-app message.
     */
    public InAppMessage getMessage() {
        return message;
    }

    /**
     * Flag indicating if the in-app message is being redisplayed.
     *
     * @return {@code true} if the in-app message is being redisplayed, otherwise {@code false}.
     */
    public boolean isRedisplay() {
        return isRedisplay;
    }

    /**
     * Gets the prefetched assets.
     *
     * @return The prefetched assets as a bundle.
     */
    public Bundle getAssets() {
        return assets;
    }

    /**
     * Gets the display handler for the in-app message.
     *
     * @return The display handler.
     */
    public DisplayHandler getHandler() {
        return displayHandler;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (isRedisplay ? 1 : 0));
        dest.writeBundle(assets);
        dest.writeParcelable(message, flags);
        dest.writeParcelable(displayHandler, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Creator for parcelable interface.
     *
     * @hide
     */
    public static final Creator<DisplayArguments> CREATOR = new Creator<DisplayArguments>() {
        @Override
        public DisplayArguments createFromParcel(Parcel in) {
            return new DisplayArguments(in);
        }

        @Override
        public DisplayArguments[] newArray(int size) {
            return new DisplayArguments[size];
        }
    };

}
