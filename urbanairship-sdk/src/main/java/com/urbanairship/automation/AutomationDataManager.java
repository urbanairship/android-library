/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DataManager;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link DataManager} class for automation schedules.
 */
class AutomationDataManager extends DataManager {

    /**
     * Action schedules table contract
     */
    private class ActionSchedulesTable implements BaseColumns {

        public static final String TABLE_NAME = "action_schedules";

        static final String COLUMN_NAME_SCHEDULE_ID = "s_id";
        static final String COLUMN_NAME_ACTIONS = "s_actions";
        static final String COLUMN_NAME_COUNT = "s_count";
        static final String COLUMN_NAME_LIMIT = "s_limit";
        static final String COLUMN_NAME_GROUP = "s_group";
        static final String COLUMN_NAME_START = "s_start";
        static final String COLUMN_NAME_END = "s_end";
        static final String COLUMN_NAME_IS_PENDING_EXECUTION = "s_is_pending_execution";
        static final String COLUMN_NAME_PENDING_EXECUTION_DATE = "s_pending_execution_date";
    }

    /**
     * Triggers table contract
     */
    private class TriggersTable implements BaseColumns {

        public static final String TABLE_NAME = "triggers";

        static final String COLUMN_NAME_TYPE = "t_type";
        static final String COLUMN_NAME_SCHEDULE_ID = "t_s_id";
        static final String COLUMN_NAME_PREDICATE = "t_predicate";
        static final String COLUMN_NAME_PROGRESS = "t_progress";
        static final String COLUMN_NAME_GOAL = "t_goal";
        static final String COLUMN_NAME_START = "t_start";
        static final String COLUMN_NAME_DELAY_ID = "t_d_id";
    }

    /**
     * ScheduleDelay table contract
     */
    private class ScheduleDelayTable implements BaseColumns {

        public static final String TABLE_NAME = "schedule_delays";

        static final String COLUMN_NAME_DELAY_ID = "d_id";
        static final String COLUMN_NAME_SCHEDULE_ID = "d_s_id";
        static final String COLUMN_NAME_SECONDS = "d_seconds";
        static final String COLUMN_NAME_SCREEN = "d_screen";
        static final String COLUMN_NAME_APP_STATE = "d_app_state";
        static final String COLUMN_NAME_REGION_ID = "d_region_id";
    }

    /**
     * Maximum SQL argument count.
     */
    private static final int MAX_ARG_COUNT = 999;

    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "ua_automation.db";

    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 2;

    /**
     * Appended to the end of schedules GET queries to group rows by schedule ID.
     */
    private static final String ORDER_SCHEDULES_STATEMENT = " ORDER BY " + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + " ASC";

    /**
     * Query for retrieving schedules with associated delays.
     */
    static final String GET_SCHEDULES_QUERY = "SELECT * FROM " + ActionSchedulesTable.TABLE_NAME + " a LEFT OUTER JOIN " + TriggersTable.TABLE_NAME + " b ON a." + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + "=b." + TriggersTable.COLUMN_NAME_SCHEDULE_ID
            + " LEFT OUTER JOIN " + ScheduleDelayTable.TABLE_NAME + " c ON b." + TriggersTable.COLUMN_NAME_SCHEDULE_ID + "=c." + ScheduleDelayTable.COLUMN_NAME_SCHEDULE_ID;

    /**
     * Query for retrieving active triggers.
     */
    static final String GET_ACTIVE_TRIGGERS = "SELECT t.* FROM " + TriggersTable.TABLE_NAME + " t LEFT OUTER JOIN " + ActionSchedulesTable.TABLE_NAME + " a ON a." + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + " = " + TriggersTable.COLUMN_NAME_SCHEDULE_ID + " WHERE t." +
            "t_type = %s AND t." + TriggersTable.COLUMN_NAME_START + " < %s AND (t." + "t_d_id IS NULL OR a." + "s_is_pending_execution = 1)";

    /**
     * Partial query for deleting schedules by ID.
     */
    static final String SCHEDULES_TO_DELETE_QUERY = "DELETE FROM " + ActionSchedulesTable.TABLE_NAME + " WHERE " + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID;

    /**
     * Partial query for incrementing schedule counts by ID.
     */
    static final String SCHEDULES_TO_INCREMENT_QUERY = "UPDATE " + ActionSchedulesTable.TABLE_NAME + " SET " + ActionSchedulesTable.COLUMN_NAME_COUNT + " = " + ActionSchedulesTable.COLUMN_NAME_COUNT + " + 1 WHERE " + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID;

