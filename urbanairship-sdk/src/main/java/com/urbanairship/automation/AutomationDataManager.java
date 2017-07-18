/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.util.DataManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.urbanairship.util.UAStringUtil.repeat;

/**
 * {@link DataManager} class for automation schedules.
 */
class AutomationDataManager extends DataManager {

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
    private static final String ORDER_SCHEDULES_STATEMENT = " ORDER BY " + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " ASC";

    /**
     * Query for retrieving schedules with associated delays.
     */
    private static final String GET_SCHEDULES_QUERY = "SELECT * FROM " + ScheduleEntry.TABLE_NAME + " a"
            + " LEFT OUTER JOIN " + TriggerEntry.TABLE_NAME + " b ON a." + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + "=b." + TriggerEntry.COLUMN_NAME_SCHEDULE_ID;

    /**
     * Query for retrieving active triggers.
     */
    private static final String GET_ACTIVE_TRIGGERS = "SELECT * FROM " + TriggerEntry.TABLE_NAME + " t" +
            " LEFT OUTER JOIN " + ScheduleEntry.TABLE_NAME + " a ON a." + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " = t." + TriggerEntry.COLUMN_NAME_SCHEDULE_ID +
            " WHERE t." + TriggerEntry.COLUMN_NAME_TYPE + " = ? AND a." + ScheduleEntry.COLUMN_NAME_START + " < ? AND (t." + TriggerEntry.COLUMN_NAME_IS_CANCELLATION + " = 0 OR a." + ScheduleEntry.COLUMN_NAME_IS_PENDING_EXECUTION + " = 1)";

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
    protected void onCreate(@NonNull SQLiteDatabase db) {
        Logger.debug("AutomationDataManager - Creating automation database");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScheduleEntry.TABLE_NAME + " ("
                + ScheduleEntry.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " TEXT UNIQUE,"
                + ScheduleEntry.COLUMN_NAME_ACTIONS + " TEXT,"
                + ScheduleEntry.COLUMN_NAME_START + " INTEGER,"
                + ScheduleEntry.COLUMN_NAME_END + " INTEGER,"
                + ScheduleEntry.COLUMN_NAME_COUNT + " INTEGER,"
                + ScheduleEntry.COLUMN_NAME_LIMIT + " INTEGER,"
                + ScheduleEntry.COLUMN_NAME_GROUP + " TEXT,"
                + ScheduleEntry.COLUMN_NAME_IS_PENDING_EXECUTION + " INTEGER,"
                + ScheduleEntry.COLUMN_NAME_PENDING_EXECUTION_DATE + " DOUBLE,"
                + ScheduleEntry.COLUMN_NAME_APP_STATE + " INTEGER,"
                + ScheduleEntry.COLUMN_NAME_REGION_ID + " TEXT,"
                + ScheduleEntry.COLUMN_NAME_SCREEN + " TEXT,"
                + ScheduleEntry.COLUMN_NAME_SECONDS + " DOUBLE"
                + ");");


        db.execSQL("CREATE TABLE IF NOT EXISTS " + TriggerEntry.TABLE_NAME + " ("
                + TriggerEntry.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TriggerEntry.COLUMN_NAME_TYPE + " INTEGER,"
                + TriggerEntry.COLUMN_NAME_IS_CANCELLATION + " INTEGER,"
                + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + " TEXT,"
                + TriggerEntry.COLUMN_NAME_PREDICATE + " TEXT,"
                + TriggerEntry.COLUMN_NAME_PROGRESS + " DOUBLE,"
                + TriggerEntry.COLUMN_NAME_GOAL + " DOUBLE,"
                + "FOREIGN KEY(" + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + ") REFERENCES " + ScheduleEntry.TABLE_NAME + "(" + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + ") ON DELETE CASCADE"
                + ");");

