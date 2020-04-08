/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;

import com.urbanairship.automation.Schedule;
import com.urbanairship.json.JsonMap;

/**
 * Defines an in-app message schedule.
 */
public class InAppMessageSchedule implements Schedule<InAppMessageScheduleInfo> {

    private final String id;
    private final InAppMessageScheduleInfo info;
    private final JsonMap metadata;

    /**
     * Class constructor.
     *
     * @param id The schedule ID.
     * @param metadata The metadata.
     * @param info The ActionScheduleInfo instance.
     */
    public InAppMessageSchedule(@NonNull String id, @NonNull JsonMap metadata, @NonNull InAppMessageScheduleInfo info) {
        this.id = id;
        this.info = info;
        this.metadata = metadata;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public InAppMessageScheduleInfo getInfo() {
        return info;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public JsonMap getMetadata() {
        return metadata;
    }

}
