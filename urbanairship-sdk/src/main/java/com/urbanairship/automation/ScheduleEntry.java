package com.urbanairship.automation;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;


/**
 * Schedule information stored in the schedules table.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ScheduleEntry implements ScheduleInfo {

    @IntDef({ STATE_IDLE, STATE_PENDING_EXECUTION, STATE_EXECUTING })
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {}

    static final int STATE_IDLE = 0;
    static final int STATE_PENDING_EXECUTION = 1;
    static final int STATE_EXECUTING = 2;

    static final String TABLE_NAME = "action_schedules";
    // Schedule
    static final String COLUMN_NAME_SCHEDULE_ID = "s_id";

    // Schedule Info
    static final String COLUMN_NAME_DATA = "s_data";
    static final String COLUMN_NAME_LIMIT = "s_limit";
    static final String COLUMN_NAME_PRIORITY = "s_priority";
    static final String COLUMN_NAME_GROUP = "s_group";
    static final String COLUMN_NAME_START = "s_start";
    static final String COLUMN_NAME_END = "s_end";

    // Delay
    static final String COLUMN_NAME_SECONDS = "d_seconds";
    static final String COLUMN_NAME_SCREEN = "d_screen";
    static final String COLUMN_NAME_APP_STATE = "d_app_state";
    static final String COLUMN_NAME_REGION_ID = "d_region_id";

    // State
    static final String COLUMN_NAME_EXECUTION_STATE = "s_execution_state";
    static final String COLUMN_NAME_PENDING_EXECUTION_DATE = "s_pending_execution_date";
    static final String COLUMN_NAME_COUNT = "s_count";
    static final String COLUMN_NAME_ID = "s_row_id";


    public final String scheduleId;
    public final JsonSerializable data;
    public final int limit;
    public final int priority;
    public final String group;
    public final long start;
    public final long end;
    public final long seconds;
    public final List<String> screens;
    public final int appState;
    public final String regionId;
    public final List<TriggerEntry> triggerEntries = new ArrayList<>();

    // State
    private long id = -1;

    private int count;
    private int executionState = STATE_IDLE;
    private long pendingExecutionDate;
    private boolean isDirty;

    ScheduleEntry(@NonNull String scheduleId, @NonNull ScheduleInfo scheduleInfo) {
        this.scheduleId = scheduleId;
        this.data = scheduleInfo.getData();
        this.limit = scheduleInfo.getLimit();
        this.priority = scheduleInfo.getPriority();
        this.group = scheduleInfo.getGroup();
        this.start = scheduleInfo.getStart();
        this.end = scheduleInfo.getEnd();

        if (scheduleInfo.getDelay() != null) {
            this.screens = scheduleInfo.getDelay().getScreens();
            this.regionId = scheduleInfo.getDelay().getRegionId();
            this.appState = scheduleInfo.getDelay().getAppState();
            this.seconds = scheduleInfo.getDelay().getSeconds();

            for (Trigger trigger : scheduleInfo.getDelay().getCancellationTriggers()) {
                TriggerEntry triggerEntry = new TriggerEntry(trigger, scheduleId, true);
                this.triggerEntries.add(triggerEntry);
            }
        } else {
            this.seconds = 0;
            this.regionId = null;
            this.screens = null;
            this.appState = ScheduleDelay.APP_STATE_ANY;
        }

        for (Trigger trigger : scheduleInfo.getTriggers()) {
            TriggerEntry triggerEntry = new TriggerEntry(trigger, scheduleId, false);
            this.triggerEntries.add(triggerEntry);
        }
    }

    private ScheduleEntry(Cursor cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_ID));
        this.scheduleId = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SCHEDULE_ID));
        this.count = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_COUNT));
        this.limit = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_LIMIT));
        this.priority = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_PRIORITY));
        this.group = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_GROUP));

        JsonValue parsedData;
        try {
            parsedData = JsonValue.parseString(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA)));
        } catch (JsonException e) {
            parsedData = JsonValue.NULL;
        }

        this.data = parsedData;
        this.end = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_END));
        this.start = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_START));
        this.executionState = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_EXECUTION_STATE));
        this.pendingExecutionDate = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_PENDING_EXECUTION_DATE));
        this.appState = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_APP_STATE));
        this.regionId = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_REGION_ID));

        JsonValue parsedScreens;

        try {
            parsedScreens = JsonValue.parseString(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SCREEN)));
        } catch (JsonException e) {
            parsedScreens = JsonValue.NULL;
        }

        this.screens = new ArrayList<>();

        if (parsedScreens.isJsonList()) {
            for (JsonValue value : parsedScreens.optList()) {
                if (value.getString() != null) {
                    this.screens.add(value.getString());
                }
            }
        } else {
            // Migrate old screen name data
            String oldScreenName = parsedScreens.getString();
            if (oldScreenName != null) {
                this.screens.add(oldScreenName);
            }
        }

        this.seconds = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_SECONDS));
    }

    /**
     * Sets the current execution count.
     *
     * @param count The execution count.
     */
    void setCount(int count) {
        if (this.count != count) {
            this.count = count;
            this.isDirty = true;
        }
    }

    /**
     * Returns the current execution count.
     *
     * @return The current execution count.
     */
    int getCount() {
        return count;
    }

    /**
     * Sets the execution state.
     *
     * @param executionState Pending execution flag.
     */
    void setExecutionState(int executionState) {
        if (this.executionState != executionState) {
            this.executionState = executionState;
            this.isDirty = true;
        }
    }

    /**
     * Gets the execution state.
     *
     * @return The execution state.
     */
    @State
    int getExecutionState() {
        return executionState;
    }

    /**
     * Sets the pending execution date.
     *
     * @param date The pending execution date in milliseconds.
     */
    void setPendingExecutionDate(long date) {
        if (this.pendingExecutionDate != date) {
            this.pendingExecutionDate = date;
            this.isDirty = true;
        }
    }

    /**
     * Get the pending execution date in milliseconds.
     *
     * @return Pending execution date.
     */
    long getPendingExecutionDate() {
        return this.pendingExecutionDate;
    }

    /**
     * Saves the entry to the database.
     *
     * @param database Saves the entry to the database.
     * @return {code} true if the entry was saved, otherwise {@code false}.
     */
    @WorkerThread
    boolean save(SQLiteDatabase database) {
        if (id == -1) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_NAME_SCHEDULE_ID, scheduleId);
            contentValues.put(COLUMN_NAME_DATA, data.toJsonValue().toString());
            contentValues.put(COLUMN_NAME_LIMIT, limit);
            contentValues.put(COLUMN_NAME_PRIORITY, priority);
            contentValues.put(COLUMN_NAME_GROUP, group);
            contentValues.put(COLUMN_NAME_COUNT, count);
            contentValues.put(COLUMN_NAME_START, start);
            contentValues.put(COLUMN_NAME_END, end);
            contentValues.put(COLUMN_NAME_EXECUTION_STATE, executionState);
            contentValues.put(COLUMN_NAME_PENDING_EXECUTION_DATE, pendingExecutionDate);
            contentValues.put(COLUMN_NAME_APP_STATE, appState);
            contentValues.put(COLUMN_NAME_REGION_ID, regionId);
            contentValues.put(COLUMN_NAME_SCREEN, JsonValue.wrapOpt(screens).optList().toString());
            contentValues.put(COLUMN_NAME_SECONDS, seconds);
            id = database.insert(TABLE_NAME, null, contentValues);
            if (id == -1) {
                return false;
            }
        } else if (isDirty) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_NAME_COUNT, count);
            contentValues.put(COLUMN_NAME_EXECUTION_STATE, executionState);
            contentValues.put(COLUMN_NAME_PENDING_EXECUTION_DATE, pendingExecutionDate);
            if (database.updateWithOnConflict(TABLE_NAME, contentValues, COLUMN_NAME_ID + " = ?", new String[] { String.valueOf(id) }, SQLiteDatabase.CONFLICT_REPLACE) == 0) {
                return false;
            }
        }

        for (TriggerEntry triggerEntry : triggerEntries) {
            if (!triggerEntry.save(database)) {
                return false;
            }
        }

        isDirty = false;
        return true;
    }

    /**
     * Generates a schedule entry from a cursor.
     *
     * @param cursor The {@link Cursor} instance.
     * @return The {@link ScheduleEntry} instance.
     */
    @Nullable
    static ScheduleEntry fromCursor(Cursor cursor) {

        // The cursor rows will be that of the schedules table joined with the triggers table - each
        // row will contain a single trigger paired with its correlated schedule. We only want to build
        // the schedule info once for every grouping of triggers. As we step through the sorted cursor
        // rows, this will track the last found schedule ID. When that ID has changed in a given row,
        // the current action schedule info builder can be completed and a new one started for the
        // next set of triggers.

        ScheduleEntry scheduleEntry = null;

        while (!cursor.isAfterLast()) {

            if (scheduleEntry == null) {
                scheduleEntry = new ScheduleEntry(cursor);
            }

            if (scheduleEntry.scheduleId == null || !scheduleEntry.scheduleId.equals(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SCHEDULE_ID)))) {
                cursor.moveToPrevious();
                break;
            }

            // If the row contains triggers, parse and add them to the builder.
            if (cursor.getColumnIndex(TriggerEntry.COLUMN_NAME_TYPE) != -1) {
                TriggerEntry triggerEntry = new TriggerEntry(cursor);
                scheduleEntry.triggerEntries.add(triggerEntry);
            }

            cursor.moveToNext();
        }


        return scheduleEntry;
    }

    @Override
    public List<Trigger> getTriggers() {
        List<Trigger> triggers = new ArrayList<>();

        for (TriggerEntry triggerEntry : this.triggerEntries) {
            if (!triggerEntry.isCancellation) {
                triggers.add(triggerEntry.toTrigger());
            }
        }

        return triggers;
    }

    @Override
    public JsonSerializable getData() {
        return this.data;
    }

    @Override
    public int getLimit() {
        return this.limit;
    }

    @Override
    public int getPriority() { return this.priority; }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public long getStart() {
        return this.start;
    }

    @Override
    public long getEnd() {
        return this.end;
    }

    @Override
    public ScheduleDelay getDelay() {
        ScheduleDelay.Builder delayBuilder = ScheduleDelay.newBuilder()
                                                          .setAppState(appState)
                                                          .setRegionId(regionId)
                                                          .setScreens(screens)
                                                          .setSeconds(seconds);


        for (TriggerEntry triggerEntry : this.triggerEntries) {
            if (triggerEntry.isCancellation) {
                delayBuilder.addCancellationTrigger(triggerEntry.toTrigger());
            }
        }

        return delayBuilder.build();
    }
}
