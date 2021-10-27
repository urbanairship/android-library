/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.MediaType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public class MediaModel extends BaseModel {
    @NonNull
    private final String url;
    @NonNull
    private final MediaType mediaType;

    public MediaModel(@NonNull String url, @NonNull MediaType mediaType) {
        super(ViewType.MEDIA);

        this.url = url;
        this.mediaType = mediaType;
    }

    @NonNull
    public static MediaModel fromJson(@NonNull JsonMap json) {
        String url = json.opt("url").optString();
        String mediaTypeString = json.opt("mediaType").optString();

        MediaType mediaType = MediaType.from(mediaTypeString);

        return new MediaModel(url, mediaType);
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public MediaType getMediaType() {
        return mediaType;
    }
}
