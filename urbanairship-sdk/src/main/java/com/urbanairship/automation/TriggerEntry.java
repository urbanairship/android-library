/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;


/**
 * Trigger information stored in the triggers table.
 */
class TriggerEntry {

    static final String TABLE_NAME = "triggers";

    static final String COLUMN_NAME_TYPE = "t_type";
    static final String COLUMN_NAME_SCHEDULE_ID = "t_s_id";
    static final String COLUMN_NAME_PREDICATE = "t_predicate";
    static final String COLUMN_NAME_PROGRESS = "t_progress";
    static final String COLUMN_NAME_GOAL = "t_goal";
    static final String COLUMN_NAME_IS_CANCELLATION = "t_cancellation";
    static final String COLUMN_NAME_ID = "t_row_id";

    final String scheduleId;
    final int type;
    final double goal;
    final JsonPredicate jsonPredicate;
    final boolean isCancellation;

    private long id = -1;
    private double progress;
    private boolean isDirty = false;

    TriggerEntry(Trigger trigger, String scheduleId, boolean isCancellation) {
        this.scheduleId = scheduleId;
        this.type = trigger.getType();
        this.goal = trigger.getGoal();
        this.jsonPredicate = trigger.getPredicate();
        this.isCancellation = isCancellation;
    }

    TriggerEntry(Cursor cursor) {
        this.type = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_TYPE));
        this.goal = cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_GOAL));
        this.progress = cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_PROGRESS));
        this.jsonPredicate = parseJsonPredicate(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_PREDICATE)));
        this.id = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_ID));
        this.scheduleId = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SCHEDULE_ID));
        this.isCancellation = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_IS_CANCELLATION)) == 1;
    }

    @WorkerThread
    boolean save(SQLiteDatabase database) {
        if (id == -1) {
            ContentValues value = new ContentValues();
            value.put(COLUMN_NAME_TYPE, type);
            value.put(COLUMN_NAME_SCHEDULE_ID, scheduleId);
            value.put(COLUMN_NAME_PREDICATE, jsonPredicate == null ? null : JsonValue.wrap(jsonPredicate).toString());
            value.put(COLUMN_NAME_GOAL, goal);
            value.put(COLUMN_NAME_PROGRESS, progress);
            value.put(COLUMN_NAME_IS_CANCELLATION, isCancellation ? 1 : 0);
            id = database.insert(TABLE_NAME, null, value);
            if (id != -1) {
                isDirty = false;
                return true;
            }
        } else if (isDirty) {
            ContentValues value = new ContentValues();
            value.put(COLUMN_NAME_PROGRESS, progress);

            if (database.updateWithOnConflict(TABLE_NAME, value, COLUMN_NAME_ID + " = ?", new String[] { String.valueOf(id) }, SQLiteDatabase.CONFLICT_REPLACE) != 0) {
                isDirty = false;
                return true;
            } else {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the trigger's progress.
     *
     * @return The trigger's progress.
     */
    double getProgress() {
        return this.progress;
    }

    /**
     * Sets the trigger's progress.
     *
     * @param progress The triggers progress.
     */
    void setProgress(double progress) {
        if (progress != this.progress) {
            this.progress = progress;
            this.isDirty = true;
        }
    }

    /**
     * Converts the entry into a {@link Trigger}.
     *
     * @return A {@link Trigger}.
     */
    Trigger toTrigger() {
        return new Trigger(type, goal, jsonPredicate);
    }

    /**
     * Parses the JSON predicate.
     *
     * @param payload JSON payload.
     * @return The parsed JsonPredicate or null.
     */
    @Nullable
    JsonPredicate parseJsonPredicate(String payload) {
        try {
            JsonValue jsonValue = JsonValue.parseString(payload);
            if (!jsonValue.isNull()) {
                return JsonPredicate.parse(jsonValue);
            }
        } catch (JsonException e) {
            Logger.error("Failed to parse JSON predicate.", e);
            return null;
        }

        return null;

    }
}
