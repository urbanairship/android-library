/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.wallet;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

/**
 * Pass representing either an offer or loyalty wallet object.
 *
 * Once a pass is obtained, call {@link #requestToSavePass(Context)}.
 */
public class Pass implements Parcelable {

    private static final String PUBLIC_URL_KEY = "publicUrl";
    private static final String PUBLIC_URL_PATH_KEY = "path";
    private static final String ID_KEY = "id";

    public static final Creator<Pass> CREATOR = new Creator<Pass>() {
        @Override
        public Pass createFromParcel(Parcel in) {
            return new Pass(in);
        }

        @Override
        public Pass[] newArray(int size) {
            return new Pass[size];
        }
    };

    private final Uri publicUri;
    private final String id;

    /**
     * Default constructor.
     *
     * @param uri The publicly accessible URI for the pass.
     * @param id The pass ID.
     */
    Pass(Uri uri, String id) {
        this.publicUri = uri;
        this.id = id;
    }

    protected Pass(Parcel in) {
        publicUri = in.readParcelable(Uri.class.getClassLoader());
        id = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(publicUri, flags);
        dest.writeString(id);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Requests to save the pass.
     *
     * @param context The application context.
     */
    public void requestToSavePass(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setData(publicUri)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }

    /**
     * Get the pass URI to link to Android Pay.
     *
     * @return The pass URI.
     */
    public Uri getPublicUri() {
        return publicUri;
    }

    /**
     * Get the pass ID.
     *
     * @return The pass ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Parse a pass.
     *
     * @param pass The pass response.
     * @return A {@link Pass}.
     */
    static Pass parsePass(JsonValue pass) {
        String id = pass.optMap().opt(ID_KEY).getString();
        String uriString = pass.optMap().opt(PUBLIC_URL_KEY).optMap().opt(PUBLIC_URL_PATH_KEY).getString();
        if (!UAStringUtil.isEmpty(uriString)) {
            Uri url = Uri.parse(uriString);
            return new Pass(url, id);
        }

        Logger.error("Pass - unable to parse URI from " + pass);
        return null;
    }
}