    /**
     * Partial query for resetting trigger progress by ID.
     */
    static final String TRIGGERS_TO_RESET_QUERY = "UPDATE " + TriggersTable.TABLE_NAME + " SET " + TriggersTable.COLUMN_NAME_PROGRESS + " = 0 WHERE " + TriggersTable._ID;

    /**
     * Partial query for resetting trigger progress by schedule ID.
     */
    static final String CANCELLATION_TRIGGERS_TO_RESET = "UPDATE " + TriggersTable.TABLE_NAME + " SET " + TriggersTable.COLUMN_NAME_PROGRESS + " = 0 WHERE " + TriggersTable.COLUMN_NAME_DELAY_ID + " IS NOT NULL" + " AND " + TriggersTable.COLUMN_NAME_SCHEDULE_ID;

    /**
     * Query for incrementing schedule based on cancellation or standard behavior.
     */
    static final String TRIGGERS_TO_INCREMENT_QUERY = "UPDATE " + TriggersTable.TABLE_NAME + " SET " + TriggersTable.COLUMN_NAME_PROGRESS + " = " + TriggersTable.COLUMN_NAME_PROGRESS +
            " + %s WHERE " + TriggersTable.COLUMN_NAME_SCHEDULE_ID + " IN (SELECT " + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + " FROM " + ActionSchedulesTable.TABLE_NAME + " WHERE " + ActionSchedulesTable.COLUMN_NAME_IS_PENDING_EXECUTION + " = %s)" +
            " AND " +  TriggersTable._ID;

    /**
     * Partial query for resetting a schedule delay trigger execution state.
     */
    static final String SCHEDULES_EXECUTION_STATE_UPDATE = "UPDATE " + ActionSchedulesTable.TABLE_NAME + " SET " + ActionSchedulesTable.COLUMN_NAME_IS_PENDING_EXECUTION + " = %s WHERE " + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID;

    /**
     * Partial query for resetting a schedule delay trigger execution state.
     */
    static final String SCHEDULES_EXECUTION_DATE_UPDATE = "UPDATE " + ActionSchedulesTable.TABLE_NAME + " SET " + ActionSchedulesTable.COLUMN_NAME_PENDING_EXECUTION_DATE + " = %s WHERE " + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID;


    /**
     * Class constructor.
     *
     * @param context The app context.
     * @param appKey The app key.
     */
    AutomationDataManager(@NonNull Context context, @NonNull String appKey) {
        super(context, appKey, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Logs that the database is being upgraded
        Logger.debug("AutomationDataManager - Upgrading automation database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");

        // Kills the table and existing data
        db.execSQL("DROP TABLE IF EXISTS " + TriggersTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ActionSchedulesTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ScheduleDelayTable.TABLE_NAME);

        // Recreates the database with a new version
        onCreate(db);
    }

    @Override
    protected void onCreate(@NonNull SQLiteDatabase db) {
        Logger.debug("AutomationDataManager - Creating automation database");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + ActionSchedulesTable.TABLE_NAME + " ("
                + ActionSchedulesTable._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + " TEXT UNIQUE,"
                + ActionSchedulesTable.COLUMN_NAME_ACTIONS + " TEXT,"
                + ActionSchedulesTable.COLUMN_NAME_START + " INTEGER,"
                + ActionSchedulesTable.COLUMN_NAME_END + " INTEGER,"
                + ActionSchedulesTable.COLUMN_NAME_COUNT + " INTEGER,"
                + ActionSchedulesTable.COLUMN_NAME_LIMIT + " INTEGER,"
                + ActionSchedulesTable.COLUMN_NAME_GROUP + " TEXT,"
                + ActionSchedulesTable.COLUMN_NAME_IS_PENDING_EXECUTION  + " INTEGER,"
                + ActionSchedulesTable.COLUMN_NAME_PENDING_EXECUTION_DATE + " DOUBLE"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScheduleDelayTable.TABLE_NAME + " ("
                + ScheduleDelayTable._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ScheduleDelayTable.COLUMN_NAME_DELAY_ID + " TEXT UNIQUE,"
                + ScheduleDelayTable.COLUMN_NAME_SCHEDULE_ID + " TEXT,"
                + ScheduleDelayTable.COLUMN_NAME_APP_STATE + " INTEGER,"
                + ScheduleDelayTable.COLUMN_NAME_REGION_ID + " TEXT,"
                + ScheduleDelayTable.COLUMN_NAME_SCREEN + " TEXT,"
                + ScheduleDelayTable.COLUMN_NAME_SECONDS + " DOUBLE,"
                + "FOREIGN KEY(" + ScheduleDelayTable.COLUMN_NAME_SCHEDULE_ID + ") REFERENCES " + ActionSchedulesTable.TABLE_NAME + "(" + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + ") ON DELETE CASCADE"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TriggersTable.TABLE_NAME + " ("
                + TriggersTable._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TriggersTable.COLUMN_NAME_TYPE + " INTEGER,"
                + TriggersTable.COLUMN_NAME_DELAY_ID + " TEXT,"
                + TriggersTable.COLUMN_NAME_SCHEDULE_ID + " TEXT,"
                + TriggersTable.COLUMN_NAME_PREDICATE + " TEXT,"
                + TriggersTable.COLUMN_NAME_PROGRESS + " DOUBLE,"
                + TriggersTable.COLUMN_NAME_GOAL + " DOUBLE,"
                + TriggersTable.COLUMN_NAME_START + " INTEGER,"
                + "FOREIGN KEY(" + TriggersTable.COLUMN_NAME_DELAY_ID + ") REFERENCES " + ScheduleDelayTable.TABLE_NAME + "(" + ScheduleDelayTable.COLUMN_NAME_DELAY_ID + ") ON DELETE CASCADE,"
                + "FOREIGN KEY(" + TriggersTable.COLUMN_NAME_SCHEDULE_ID + ") REFERENCES " + ActionSchedulesTable.TABLE_NAME + "(" + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + ") ON DELETE CASCADE"
                + ");");

        Logger.debug("AutomationDataManager - Automation database created");
    }

