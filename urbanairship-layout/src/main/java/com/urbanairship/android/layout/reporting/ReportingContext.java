/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonList;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * Used for reporting. Allows us to target in_app experience displays that contain surveys and/or tours.
 */
public class ReportingContext {
    @NonNull
    private final List<ContentType> contentTypes;

    public ReportingContext(@NonNull List<ContentType> contentTypes) {
        this.contentTypes = contentTypes;
    }

    @NonNull
    public static ReportingContext fromJson(@NonNull JsonList json) {
        List<ContentType> contentTypes = ContentType.fromList(json);
        return new ReportingContext(contentTypes);
    }

    @NonNull
    public List<ContentType> getContentTypes() {
        return contentTypes;
    }
}
