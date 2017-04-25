package com.urbanairship.automation;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Schedule information stored in the schedules table.
 */
class ScheduleEntry {

    static final String TABLE_NAME = "action_schedules";
    // Schedule
    static final String COLUMN_NAME_SCHEDULE_ID = "s_id";

    // Schedule Info
    static final String COLUMN_NAME_ACTIONS = "s_actions";
    static final String COLUMN_NAME_LIMIT = "s_limit";
    static final String COLUMN_NAME_GROUP = "s_group";
    static final String COLUMN_NAME_START = "s_start";
    static final String COLUMN_NAME_END = "s_end";

    // Delay
    static final String COLUMN_NAME_SECONDS = "d_seconds";
    static final String COLUMN_NAME_SCREEN = "d_screen";
    static final String COLUMN_NAME_APP_STATE = "d_app_state";
    static final String COLUMN_NAME_REGION_ID = "d_region_id";

    // State
    static final String COLUMN_NAME_IS_PENDING_EXECUTION = "s_is_pending_execution";
    static final String COLUMN_NAME_PENDING_EXECUTION_DATE = "s_pending_execution_date";
    static final String COLUMN_NAME_COUNT = "s_count";
    static final String COLUMN_NAME_ID = "s_row_id";


    final String scheduleId;
    final String actionsPayload;
    final int limit;
    final String group;
    final long start;
    final long end;
    final long seconds;
    final String screen;
    final int appState;
    final String regionId;
    final List<TriggerEntry> triggers = new ArrayList<>();

    // State
    private long id = -1;

    private int count;
    private boolean isPendingExecution;
    private long pendingExecutionDate;
    private boolean isDirty;

    ScheduleEntry(ActionSchedule schedule) {
        this.scheduleId = schedule.getId();
        this.actionsPayload = JsonValue.wrapOpt(schedule.getInfo().getActions()).toString();

        this.limit = schedule.getInfo().getLimit();
        this.group = schedule.getInfo().getGroup();
        this.start = schedule.getInfo().getStart();
        this.end = schedule.getInfo().getEnd();

        if (schedule.getInfo().getDelay() != null) {
            this.screen = schedule.getInfo().getDelay().getScreen();
            this.regionId = schedule.getInfo().getDelay().getRegionId();
            this.appState = schedule.getInfo().getDelay().getAppState();
            this.seconds = schedule.getInfo().getDelay().getSeconds();

            for (Trigger trigger : schedule.getInfo().getDelay().getCancellationTriggers()) {
                TriggerEntry triggerEntry = new TriggerEntry(trigger, schedule.getId(), true);
                this.triggers.add(triggerEntry);
            }
        } else {
            this.seconds = 0;
            this.regionId = null;
            this.screen = null;
            this.appState = ScheduleDelay.APP_STATE_ANY;
        }

        for (Trigger trigger : schedule.getInfo().getTriggers()) {
            TriggerEntry triggerEntry = new TriggerEntry(trigger, schedule.getId(), false);
            this.triggers.add(triggerEntry);
        }
    }

    ScheduleEntry(Cursor cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_ID));
        this.scheduleId = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SCHEDULE_ID));
        this.count = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_COUNT));
        this.limit = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_LIMIT));
        this.group = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_GROUP));
        this.actionsPayload = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_ACTIONS));
        this.end = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_END));
        this.start = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_START));
        this.isPendingExecution = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_IS_PENDING_EXECUTION)) == 1;
        this.pendingExecutionDate = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_PENDING_EXECUTION_DATE));
        this.appState = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_APP_STATE));
        this.regionId = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_REGION_ID));
        this.screen = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SCREEN));
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
     * Sets the pending execution status.
     *
     * @param isPendingExecution Pending execution flag.
     */
    void setIsPendingExecution(boolean isPendingExecution) {
        if (this.isPendingExecution != isPendingExecution) {
            this.isPendingExecution = isPendingExecution;
            this.isDirty = true;
        }
    }

    /**
     * Returns a flag indicating if the schedule is pending execution.
     *
     * @return {@code true} if the schedule is pending execution, otherwise {@code false}.
     */
    boolean isPendingExecution() {
        return isPendingExecution;
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
     * Creates an {@link ActionSchedule} from the entry.
     *
     * @return An {@link ActionSchedule}.
     */
    ActionSchedule toSchedule() {
        ActionScheduleInfo.Builder infoBuilder = ActionScheduleInfo.newBuilder()
                                                                   .setEnd(end)
                                                                   .setStart(start)
                                                                   .setGroup(group)
                                                                   .setLimit(limit);


        ScheduleDelay.Builder delayBuilder = ScheduleDelay.newBuilder()
                                                          .setAppState(appState)
                                                          .setRegionId(regionId)
                                                          .setScreen(screen)
                                                          .setSeconds(seconds);

        try {
            JsonMap actionsMap = JsonValue.parseString(actionsPayload).optMap();
            infoBuilder.addAllActions(actionsMap);
        } catch (JsonException e) {
            Logger.error("Unable to deserialize actions map. ", e);
        }

        for (TriggerEntry triggerEntry : triggers) {
            if (triggerEntry.isCancellation) {
                delayBuilder.addCancellationTrigger(triggerEntry.toTrigger());
            } else {
                infoBuilder.addTrigger(triggerEntry.toTrigger());
            }
        }

        infoBuilder.setDelay(delayBuilder.build());

        return new ActionSchedule(scheduleId, infoBuilder.build());
    }

    /**
     * Saves the entry to the database.
     *
     * @param database Saves the entry to the database.
     * @return {code} true if the entry was saved, otherwise {@code false}.
     */
    @WorkerThread
    public boolean save(SQLiteDatabase database) {
        if (id == -1) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_NAME_SCHEDULE_ID, scheduleId);
            contentValues.put(COLUMN_NAME_ACTIONS, actionsPayload);
            contentValues.put(COLUMN_NAME_LIMIT, limit);
            contentValues.put(COLUMN_NAME_GROUP, group);
            contentValues.put(COLUMN_NAME_COUNT, count);
            contentValues.put(COLUMN_NAME_START, start);
            contentValues.put(COLUMN_NAME_END, end);
            contentValues.put(COLUMN_NAME_IS_PENDING_EXECUTION, isPendingExecution ? 1 : 0);
            contentValues.put(COLUMN_NAME_PENDING_EXECUTION_DATE, pendingExecutionDate);
            contentValues.put(COLUMN_NAME_APP_STATE, appState);
            contentValues.put(COLUMN_NAME_REGION_ID, regionId);
            contentValues.put(COLUMN_NAME_SCREEN, screen);
            contentValues.put(COLUMN_NAME_SECONDS, seconds);
            id = database.insert(TABLE_NAME, null, contentValues);
            if (id == -1) {
                return false;
            }
        } else if (isDirty) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_NAME_COUNT, count);
            contentValues.put(COLUMN_NAME_IS_PENDING_EXECUTION, isPendingExecution ? 1 : 0);
            contentValues.put(COLUMN_NAME_PENDING_EXECUTION_DATE, pendingExecutionDate);
            if (database.updateWithOnConflict(TABLE_NAME, contentValues, COLUMN_NAME_ID + " = ?", new String[] { String.valueOf(id) }, SQLiteDatabase.CONFLICT_REPLACE) == 0) {
                return false;
            }
        }

        for (TriggerEntry triggerEntry : triggers) {
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
     * @return The {@link ActionSchedule} instance.
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
                scheduleEntry.triggers.add(triggerEntry);
            }

            cursor.moveToNext();
        }


        return scheduleEntry;
    }

}