    @Override
    protected void bindValuesToSqliteStatement(@NonNull String table, @NonNull SQLiteStatement statement, @NonNull ContentValues values) {
        if (TriggersTable.TABLE_NAME.equals(table)) {
            bind(statement, 1, values.getAsInteger(TriggersTable.COLUMN_NAME_TYPE));
            bind(statement, 2, values.getAsString(TriggersTable.COLUMN_NAME_SCHEDULE_ID));
            bind(statement, 3, values.getAsString(TriggersTable.COLUMN_NAME_PREDICATE));
            bind(statement, 4, values.getAsDouble(TriggersTable.COLUMN_NAME_PROGRESS));
            bind(statement, 5, values.getAsDouble(TriggersTable.COLUMN_NAME_GOAL));
            bind(statement, 6, values.getAsLong(TriggersTable.COLUMN_NAME_START));
            bind(statement, 7, values.getAsString(TriggersTable.COLUMN_NAME_DELAY_ID));
        } else if (ActionSchedulesTable.TABLE_NAME.equals(table)) {
            bind(statement, 1, values.getAsString(ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID));
            bind(statement, 2, values.getAsString(ActionSchedulesTable.COLUMN_NAME_ACTIONS));
            bind(statement, 3, values.getAsLong(ActionSchedulesTable.COLUMN_NAME_START));
            bind(statement, 4, values.getAsLong(ActionSchedulesTable.COLUMN_NAME_END));
            bind(statement, 5, values.getAsInteger(ActionSchedulesTable.COLUMN_NAME_COUNT));
            bind(statement, 6, values.getAsInteger(ActionSchedulesTable.COLUMN_NAME_LIMIT));
            bind(statement, 7, values.getAsString(ActionSchedulesTable.COLUMN_NAME_GROUP));
            bind(statement, 8, values.getAsInteger(ActionSchedulesTable.COLUMN_NAME_IS_PENDING_EXECUTION));
            bind(statement, 9, values.getAsLong(ActionSchedulesTable.COLUMN_NAME_PENDING_EXECUTION_DATE));
        } else if (ScheduleDelayTable.TABLE_NAME.equals(table)) {
            bind(statement, 1, values.getAsString(ScheduleDelayTable.COLUMN_NAME_DELAY_ID));
            bind(statement, 2, values.getAsInteger(ScheduleDelayTable.COLUMN_NAME_APP_STATE));
            bind(statement, 3, values.getAsString(ScheduleDelayTable.COLUMN_NAME_REGION_ID));
            bind(statement, 4, values.getAsString(ScheduleDelayTable.COLUMN_NAME_SCHEDULE_ID));
            bind(statement, 5, values.getAsString(ScheduleDelayTable.COLUMN_NAME_SCREEN));
            bind(statement, 6, values.getAsLong(ScheduleDelayTable.COLUMN_NAME_SECONDS));
        }
    }