        Logger.debug("AutomationDataManager - Automation database created");
    }

    @Override
    protected void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        switch (oldVersion) {
            // Upgrade 1 ~> 2 changes
            //
            //      action_schedules:
            //          * _id column renamed to s_row_id
            //          * schedule delay information added
            //
            //      triggers
            //          * _id column renamed to t_row_id
            //          * is_cancellation column added
            //          * t_start column removed
            //
            case 1:
                // Update the schedule table and rename the ID column.
                String tempScheduleTableName = "temp_schedule_entry_table";
                String tempTriggersTableName = "temp_triggers_entry_table";
                String oldIdColumn = "_id";

                db.execSQL("BEGIN TRANSACTION;");
                db.execSQL("ALTER TABLE " + ScheduleEntry.TABLE_NAME + " RENAME TO " + tempScheduleTableName + ";");
                db.execSQL("ALTER TABLE " + TriggerEntry.TABLE_NAME + " RENAME TO " + tempTriggersTableName + ";");

                db.execSQL("CREATE TABLE " + ScheduleEntry.TABLE_NAME + " ("
                        + ScheduleEntry.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " TEXT UNIQUE,"
                        + ScheduleEntry.COLUMN_NAME_ACTIONS + " TEXT,"
                        + ScheduleEntry.COLUMN_NAME_START + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_END + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_COUNT + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_LIMIT + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_GROUP + " TEXT,"
                        + ScheduleEntry.COLUMN_NAME_IS_PENDING_EXECUTION + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_PENDING_EXECUTION_DATE + " DOUBLE,"
                        + ScheduleEntry.COLUMN_NAME_APP_STATE + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_REGION_ID + " TEXT,"
                        + ScheduleEntry.COLUMN_NAME_SCREEN + " TEXT,"
                        + ScheduleEntry.COLUMN_NAME_SECONDS + " DOUBLE"
                        + ");");

                db.execSQL("CREATE TABLE " + TriggerEntry.TABLE_NAME + "("
                        + TriggerEntry.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + TriggerEntry.COLUMN_NAME_TYPE + " INTEGER,"
                        + TriggerEntry.COLUMN_NAME_IS_CANCELLATION + " INTEGER,"
                        + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + " TEXT,"
                        + TriggerEntry.COLUMN_NAME_PREDICATE + " TEXT,"
                        + TriggerEntry.COLUMN_NAME_PROGRESS + " DOUBLE,"
                        + TriggerEntry.COLUMN_NAME_GOAL + " DOUBLE,"
                        + "FOREIGN KEY(" + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + ") REFERENCES " + ScheduleEntry.TABLE_NAME + "(" + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + ") ON DELETE CASCADE"
                        + ");");

                db.execSQL("INSERT INTO " + ScheduleEntry.TABLE_NAME + "("
                        + ScheduleEntry.COLUMN_NAME_ID + ", "
                        + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + ", "
                        + ScheduleEntry.COLUMN_NAME_ACTIONS + ", "
                        + ScheduleEntry.COLUMN_NAME_START + ", "
                        + ScheduleEntry.COLUMN_NAME_END + ", "
                        + ScheduleEntry.COLUMN_NAME_COUNT + ", "
                        + ScheduleEntry.COLUMN_NAME_LIMIT + ", "
                        + ScheduleEntry.COLUMN_NAME_GROUP + ", "
                        + ScheduleEntry.COLUMN_NAME_IS_PENDING_EXECUTION + ", "
                        + ScheduleEntry.COLUMN_NAME_PENDING_EXECUTION_DATE + ", "
                        + ScheduleEntry.COLUMN_NAME_APP_STATE + ", "
                        + ScheduleEntry.COLUMN_NAME_REGION_ID + ", "
                        + ScheduleEntry.COLUMN_NAME_SCREEN + ", "
                        + ScheduleEntry.COLUMN_NAME_SECONDS + ") " +
                        "SELECT "
                        + oldIdColumn + ", "
                        + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + ", "
                        + ScheduleEntry.COLUMN_NAME_ACTIONS + ", "
                        + ScheduleEntry.COLUMN_NAME_START + ", "
                        + ScheduleEntry.COLUMN_NAME_END + ", "
                        + ScheduleEntry.COLUMN_NAME_COUNT + ", "
                        + ScheduleEntry.COLUMN_NAME_LIMIT + ", "
                        + ScheduleEntry.COLUMN_NAME_GROUP + ", "
                        + "0, 0.0, 1, NULL, NULL, 0 " +
                        "FROM " + tempScheduleTableName + ";");

                db.execSQL("INSERT INTO " + TriggerEntry.TABLE_NAME + "("
                        + TriggerEntry.COLUMN_NAME_ID + ", "
                        + TriggerEntry.COLUMN_NAME_TYPE + ", "
                        + TriggerEntry.COLUMN_NAME_IS_CANCELLATION + ", "
                        + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + ", "
                        + TriggerEntry.COLUMN_NAME_PREDICATE + ", "
                        + TriggerEntry.COLUMN_NAME_PROGRESS + ", "
                        + TriggerEntry.COLUMN_NAME_GOAL + ") " +
                        "SELECT "
                        + oldIdColumn + ", "
                        + TriggerEntry.COLUMN_NAME_TYPE + ", "
                        + "0, "
                        + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + ", "
                        + TriggerEntry.COLUMN_NAME_PREDICATE + ", "
                        + TriggerEntry.COLUMN_NAME_PROGRESS + ", "
                        + TriggerEntry.COLUMN_NAME_GOAL +
                        " FROM " + tempTriggersTableName + ";");

                db.execSQL("DROP TABLE " + tempScheduleTableName + ";");
                db.execSQL("DROP TABLE " + tempTriggersTableName + ";");
                db.execSQL("COMMIT;");
                break;

            default:
                // Kills the table and existing data
                db.execSQL("DROP TABLE IF EXISTS " + ScheduleEntry.TABLE_NAME);
                db.execSQL("DROP TABLE IF EXISTS " + TriggerEntry.TABLE_NAME);

                // Recreates the database with a new version
                onCreate(db);
        }
    }

    @Override
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Logs that the database is being downgraded
        Logger.debug("AutomationDataManager - Downgrading automation database from version " + oldVersion + " to " + newVersion);

        switch (oldVersion) {
            case 2:
                // Update the schedule table and rename the ID column.
                String tempScheduleTableName = "temp_schedule_entry_table";
                String tempTriggersTableName = "temp_triggers_entry_table";
                String oldIdColumn = "_id";
                String triggersStartColumn = "t_start";

                db.execSQL("BEGIN TRANSACTION;");
                db.execSQL("ALTER TABLE " + TriggerEntry.TABLE_NAME + " RENAME TO " + tempTriggersTableName + ";");
                db.execSQL("ALTER TABLE " + tempTriggersTableName + " ADD COLUMN " + triggersStartColumn + ";");
                db.execSQL("UPDATE " + tempTriggersTableName + " SET " + triggersStartColumn
                        + " = (SELECT " + ScheduleEntry.COLUMN_NAME_START + " FROM " + ScheduleEntry.TABLE_NAME + " WHERE " + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " = " + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + ");");
                db.execSQL("ALTER TABLE " + ScheduleEntry.TABLE_NAME + " RENAME TO " + tempScheduleTableName + ";");


                db.execSQL("CREATE TABLE " + ScheduleEntry.TABLE_NAME + " ("
                        + oldIdColumn + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " TEXT UNIQUE,"
                        + ScheduleEntry.COLUMN_NAME_ACTIONS + " TEXT,"
                        + ScheduleEntry.COLUMN_NAME_START + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_END + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_COUNT + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_LIMIT + " INTEGER,"
                        + ScheduleEntry.COLUMN_NAME_GROUP + " TEXT"
                        + ");");

                db.execSQL("CREATE TABLE " + TriggerEntry.TABLE_NAME + "("
                        + oldIdColumn + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + TriggerEntry.COLUMN_NAME_TYPE + " INTEGER,"
                        + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + " TEXT,"
                        + TriggerEntry.COLUMN_NAME_PREDICATE + " TEXT,"
                        + TriggerEntry.COLUMN_NAME_PROGRESS + " DOUBLE,"
                        + TriggerEntry.COLUMN_NAME_GOAL + " DOUBLE,"
                        + triggersStartColumn + " INTEGER,"
                        + "FOREIGN KEY(" + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + ") REFERENCES " + ScheduleEntry.TABLE_NAME + "(" + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + ") ON DELETE CASCADE"
                        + ");");

                db.execSQL("INSERT INTO " + ScheduleEntry.TABLE_NAME + "("
                        + oldIdColumn + ", "
                        + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + ", "
                        + ScheduleEntry.COLUMN_NAME_ACTIONS + ", "
                        + ScheduleEntry.COLUMN_NAME_START + ", "
                        + ScheduleEntry.COLUMN_NAME_END + ", "
                        + ScheduleEntry.COLUMN_NAME_COUNT + ", "
                        + ScheduleEntry.COLUMN_NAME_LIMIT + ", "
                        + ScheduleEntry.COLUMN_NAME_GROUP + ") "
                        + "SELECT "
                        + ScheduleEntry.COLUMN_NAME_ID + ", "
                        + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + ", "
                        + ScheduleEntry.COLUMN_NAME_ACTIONS + ", "
                        + ScheduleEntry.COLUMN_NAME_START + ", "
                        + ScheduleEntry.COLUMN_NAME_END + ", "
                        + ScheduleEntry.COLUMN_NAME_COUNT + ", "
                        + ScheduleEntry.COLUMN_NAME_LIMIT + ", "
                        + ScheduleEntry.COLUMN_NAME_GROUP +
                        " FROM " + tempScheduleTableName + ";");

                db.execSQL("INSERT INTO " + TriggerEntry.TABLE_NAME + " ("
                        + oldIdColumn + ", "
                        + TriggerEntry.COLUMN_NAME_TYPE + ", "
                        + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + ", "
                        + TriggerEntry.COLUMN_NAME_PREDICATE + ", "
                        + TriggerEntry.COLUMN_NAME_PROGRESS + ", "
                        + TriggerEntry.COLUMN_NAME_GOAL + ", "
                        + triggersStartColumn + ")" +
                        " SELECT "
                        + TriggerEntry.COLUMN_NAME_ID + ", "
                        + TriggerEntry.COLUMN_NAME_TYPE + ", "
                        + TriggerEntry.COLUMN_NAME_SCHEDULE_ID + ", "
                        + TriggerEntry.COLUMN_NAME_PREDICATE + ", "
                        + TriggerEntry.COLUMN_NAME_PROGRESS + ", "
                        + TriggerEntry.COLUMN_NAME_GOAL + ", "
                        + triggersStartColumn +
                        " FROM " + tempTriggersTableName +
                        " WHERE " + TriggerEntry.COLUMN_NAME_IS_CANCELLATION + " != 1;");

                db.execSQL("DROP TABLE " + tempTriggersTableName + ";");
                db.execSQL("DROP TABLE " + tempScheduleTableName + ";");

                db.execSQL("COMMIT;");
                break;
            default:
                // Drop the table and recreate it
                db.execSQL("DROP TABLE IF EXISTS " + TriggerEntry.TABLE_NAME);
                db.execSQL("DROP TABLE IF EXISTS " + ScheduleEntry.TABLE_NAME);
                onCreate(db);
        }
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
        if (delete(ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " = ?", new String[] { scheduleId }) < 0) {
            Logger.warn("AutomationDataManager - failed to delete schedule for schedule ID " + scheduleId);
        }
    }

    /**
     * Deletes schedules given a group.
     *
     * @param group The schedule group.
     */
    void deleteGroup(String group) {
        if (delete(ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_NAME_GROUP + " = ?", new String[] { group }) < 0) {
            Logger.warn("AutomationDataManager - failed to delete schedules for group " + group);
        }
    }

    /**
     * Deletes all schedules.
     */
    void deleteAllSchedules() {
        if (delete(ScheduleEntry.TABLE_NAME, null, null) < 0) {
            Logger.warn("AutomationDataManager - failed to delete schedules");
        }
    }

    /**
     * Saves schedules.
     *
     * @param scheduleEntries Collection of schedule entries.
     */
    void saveSchedules(Collection<ScheduleEntry> scheduleEntries) {
        if (scheduleEntries.isEmpty()) {
            return;
        }

        final SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            Logger.error("AutomationDataManager - Unable to update automation rules.");
            return;
        }

        db.beginTransaction();

        for (ScheduleEntry scheduleEntry : scheduleEntries) {
            if (!scheduleEntry.save(db)) {
                db.endTransaction();
                return;
            }
        }

        db.setTransactionSuccessful();
        db.endTransaction();
    }

    /**
     * Saves triggers.
     *
     * @param triggerEntries Collection of trigger entries.
     */
    void saveTriggers(Collection<TriggerEntry> triggerEntries) {
        if (triggerEntries.isEmpty()) {
            return;
        }

        final SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            Logger.error("AutomationDataManager - Unable to update automation rules.");
            return;
        }

        db.beginTransactionNonExclusive();

        for (TriggerEntry triggerEntry : triggerEntries) {
            if (!triggerEntry.save(db)) {
                db.endTransaction();
                return;
            }
        }

        db.setTransactionSuccessful();
        db.endTransaction();
    }


    /**
     * Deletes schedules given a list of IDs.
     *
     * @param schedulesToDelete The list of schedule IDs.
     */
    void deleteSchedules(@NonNull Collection<String> schedulesToDelete) {

        performSubSetOperations(schedulesToDelete, MAX_ARG_COUNT, new SetOperation<String>() {
            @Override
            public void perform(List<String> subset) {
                String inStatement = repeat("?", subset.size(), ", ");
                delete(ScheduleEntry.TABLE_NAME, ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " IN ( " + inStatement + " )", subset.toArray(new String[subset.size()]));
            }
        });

    }


    /**
     * Gets schedules for a given group.
     *
     * @param group The schedule group.
     * @return The list of {@link ActionSchedule} instances.
     */
    List<ScheduleEntry> getScheduleEntries(String group) {
        String query = GET_SCHEDULES_QUERY + " WHERE a." + ScheduleEntry.COLUMN_NAME_GROUP + "=?" + ORDER_SCHEDULES_STATEMENT;
        Cursor cursor = rawQuery(query, new String[] { String.valueOf(group) });
        if (cursor == null) {
            return Collections.emptyList();
        }

        List<ScheduleEntry> entries = generateSchedules(cursor);
        cursor.close();
        return entries;
    }

    /**
     * Gets all schedules.
     *
     * @return The list of {@link ActionSchedule} instances.
     */
    List<ScheduleEntry> getScheduleEntries() {
        String query = GET_SCHEDULES_QUERY + ORDER_SCHEDULES_STATEMENT;
        Cursor cursor = rawQuery(query, null);
        if (cursor == null) {
            return Collections.emptyList();
        }

        List<ScheduleEntry> entries = generateSchedules(cursor);
        cursor.close();
        return entries;
    }

    /**
     * Gets schedules for a given set of IDs.
     *
     * @param ids The set of schedule IDs.
     * @return The list of {@link ActionSchedule} instances.
     */
    List<ScheduleEntry> getScheduleEntries(Set<String> ids) {
        final List<ScheduleEntry> schedules = new ArrayList<>(ids.size());

        performSubSetOperations(ids, MAX_ARG_COUNT, new SetOperation<String>() {
            @Override
            public void perform(List<String> subset) {
                String query = GET_SCHEDULES_QUERY + " WHERE a." + ScheduleEntry.COLUMN_NAME_SCHEDULE_ID + " IN ( " + repeat("?", subset.size(), ", ") + ")" + ORDER_SCHEDULES_STATEMENT;


                Cursor cursor = rawQuery(query, subset.toArray(new String[subset.size()]));
                if (cursor != null) {
                    schedules.addAll(generateSchedules(cursor));
                    cursor.close();
                }

            }
        });

        return schedules;
    }

    /**
     * Gets all schedules that are pending execution.
     *
     * @return A list of pending execution schedules.
     */
    List<ScheduleEntry> getPendingExecutionSchedules() {
        String query = GET_SCHEDULES_QUERY + " WHERE a." + ScheduleEntry.COLUMN_NAME_IS_PENDING_EXECUTION + " = 1";
        Cursor cursor = rawQuery(query, null);

        if (cursor == null) {
            return Collections.emptyList();
        }

        List<ScheduleEntry> entries = generateSchedules(cursor);
        cursor.close();
        return entries;
    }

    /**
     * Interface for operating on a subset of IDs.
     *
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
     * Gets triggers for a given type.
     *
     * @param type The trigger type.
     * @return THe list of {@link TriggerEntry} instances.
     */
    List<TriggerEntry> getActiveTriggerEntries(int type) {
        List<TriggerEntry> triggers = new ArrayList<>();
        Cursor cursor = rawQuery(GET_ACTIVE_TRIGGERS, new String[] { String.valueOf(type), String.valueOf(System.currentTimeMillis()) });

        if (cursor == null) {
            return triggers;
        }

        // create triggers
        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            TriggerEntry triggerEntry = new TriggerEntry(cursor);
            triggers.add(triggerEntry);
            cursor.moveToNext();
        }

        cursor.close();
        return triggers;
    }


    /**
     * Returns the current schedule count.
     *
     * @return The current schedule count.
     */
    long getScheduleCount() {
        final SQLiteDatabase db = getReadableDatabase();
        if (db == null) {
            return -1;
        }

        return DatabaseUtils.queryNumEntries(db, ScheduleEntry.TABLE_NAME);
    }

    /**
     * Helper method to generate schedule entries from a a cursor.
     *
     * @param cursor The cursor.
     * @return A list of schedule entries.
     */
    @NonNull
    private List<ScheduleEntry> generateSchedules(@NonNull Cursor cursor) {
        cursor.moveToFirst();

        List<ScheduleEntry> entries = new ArrayList<>();
        while (!cursor.isAfterLast()) {
            ScheduleEntry entry = ScheduleEntry.fromCursor(cursor);
            if (entry != null) {
                entries.add(entry);
            }
            cursor.moveToNext();
        }

        return entries;
    }
}
