/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.widget.ImageView;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.MediaFit;
import com.urbanairship.android.layout.property.MediaType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MediaModel extends BaseModel implements Accessible {
    @NonNull
    private final String url;
    @NonNull
    private final MediaType mediaType;
    @NonNull
    private final ImageView.ScaleType scaleType;
    @Nullable
    private final String contentDescription;

    public MediaModel(
        @NonNull String url,
        @NonNull MediaType mediaType,
        @NonNull ImageView.ScaleType scaleType,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.MEDIA, backgroundColor, border);

        this.url = url;
        this.mediaType = mediaType;
        this.scaleType = scaleType;
        this.contentDescription = contentDescription;
    }

    @NonNull
    public static MediaModel fromJson(@NonNull JsonMap json) throws JsonException {
        String url = json.opt("url").optString();
        String mediaTypeString = json.opt("media_type").optString();
        String mediaFitString = json.opt("media_fit").optString();
        String contentDescription = Accessible.contentDescriptionFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        MediaType mediaType = MediaType.from(mediaTypeString);
        ImageView.ScaleType objectFit = MediaFit.asScaleType(mediaFitString);

        return new MediaModel(url, mediaType, objectFit, contentDescription, backgroundColor, border);
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public MediaType getMediaType() {
        return mediaType;
    }

    @NonNull
    public ImageView.ScaleType getScaleType() {
        return scaleType;
    }

    @Nullable
    @Override
    public String getContentDescription() {
        return contentDescription;
    }
}