    @Override
    protected SQLiteStatement getInsertStatement(@NonNull String table, @NonNull SQLiteDatabase db) {
        if (table.equals(TriggersTable.TABLE_NAME)) {
            String sql = this.buildInsertStatement(table, TriggersTable.COLUMN_NAME_TYPE,
                    TriggersTable.COLUMN_NAME_SCHEDULE_ID, TriggersTable.COLUMN_NAME_PREDICATE,
                    TriggersTable.COLUMN_NAME_PROGRESS, TriggersTable.COLUMN_NAME_GOAL,
                    TriggersTable.COLUMN_NAME_START, TriggersTable.COLUMN_NAME_DELAY_ID);

            return db.compileStatement(sql);
        } else if (table.equals(ActionSchedulesTable.TABLE_NAME)) {
            String sql = this.buildInsertStatement(table, ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID,
                    ActionSchedulesTable.COLUMN_NAME_ACTIONS, ActionSchedulesTable.COLUMN_NAME_START,
                    ActionSchedulesTable.COLUMN_NAME_END, ActionSchedulesTable.COLUMN_NAME_COUNT,
                    ActionSchedulesTable.COLUMN_NAME_LIMIT, ActionSchedulesTable.COLUMN_NAME_GROUP,
                    ActionSchedulesTable.COLUMN_NAME_IS_PENDING_EXECUTION, ActionSchedulesTable.COLUMN_NAME_PENDING_EXECUTION_DATE);

            return db.compileStatement(sql);
        } else if (table.equals(ScheduleDelayTable.TABLE_NAME)) {
            String sql = this.buildInsertStatement(table, ScheduleDelayTable.COLUMN_NAME_DELAY_ID,
                    ScheduleDelayTable.COLUMN_NAME_APP_STATE, ScheduleDelayTable.COLUMN_NAME_REGION_ID,
                    ScheduleDelayTable.COLUMN_NAME_SCHEDULE_ID, ScheduleDelayTable.COLUMN_NAME_SCREEN,
                    ScheduleDelayTable.COLUMN_NAME_SECONDS);

            return db.compileStatement(sql);
        }

        return null;
    }

    @Override
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Logs that the database is being downgraded
        Logger.debug("AutomationDataManager - Downgrading automation database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");

