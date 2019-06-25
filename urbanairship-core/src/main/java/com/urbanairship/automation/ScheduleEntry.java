package com.urbanairship.automation;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Schedule information stored in the schedules table.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ScheduleEntry implements ScheduleInfo {

    @IntDef({ STATE_IDLE, STATE_WAITING_SCHEDULE_CONDITIONS, STATE_EXECUTING, STATE_PAUSED,
            STATE_FINISHED, STATE_PREPARING_SCHEDULE, STATE_TIME_DELAYED })
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {}

    // The state values do not define the order.

    // Schedule is active
    static final int STATE_IDLE = 0;

    // Schedule is waiting for its time delay to expire
    static final int STATE_TIME_DELAYED = 5;

    // Schedule is being prepared by the adapter
    static final int STATE_PREPARING_SCHEDULE = 6;

    // Schedule is waiting for app state conditions to be met
    static final int STATE_WAITING_SCHEDULE_CONDITIONS = 1;

    // Schedule is executing
    static final int STATE_EXECUTING = 2;

    // Schedule finished executing and is now waiting for its execution interval to expire
    static final int STATE_PAUSED = 3;

    // Schedule is either expired or at its execution limit
    static final int STATE_FINISHED = 4;

    static final String TABLE_NAME = "action_schedules";
    // Schedule
    static final String COLUMN_NAME_SCHEDULE_ID = "s_id";
    static final String COLUMN_NAME_METADATA = "s_metadata";

    // Schedule Info
    static final String COLUMN_NAME_DATA = "s_data";
    static final String COLUMN_NAME_LIMIT = "s_limit";
    static final String COLUMN_NAME_PRIORITY = "s_priority";
    static final String COLUMN_NAME_GROUP = "s_group";
    static final String COLUMN_NAME_START = "s_start";
    static final String COLUMN_NAME_END = "s_end";
    static final String COLUMN_EDIT_GRACE_PERIOD = "s_edit_grace_period";
    static final String COLUMN_NAME_INTERVAL = "s_interval";

    // Delay
    static final String COLUMN_NAME_SECONDS = "d_seconds";
    static final String COLUMN_NAME_SCREEN = "d_screen";
    static final String COLUMN_NAME_APP_STATE = "d_app_state";
    static final String COLUMN_NAME_REGION_ID = "d_region_id";

    // State
    static final String COLUMN_NAME_EXECUTION_STATE = "s_execution_state";
    static final String COLUMN_NAME_EXECUTION_STATE_CHANGE_DATE = "s_execution_state_change_date";
    static final String COLUMN_NAME_DELAY_FINISH_DATE = "s_pending_execution_date";
    static final String COLUMN_NAME_COUNT = "s_count";
    static final String COLUMN_NAME_ID = "s_row_id";

    public final String scheduleId;
    public final String group;
    public final long seconds;
    public final List<String> screens;
    public final int appState;
    public final List<TriggerEntry> triggerEntries = new ArrayList<>();
    public final String regionId;

    public JsonMap metadata;
    private JsonSerializable data;
    private int limit;
    private int priority;
    private long start;
    private long end;
    private long editGracePeriod;
    private long interval;

    // State
    private long id = -1;

    private int count;
    private int executionState = STATE_IDLE;
    private long delayFinishDate;
    private long executionStateChangeDate;
    private boolean isDirty;
    private boolean isEdit;

    ScheduleEntry(@NonNull String scheduleId, @NonNull ScheduleInfo scheduleInfo, @NonNull JsonMap metadata) {
        this.scheduleId = scheduleId;
        this.metadata = metadata;
        this.data = scheduleInfo.getData();
        this.limit = scheduleInfo.getLimit();
        this.priority = scheduleInfo.getPriority();
        this.group = scheduleInfo.getGroup();
        this.start = scheduleInfo.getStart();
        this.end = scheduleInfo.getEnd();
        this.editGracePeriod = scheduleInfo.getEditGracePeriod();
        this.interval = scheduleInfo.getInterval();

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

    private ScheduleEntry(Cursor cursor) throws JsonException {
        this.id = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_ID));
        this.metadata = JsonValue.parseString(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_METADATA))).optMap();
        this.scheduleId = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SCHEDULE_ID));
        this.count = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_COUNT));
        this.limit = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_LIMIT));
        this.priority = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_PRIORITY));
        this.group = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_GROUP));
        this.editGracePeriod = cursor.getLong(cursor.getColumnIndex(COLUMN_EDIT_GRACE_PERIOD));
        this.data = JsonValue.parseString(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA)));
        this.end = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_END));
        this.start = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_START));
        this.executionState = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_EXECUTION_STATE));
        this.executionStateChangeDate = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_EXECUTION_STATE_CHANGE_DATE));
        this.delayFinishDate = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_DELAY_FINISH_DATE));
        this.appState = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_APP_STATE));
        this.regionId = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_REGION_ID));
        this.interval = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_INTERVAL));
        this.seconds = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_SECONDS));
        this.screens = parseScreens(JsonValue.parseString(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SCREEN))));
    }

    private List<String> parseScreens(JsonValue json) {
        List<String> screens = new ArrayList<>();
        if (json.isJsonList()) {
            for (JsonValue value : json.optList()) {
                if (value.getString() != null) {
                    screens.add(value.getString());
                }
            }
        } else {
            // Migrate old screen name data
            String oldScreenName = json.getString();
            if (oldScreenName != null) {
                screens.add(oldScreenName);
            }
        }

        return screens;
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
            this.executionStateChangeDate = System.currentTimeMillis();
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
    void setDelayFinishDate(long date) {
        if (this.delayFinishDate != date) {
            this.delayFinishDate = date;
            this.isDirty = true;
        }
    }

    /**
     * Get the date of the last state change.
     *
     * @return State change date.
     */
    long getExecutionStateChangeDate() {
        return this.executionStateChangeDate;
    }

    /**
     * Edits the schedule entry.
     *
     * @param edits The schedule edits.
     */
    void applyEdits(@NonNull ScheduleEdits edits) {
        this.start = edits.getStart() == null ? this.start : edits.getStart();
        this.end = edits.getEnd() == null ? this.end : edits.getEnd();
        this.limit = edits.getLimit() == null ? this.limit : edits.getLimit();
        this.data = edits.getData() == null ? this.data : edits.getData();
        this.priority = edits.getPriority() == null ? this.priority : edits.getPriority();
        this.interval = edits.getInterval() == null ? this.interval : edits.getInterval();
        this.editGracePeriod = edits.getEditGracePeriod() == null ? this.editGracePeriod : edits.getEditGracePeriod();
        this.metadata = edits.getMetadata() == null ? this.metadata : edits.getMetadata();

        isDirty = true;
        isEdit = true;
    }

    /**
     * Get the pending execution date in milliseconds.
     *
     * @return Pending execution date.
     */
    long getDelayFinishDate() {
        return this.delayFinishDate;
    }

    /**
     * Checks if a schedule is expired.
     *
     * @return {@code true} if expired, otherwise {@code false}.
     */
    boolean isExpired() {
        return getEnd() >= 0 && getEnd() < System.currentTimeMillis();
    }

    /**
     * Checks whether the schedule has exceeded its limit.
     *
     * @return {@code true} if expired, otherwise {@code false}.
     */
    boolean isOverLimit() {
        return getLimit() > 0 && getCount() >= getLimit();
    }

    /**
     * Saves the entry to the database.
     *
     * @param database Saves the entry to the database.
     * @return {code} true if the entry was saved, otherwise {@code false}.
     */
    @WorkerThread
    boolean save(@NonNull SQLiteDatabase database) {
        if (id == -1) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_NAME_SCHEDULE_ID, scheduleId);
            contentValues.put(COLUMN_NAME_METADATA, metadata.toString());
            contentValues.put(COLUMN_NAME_DATA, data.toJsonValue().toString());
            contentValues.put(COLUMN_NAME_LIMIT, limit);
            contentValues.put(COLUMN_NAME_PRIORITY, priority);
            contentValues.put(COLUMN_NAME_GROUP, group);
            contentValues.put(COLUMN_NAME_COUNT, count);
            contentValues.put(COLUMN_NAME_START, start);
            contentValues.put(COLUMN_NAME_END, end);
            contentValues.put(COLUMN_NAME_EXECUTION_STATE, executionState);
            contentValues.put(COLUMN_NAME_EXECUTION_STATE_CHANGE_DATE, executionStateChangeDate);
            contentValues.put(COLUMN_NAME_DELAY_FINISH_DATE, delayFinishDate);
            contentValues.put(COLUMN_NAME_APP_STATE, appState);
            contentValues.put(COLUMN_NAME_REGION_ID, regionId);
            contentValues.put(COLUMN_NAME_SCREEN, JsonValue.wrapOpt(screens).optList().toString());
            contentValues.put(COLUMN_NAME_SECONDS, seconds);
            contentValues.put(COLUMN_EDIT_GRACE_PERIOD, editGracePeriod);
            contentValues.put(COLUMN_NAME_INTERVAL, interval);
            try {
                id = database.insert(TABLE_NAME, null, contentValues);
                if (id == -1) {
                    return false;
                }
            } catch (SQLException e) {
                Logger.error(e, "ScheduleEntry - Unable to save.");
                return false;
            }
        } else if (isDirty) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_NAME_COUNT, count);
            contentValues.put(COLUMN_NAME_EXECUTION_STATE, executionState);
            contentValues.put(COLUMN_NAME_EXECUTION_STATE_CHANGE_DATE, executionStateChangeDate);
            contentValues.put(COLUMN_NAME_DELAY_FINISH_DATE, delayFinishDate);

            if (isEdit) {
                contentValues.put(COLUMN_NAME_DATA, data.toJsonValue().toString());
                contentValues.put(COLUMN_NAME_METADATA, metadata.toString());
                contentValues.put(COLUMN_NAME_LIMIT, limit);
                contentValues.put(COLUMN_NAME_PRIORITY, priority);
                contentValues.put(COLUMN_NAME_START, start);
                contentValues.put(COLUMN_NAME_END, end);
                contentValues.put(COLUMN_EDIT_GRACE_PERIOD, editGracePeriod);
                contentValues.put(COLUMN_NAME_INTERVAL, interval);
            }
            try {
                if (database.updateWithOnConflict(TABLE_NAME, contentValues, COLUMN_NAME_ID + " = ?", new String[] { String.valueOf(id) }, SQLiteDatabase.CONFLICT_REPLACE) == 0) {
                    return false;
                }
            } catch (SQLException e) {
                Logger.error(e, "ScheduleEntry - Unable to save.");
                return false;
            }
        }

        for (TriggerEntry triggerEntry : triggerEntries) {
            if (!triggerEntry.save(database)) {
                return false;
            }
        }

        isDirty = false;
        isEdit = false;
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
                try {
                    scheduleEntry = new ScheduleEntry(cursor);
                } catch (JsonException e) {
                    Logger.error(e, "Failed to parse schedule entry.");
                    return null;
                }
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

    @NonNull
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

    @NonNull
    @Override
    public JsonSerializable getData() {
        return this.data;
    }

    @Override
    public int getLimit() {
        return this.limit;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

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
    public long getEditGracePeriod() {
        return this.editGracePeriod;
    }

    @Override
    public long getInterval() {
        return this.interval;
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

    @NonNull
    @Override
    public String toString() {
        return scheduleId;
    }

}