        // Drop the table and recreate it
        db.execSQL("DROP TABLE IF EXISTS " + TriggersTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ActionSchedulesTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ScheduleDelayTable.TABLE_NAME);
        onCreate(db);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    protected void onOpen(SQLiteDatabase db) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN && !db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }
    
    /**
     * Deletes a schedule given an ID.
     *
     * @param scheduleId The schedule ID.
     */
    void deleteSchedule(String scheduleId) {
        if (delete(ActionSchedulesTable.TABLE_NAME, ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + " = ?", new String[] { scheduleId }) < 0) {
            Logger.warn("AutomationDataManager - failed to delete schedule for schedule ID " + scheduleId);
        }
    }

    /**
     * Deletes schedules given a group.
     *
     * @param group The schedule group.
     */
    void deleteSchedules(String group) {
        if (delete(ActionSchedulesTable.TABLE_NAME, ActionSchedulesTable.COLUMN_NAME_GROUP + " = ?", new String[] { group }) < 0) {
            Logger.warn("AutomationDataManager - failed to delete schedules for group " + group);
        }
    }

    /**
     * Deletes all schedules.
     */
    void deleteSchedules() {
        if (delete(ActionSchedulesTable.TABLE_NAME, null, null) < 0) {
            Logger.warn("AutomationDataManager - failed to delete schedules");
        }
    }

    /**
     * Deletes schedules given a list of IDs.
     *
     * @param schedulesToDelete The list of schedule IDs.
     */
    void bulkDeleteSchedules(@NonNull List<String> schedulesToDelete) {
        String query = "DELETE FROM " + ActionSchedulesTable.TABLE_NAME + " WHERE " + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID;
        HashMap<String, List<String>> deleteMap = new HashMap<>();
        deleteMap.put(query, schedulesToDelete);
        updateLists(deleteMap);
    }

    /**
     * Gets a schedule for a given ID.
     *
     * @param id The schedule ID.
     * @return The {@link ActionSchedule} instance.
     */
    ActionSchedule getSchedule(String id) {
        String query = GET_SCHEDULES_QUERY + " WHERE a." + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + "=?";

        Cursor c = rawQuery(query, new String[] { id });
        List<ActionSchedule> list = generateSchedules(c);

        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }

    /**
     * Gets schedules for a given group.
     *
     * @param group The schedule group.
     * @return The list of {@link ActionSchedule} instances.
     */
    List<ActionSchedule> getSchedules(String group) {
        String query = GET_SCHEDULES_QUERY + " WHERE a." + ActionSchedulesTable.COLUMN_NAME_GROUP + "=?" + ORDER_SCHEDULES_STATEMENT;
        Cursor c = rawQuery(query, new String[] { String.valueOf(group) });
        return generateSchedules(c);
    }

    /**
     * Gets all schedules.
     *
     * @return The list of {@link ActionSchedule} instances.
     */
    List<ActionSchedule> getSchedules() {
        String query = GET_SCHEDULES_QUERY + ORDER_SCHEDULES_STATEMENT;
        Cursor c = rawQuery(query, null);
        return generateSchedules(c);
    }

    /**
     * Gets a delayed schedule for a given ID.
     *
     * @return The {@link ActionSchedule} instance.
     */
    List<ActionSchedule> getDelayedSchedules() {
        String query = GET_SCHEDULES_QUERY + " AND c." + ScheduleDelayTable.COLUMN_NAME_DELAY_ID + " IS NOT NULL";
        Cursor c = rawQuery(query, null);
        return generateSchedules(c);
    }

    /**
     * Bulk inserts schedules.
     *
     * @param schedules The list of {@link ActionScheduleInfo} instances.
     * @return A list of inserted {@link ActionSchedule} instances.
     */
    List<ActionSchedule> insertSchedules(List<ActionScheduleInfo> schedules) {
        Map<String, ActionSchedule> added = new HashMap<>();
        Set<ContentValues> schedulesToAdd = new HashSet<>();
        Set<ContentValues> triggersToAdd = new HashSet<>();
        Set<ContentValues> delaysToAdd = new HashSet<>();

        for (ActionScheduleInfo actionScheduleInfo : schedules) {
            String scheduleId = UUID.randomUUID().toString();
            schedulesToAdd.add(getScheduleInfoContentValues(actionScheduleInfo, scheduleId));

            if (actionScheduleInfo.getDelay() != null) {
                String delayId = UUID.randomUUID().toString();
                ContentValues value = getScheduleDelayContentValues(actionScheduleInfo.getDelay(), scheduleId, delayId);
                delaysToAdd.add(value);
                for (Trigger trigger : actionScheduleInfo.getDelay().getCancellationTriggers()) {
                    ContentValues triggerValue = getTriggerContentValues(trigger, scheduleId, delayId, actionScheduleInfo.getStart());
                    triggersToAdd.add(triggerValue);
                }
            }

            for (Trigger trigger : actionScheduleInfo.getTriggers()) {
                ContentValues value = getTriggerContentValues(trigger, scheduleId, null, actionScheduleInfo.getStart());
                triggersToAdd.add(value);
            }

            ActionSchedule schedule = new ActionSchedule(scheduleId, actionScheduleInfo, 0, false, -1);
            added.put(schedule.getId(), schedule);
        }

        Map<String, ContentValues[]> toAdd = new LinkedHashMap<>();
        toAdd.put(ActionSchedulesTable.TABLE_NAME, schedulesToAdd.toArray(new ContentValues[schedulesToAdd.size()]));
        toAdd.put(ScheduleDelayTable.TABLE_NAME, delaysToAdd.toArray(new ContentValues[delaysToAdd.size()]));
        toAdd.put(TriggersTable.TABLE_NAME, triggersToAdd.toArray(new ContentValues[triggersToAdd.size()]));

        List<ActionSchedule> inserted = new ArrayList<>();
        List<ContentValues> contentValuesList = bulkInsert(toAdd).get(ActionSchedulesTable.TABLE_NAME);

        if (contentValuesList != null) {
            for (ContentValues contentValues : contentValuesList) {
                String insertedId = contentValues.getAsString(ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID);
                if (added.containsKey(insertedId)) {
                    inserted.add(added.get(insertedId));
                }
            }
        }

        return inserted;
    }

    // for update methods

    /**
     * Gets schedules for a given set of IDs.
     *
     * @param ids The set of schedule IDs.
     * @return The list of {@link ActionSchedule} instances.
     */
    List<ActionSchedule> getSchedules(Set<String> ids) {
        final List<ActionSchedule> schedules = new ArrayList<>(ids.size());

        performSubSetOperations(ids, MAX_ARG_COUNT, new SetOperation<String>() {
            @Override
            public void perform(List<String> subset) {

                String query = GET_SCHEDULES_QUERY + " WHERE a." + ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID + " IN ( " + UAStringUtil.repeat("?", subset.size(), ", ") + ")" + ORDER_SCHEDULES_STATEMENT;
                Cursor c = rawQuery(query, subset.toArray(new String[subset.size()]));
                schedules.addAll(generateSchedules(c));
            }
        });

        return schedules;
    }

    /**
     * Gets triggers for a given type.
     *
     * @param type The trigger type.
     * @return THe list of {@link TriggerEntry} instances.
     */
    List<TriggerEntry> getActiveTriggers(int type) {
        List<TriggerEntry> triggers = new ArrayList<>();
        String query = String.format(GET_ACTIVE_TRIGGERS, type, System.currentTimeMillis());
        Cursor cursor = rawQuery(query, null);

        if (cursor == null) {
            return triggers;
        }

        // create triggers
        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            TriggerEntry triggerEntry = generateTrigger(cursor);
            if (triggerEntry != null) {
                triggers.add(triggerEntry);
            }

            cursor.moveToNext();
        }

        cursor.close();
        return triggers;
    }

    /**
     * Bulk applies a series of queries and lists of IDs to update.
     *
     * @param updatesMap A map of queries to ID lists.
     */
    void updateLists(Map<String, List<String>> updatesMap) {
        if (updatesMap.isEmpty()) {
            Logger.verbose("AutomationDataManager - Nothing to update. Returning.");
            return;
        }

        final SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            Logger.error("AutomationDataManager - Unable to update automation rules.");
            return;
        }

        db.beginTransaction();

        for (final Map.Entry<String, List<String>> entry : updatesMap.entrySet()) {

            if (entry.getValue().isEmpty()) {
                continue;
            }

            performSubSetOperations(entry.getValue(), MAX_ARG_COUNT, new SetOperation<String>() {
                @Override
                public void perform(List<String> subset) {

                    String inStatement = UAStringUtil.repeat("?", subset.size(), ", ");
                    SQLiteStatement statement = db.compileStatement(entry.getKey() + " IN ( " + inStatement + " )");
                    for (int i = 0; i < subset.size(); i++) {
                        statement.bindString(i + 1, subset.get(i));
                    }

                    statement.execute();
                }
            });
        }

        db.setTransactionSuccessful();
        db.endTransaction();
    }

    long getScheduleCount() {
        final SQLiteDatabase db = getReadableDatabase();
        if (db == null) {
            return -1;
        }

        return DatabaseUtils.queryNumEntries(db, ActionSchedulesTable.TABLE_NAME);
    }

    // Helpers

    /**
     * Interface for operating on a subset of IDs.
     * @param <T> The list element type.
     */
    interface SetOperation<T> {
        void perform(List<T> subset);
    }

    /**
     * Performs an operation on a list of elements.
     *
     * @param ids The list of IDs.
     * @param subSetCount The subset size to batch in an operation.
     * @param operation The operation to perform.
     * @param <T> The list element type.
     */
    private static <T> void performSubSetOperations(Collection<T> ids, int subSetCount, SetOperation<T> operation) {
        List<T> remaining = new ArrayList<>(ids);

        while (!remaining.isEmpty()) {
            if (remaining.size() > subSetCount) {
                operation.perform(remaining.subList(0, subSetCount));
                remaining = remaining.subList(subSetCount, remaining.size());
            } else {
                operation.perform(remaining);
                remaining.clear();
            }
        }
    }

    /**
     * Generates a list of schedules from a cursor.
     *
     * @param cursor The {@link Cursor} instance.
     * @return The list of {@link ActionSchedule} instances.
     */
    private List<ActionSchedule> generateSchedules(Cursor cursor) {
        List<ActionSchedule> schedules = new ArrayList<>();

        if (cursor == null) {
            return schedules;
        }

        cursor.moveToFirst();

        // The cursor rows will be that of the schedules table joined with the triggers table - each
        // row will contain a single trigger paired with its correlated schedule. We only want to build
        // the schedule info once for every grouping of triggers. As we step through the sorted cursor
        // rows, this will track the last found schedule ID. When that ID has changed in a given row,
        // the current action schedule info builder can be completed and a new one started for the
        // next set of triggers.

        // Initialize the tracked schedule ID, count, and info builder.
        String lastId = "";
        int lastCount = 0;
        boolean lastIsPendingExecution = false;
        long lastPendingExecutionDate = -1;
        ActionScheduleInfo.Builder builder = null;

        while (!cursor.isAfterLast()) {
            // Retrieved the current row's schedule ID, count, and execution status.
            String id = cursor.getString(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID));
            int count = cursor.getInt(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_COUNT));
            boolean isPendingExecution = cursor.getInt(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_IS_PENDING_EXECUTION)) == 1;
            long pendingExecutionDate = cursor.getLong(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_PENDING_EXECUTION_DATE));

            // If a new schedule ID, build and add the previous builder and begin a new one.
            if (!lastId.equals(id)) {
                if (builder != null) {
                    schedules.add(new ActionSchedule(lastId, builder.build(), lastCount, lastIsPendingExecution, lastPendingExecutionDate));
                }

                // Set the new ID, count, and builder to track.
                builder = ActionScheduleInfo.newBuilder();
                lastId = id;
                lastCount = count;
                lastIsPendingExecution = isPendingExecution;
                lastPendingExecutionDate = pendingExecutionDate;

                // Add the relevant schedule info to the new builder.
                builder.setLimit(cursor.getInt(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_LIMIT)))
                       .setGroup(cursor.getString(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_GROUP)))
                       .setStart(cursor.getLong(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_START)))
                       .setEnd(cursor.getLong(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_END)));

                JsonMap actionsMap;
                try {
                    actionsMap = JsonValue.parseString(cursor.getString(cursor.getColumnIndex(ActionSchedulesTable.COLUMN_NAME_ACTIONS))).optMap();
                } catch (JsonException e) {
                    actionsMap = JsonValue.NULL.optMap();
                }

                for (Map.Entry<String, JsonValue> entry : actionsMap.entrySet()) {
                    builder.addAction(entry.getKey(), entry.getValue());
                }
            }

            // If the row contains schedule delays, parse and add them to the builder.
            if (cursor.getColumnIndex(ScheduleDelayTable.COLUMN_NAME_APP_STATE) != -1) {
                ScheduleDelayEntry scheduleDelay = generateScheduleDelay(cursor);
                if (scheduleDelay != null && builder != null) {
                    builder.setDelay(scheduleDelay);
                }
            }

            // If the row contains triggers, parse and add them to the builder.
            if (cursor.getColumnIndex(TriggersTable.COLUMN_NAME_TYPE) != -1) {
                TriggerEntry triggerEntry = generateTrigger(cursor);
                if (triggerEntry != null && builder != null) {
                    builder.addTrigger(triggerEntry);
                }
            }

            cursor.moveToNext();
        }

        // For the final grouping of triggers, build and add the schedule.
        if (builder != null) {
            schedules.add(new ActionSchedule(lastId, builder.build(), lastCount, lastIsPendingExecution, lastPendingExecutionDate));
        }

        cursor.close();

        return schedules;
    }

    /**
     * Generates a trigger from a cursor.
     *
     * @param cursor The {@link Cursor} instance.
     * @return The {@link TriggerEntry} instances.
     */
    private TriggerEntry generateTrigger(Cursor cursor) {
        try {
            int type = cursor.getInt(cursor.getColumnIndex(TriggersTable.COLUMN_NAME_TYPE));
            double goal = cursor.getDouble(cursor.getColumnIndex(TriggersTable.COLUMN_NAME_GOAL));
            double count = cursor.getDouble(cursor.getColumnIndex(TriggersTable.COLUMN_NAME_PROGRESS));

            JsonValue predicateJson = JsonValue.parseString(cursor.getString(cursor.getColumnIndex(TriggersTable.COLUMN_NAME_PREDICATE)));
            JsonPredicate predicate = predicateJson.optMap().isEmpty() ? null : JsonPredicate.parse(predicateJson);
            String id = cursor.getString(cursor.getColumnIndex(TriggersTable._ID));
            String scheduleId = cursor.getString(cursor.getColumnIndex(TriggersTable.COLUMN_NAME_SCHEDULE_ID));
            String delayId = cursor.getString(cursor.getColumnIndex(TriggersTable.COLUMN_NAME_DELAY_ID));

            //noinspection WrongConstant
            return new TriggerEntry(type, goal, predicate, id, scheduleId, delayId, count);
        } catch (JsonException e) {
            Logger.error("AutomationDataManager - failed to generate trigger from cursor.");
            return null;
        }
    }

    /**
     * Generates a schedule delay from a cursor.
     *
     * @param cursor The {@link Cursor} instance.
     * @return The {@link ScheduleDelayEntry} instance.
     */
    private ScheduleDelayEntry generateScheduleDelay(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndex(ScheduleDelayTable._ID));
        String scheduleId = cursor.getString(cursor.getColumnIndex(ScheduleDelayTable.COLUMN_NAME_SCHEDULE_ID));

        @ScheduleDelay.AppState int appState = cursor.getInt(cursor.getColumnIndex(ScheduleDelayTable.COLUMN_NAME_APP_STATE));
        String regionId = cursor.getString(cursor.getColumnIndex(ScheduleDelayTable.COLUMN_NAME_REGION_ID));
        String screen = cursor.getString(cursor.getColumnIndex(ScheduleDelayTable.COLUMN_NAME_SCREEN));
        long seconds = cursor.getLong(cursor.getColumnIndex(ScheduleDelayTable.COLUMN_NAME_SECONDS));

        ScheduleDelay.Builder builder = ScheduleDelay.newBuilder()
                .setAppState(appState)
                .setRegionId(regionId)
                .setScreen(screen)
                .setSeconds(seconds);

        return new ScheduleDelayEntry(builder, id, scheduleId);
    }

    /**
     * Gets the {@link ContentValues} for a schedule.
     *
     * @param actionScheduleInfo The {@link ActionScheduleInfo} instance.
     * @param id The schedule ID.
     * @return The {@link ContentValues} instance.
     */
    private ContentValues getScheduleInfoContentValues(ActionScheduleInfo actionScheduleInfo, String id) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_SCHEDULE_ID, id);
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_ACTIONS, actionScheduleInfo.getActions().toString());
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_LIMIT, actionScheduleInfo.getLimit());
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_GROUP, actionScheduleInfo.getGroup());
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_COUNT, 0);
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_START, actionScheduleInfo.getStart());
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_END, actionScheduleInfo.getEnd());
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_IS_PENDING_EXECUTION, 0);
        contentValues.put(ActionSchedulesTable.COLUMN_NAME_PENDING_EXECUTION_DATE, -1);

        return contentValues;
    }

    /**
     * Gets the {@link ContentValues} for a trigger.
     *
     * @param trigger The {@link Trigger} instance.
     * @param scheduleId The schedule ID.
     * @param delayId The delay ID.
     * @param start The schedule start time in MS.
     * @return The {@link ContentValues} instance.
     */
    private ContentValues getTriggerContentValues(Trigger trigger, String scheduleId, @Nullable String delayId, long start) {
        ContentValues value = new ContentValues();
        value.put(TriggersTable.COLUMN_NAME_TYPE, trigger.getType());
        value.put(TriggersTable.COLUMN_NAME_SCHEDULE_ID, scheduleId);
        value.put(TriggersTable.COLUMN_NAME_PREDICATE, trigger.getPredicate() == null ? null : trigger.getPredicate().toJsonValue().toString());
        value.put(TriggersTable.COLUMN_NAME_GOAL, trigger.getGoal());
        value.put(TriggersTable.COLUMN_NAME_PROGRESS, 0.0);
        value.put(TriggersTable.COLUMN_NAME_START, start);
        value.put(TriggersTable.COLUMN_NAME_DELAY_ID, delayId);

        return value;
    }

    /**
     * Gets the {@link ContentValues} for a schedule delay
     *
     * @param delay The {@link ScheduleDelay} instance.
     * @param scheduleId The associated schedule scheduleId.
     * @return The {@link ContentValues} instance.
     */
    private ContentValues getScheduleDelayContentValues(ScheduleDelay delay, String scheduleId, String delayId) {
        ContentValues value = new ContentValues();

        value.put(ScheduleDelayTable.COLUMN_NAME_DELAY_ID, delayId);
        value.put(ScheduleDelayTable.COLUMN_NAME_SCHEDULE_ID, scheduleId);
        value.put(ScheduleDelayTable.COLUMN_NAME_APP_STATE, delay.getAppState());
        value.put(ScheduleDelayTable.COLUMN_NAME_REGION_ID, delay.getRegionId());
        value.put(ScheduleDelayTable.COLUMN_NAME_SCREEN, delay.getScreen());
        value.put(ScheduleDelayTable.COLUMN_NAME_SECONDS, delay.getSeconds());

        return value;
    }
}
