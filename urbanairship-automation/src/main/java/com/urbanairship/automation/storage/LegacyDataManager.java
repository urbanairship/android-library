/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import com.urbanairship.Logger;
import com.urbanairship.util.DataManager;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * {@link DataManager} class for automation schedules.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LegacyDataManager extends DataManager {

    public interface TriggerTable {

        String COLUMN_NAME_ID = "t_row_id";
        String TABLE_NAME = "triggers";
        String COLUMN_NAME_TYPE = "t_type";
        String COLUMN_NAME_SCHEDULE_ID = "t_s_id";
        String COLUMN_NAME_PREDICATE = "t_predicate";
        String COLUMN_NAME_PROGRESS = "t_progress";
        String COLUMN_NAME_GOAL = "t_goal";
        String COLUMN_NAME_IS_CANCELLATION = "t_cancellation";

    }

    public interface ScheduleTable {

        String TABLE_NAME = "action_schedules";
        String COLUMN_NAME_SCHEDULE_ID = "s_id";
        String COLUMN_NAME_METADATA = "s_metadata";
        String COLUMN_NAME_DATA = "s_data";
        String COLUMN_NAME_LIMIT = "s_limit";
        String COLUMN_NAME_PRIORITY = "s_priority";
        String COLUMN_NAME_GROUP = "s_group";
        String COLUMN_NAME_START = "s_start";
        String COLUMN_NAME_END = "s_end";
        String COLUMN_EDIT_GRACE_PERIOD = "s_edit_grace_period";
        String COLUMN_NAME_INTERVAL = "s_interval";
        String COLUMN_NAME_SECONDS = "d_seconds";
        String COLUMN_NAME_SCREEN = "d_screen";
        String COLUMN_NAME_APP_STATE = "d_app_state";
        String COLUMN_NAME_REGION_ID = "d_region_id";
        String COLUMN_NAME_EXECUTION_STATE = "s_execution_state";
        String COLUMN_NAME_EXECUTION_STATE_CHANGE_DATE = "s_execution_state_change_date";
        String COLUMN_NAME_DELAY_FINISH_DATE = "s_pending_execution_date";
        String COLUMN_NAME_COUNT = "s_count";
        String COLUMN_NAME_ID = "s_row_id";
        String COLUMN_NAME_TRIGGER_CONTEXT = "s_trigger_context";

    }

    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 6;

    /**
     * Query for retrieving schedules with associated delays.
     */
    private static final String GET_SCHEDULES_QUERY = "SELECT * FROM " + ScheduleTable.TABLE_NAME + " a"
            + " LEFT OUTER JOIN " + TriggerTable.TABLE_NAME + " b ON a." + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + "=b." + TriggerTable.COLUMN_NAME_SCHEDULE_ID;

    /**
     * Class constructor.
     *
     * @param context The app context.
     * @param appKey The app key.
     */
    public LegacyDataManager(@NonNull Context context, @NonNull String appKey, @NonNull String dbName) {
        super(context, appKey, dbName, DATABASE_VERSION);
    }

    @Override
    protected void onCreate(@NonNull SQLiteDatabase db) {
        Logger.debug("Creating automation database");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + ScheduleTable.TABLE_NAME + " ("
                + ScheduleTable.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + " TEXT UNIQUE,"
                + ScheduleTable.COLUMN_NAME_METADATA + " TEXT,"
                + ScheduleTable.COLUMN_NAME_DATA + " TEXT,"
                + ScheduleTable.COLUMN_NAME_START + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_END + " INTEGER,"
                + ScheduleTable.COLUMN_EDIT_GRACE_PERIOD + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_EXECUTION_STATE_CHANGE_DATE + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_COUNT + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_LIMIT + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_PRIORITY + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_GROUP + " TEXT,"
                + ScheduleTable.COLUMN_NAME_EXECUTION_STATE + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_DELAY_FINISH_DATE + " DOUBLE,"
                + ScheduleTable.COLUMN_NAME_APP_STATE + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_REGION_ID + " TEXT,"
                + ScheduleTable.COLUMN_NAME_SCREEN + " TEXT,"
                + ScheduleTable.COLUMN_NAME_SECONDS + " DOUBLE,"
                + ScheduleTable.COLUMN_NAME_INTERVAL + " INTEGER,"
                + ScheduleTable.COLUMN_NAME_TRIGGER_CONTEXT + " TEXT"
                + ");");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TriggerTable.TABLE_NAME + " ("
                + TriggerTable.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TriggerTable.COLUMN_NAME_TYPE + " INTEGER,"
                + TriggerTable.COLUMN_NAME_IS_CANCELLATION + " INTEGER,"
                + TriggerTable.COLUMN_NAME_SCHEDULE_ID + " TEXT,"
                + TriggerTable.COLUMN_NAME_PREDICATE + " TEXT,"
                + TriggerTable.COLUMN_NAME_PROGRESS + " DOUBLE,"
                + TriggerTable.COLUMN_NAME_GOAL + " DOUBLE,"
                + "FOREIGN KEY(" + TriggerTable.COLUMN_NAME_SCHEDULE_ID + ") REFERENCES " + ScheduleTable.TABLE_NAME + "(" + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + ") ON DELETE CASCADE"
                + ");");

        Logger.debug("Automation database created");
    }

    @Override
    protected void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        String tempScheduleTableName = "temp_schedule_entry_table";
        String tempTriggersTableName = "temp_triggers_entry_table";
        String oldIdColumn = "_id";
        String oldActionsColumn = "s_actions";
        String oldPendingExecutionColumn = "s_is_pending_execution";

        switch (oldVersion) {
            case 1:
                // Update the schedule table and rename the ID column.

                db.execSQL("BEGIN TRANSACTION;");
                db.execSQL("ALTER TABLE " + ScheduleTable.TABLE_NAME + " RENAME TO " + tempScheduleTableName + ";");
                db.execSQL("ALTER TABLE " + TriggerTable.TABLE_NAME + " RENAME TO " + tempTriggersTableName + ";");

                db.execSQL("CREATE TABLE " + ScheduleTable.TABLE_NAME + " ("
                        + ScheduleTable.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + " TEXT UNIQUE,"
                        + oldActionsColumn + " TEXT,"
                        + ScheduleTable.COLUMN_NAME_START + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_END + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_COUNT + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_LIMIT + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_GROUP + " TEXT,"
                        + oldPendingExecutionColumn + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_DELAY_FINISH_DATE + " DOUBLE,"
                        + ScheduleTable.COLUMN_NAME_APP_STATE + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_REGION_ID + " TEXT,"
                        + ScheduleTable.COLUMN_NAME_SCREEN + " TEXT,"
                        + ScheduleTable.COLUMN_NAME_SECONDS + " DOUBLE"
                        + ");");

                db.execSQL("CREATE TABLE " + TriggerTable.TABLE_NAME + "("
                        + TriggerTable.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + TriggerTable.COLUMN_NAME_TYPE + " INTEGER,"
                        + TriggerTable.COLUMN_NAME_IS_CANCELLATION + " INTEGER,"
                        + TriggerTable.COLUMN_NAME_SCHEDULE_ID + " TEXT,"
                        + TriggerTable.COLUMN_NAME_PREDICATE + " TEXT,"
                        + TriggerTable.COLUMN_NAME_PROGRESS + " DOUBLE,"
                        + TriggerTable.COLUMN_NAME_GOAL + " DOUBLE,"
                        + "FOREIGN KEY(" + TriggerTable.COLUMN_NAME_SCHEDULE_ID + ") REFERENCES " + ScheduleTable.TABLE_NAME + "(" + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + ") ON DELETE CASCADE"
                        + ");");

                db.execSQL("INSERT INTO " + ScheduleTable.TABLE_NAME + "("
                        + ScheduleTable.COLUMN_NAME_ID + ", "
                        + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + ", "
                        + oldActionsColumn + ", "
                        + ScheduleTable.COLUMN_NAME_START + ", "
                        + ScheduleTable.COLUMN_NAME_END + ", "
                        + ScheduleTable.COLUMN_NAME_COUNT + ", "
                        + ScheduleTable.COLUMN_NAME_LIMIT + ", "
                        + ScheduleTable.COLUMN_NAME_PRIORITY + ", "
                        + ScheduleTable.COLUMN_NAME_GROUP + ", "
                        + oldPendingExecutionColumn + ", "
                        + ScheduleTable.COLUMN_NAME_DELAY_FINISH_DATE + ", "
                        + ScheduleTable.COLUMN_NAME_APP_STATE + ", "
                        + ScheduleTable.COLUMN_NAME_REGION_ID + ", "
                        + ScheduleTable.COLUMN_NAME_SCREEN + ", "
                        + ScheduleTable.COLUMN_NAME_SECONDS + ") " +
                        "SELECT "
                        + oldIdColumn + ", "
                        + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + ", "
                        + oldActionsColumn + ", "
                        + ScheduleTable.COLUMN_NAME_START + ", "
                        + ScheduleTable.COLUMN_NAME_END + ", "
                        + ScheduleTable.COLUMN_NAME_COUNT + ", "
                        + ScheduleTable.COLUMN_NAME_LIMIT + ", "
                        + ScheduleTable.COLUMN_NAME_GROUP + ", "
                        + "0, 0.0, 1, NULL, NULL, 0 " +
                        "FROM " + tempScheduleTableName + ";");

                db.execSQL("INSERT INTO " + TriggerTable.TABLE_NAME + "("
                        + TriggerTable.COLUMN_NAME_ID + ", "
                        + TriggerTable.COLUMN_NAME_TYPE + ", "
                        + TriggerTable.COLUMN_NAME_IS_CANCELLATION + ", "
                        + TriggerTable.COLUMN_NAME_SCHEDULE_ID + ", "
                        + TriggerTable.COLUMN_NAME_PREDICATE + ", "
                        + TriggerTable.COLUMN_NAME_PROGRESS + ", "
                        + TriggerTable.COLUMN_NAME_GOAL + ") " +
                        "SELECT "
                        + oldIdColumn + ", "
                        + TriggerTable.COLUMN_NAME_TYPE + ", "
                        + "0, "
                        + TriggerTable.COLUMN_NAME_SCHEDULE_ID + ", "
                        + TriggerTable.COLUMN_NAME_PREDICATE + ", "
                        + TriggerTable.COLUMN_NAME_PROGRESS + ", "
                        + TriggerTable.COLUMN_NAME_GOAL +
                        " FROM " + tempTriggersTableName + ";");

                db.execSQL("DROP TABLE " + tempScheduleTableName + ";");
                db.execSQL("DROP TABLE " + tempTriggersTableName + ";");
                db.execSQL("COMMIT;");

            case 2:
                db.execSQL("BEGIN TRANSACTION;");
                db.execSQL("ALTER TABLE " + ScheduleTable.TABLE_NAME + " RENAME TO " + tempScheduleTableName + ";");
                db.execSQL("ALTER TABLE " + TriggerTable.TABLE_NAME + " RENAME TO " + tempTriggersTableName + ";");

                db.execSQL("CREATE TABLE " + ScheduleTable.TABLE_NAME + " ("
                        + ScheduleTable.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + " TEXT UNIQUE,"
                        + ScheduleTable.COLUMN_NAME_DATA + " TEXT,"
                        + ScheduleTable.COLUMN_NAME_START + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_END + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_COUNT + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_LIMIT + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_PRIORITY + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_GROUP + " TEXT,"
                        + ScheduleTable.COLUMN_NAME_EXECUTION_STATE + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_DELAY_FINISH_DATE + " DOUBLE,"
                        + ScheduleTable.COLUMN_NAME_APP_STATE + " INTEGER,"
                        + ScheduleTable.COLUMN_NAME_REGION_ID + " TEXT,"
                        + ScheduleTable.COLUMN_NAME_SCREEN + " TEXT,"
                        + ScheduleTable.COLUMN_NAME_SECONDS + " DOUBLE"
                        + ");");

                db.execSQL("CREATE TABLE IF NOT EXISTS " + TriggerTable.TABLE_NAME + " ("
                        + TriggerTable.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + TriggerTable.COLUMN_NAME_TYPE + " INTEGER,"
                        + TriggerTable.COLUMN_NAME_IS_CANCELLATION + " INTEGER,"
                        + TriggerTable.COLUMN_NAME_SCHEDULE_ID + " TEXT,"
                        + TriggerTable.COLUMN_NAME_PREDICATE + " TEXT,"
                        + TriggerTable.COLUMN_NAME_PROGRESS + " DOUBLE,"
                        + TriggerTable.COLUMN_NAME_GOAL + " DOUBLE,"
                        + "FOREIGN KEY(" + TriggerTable.COLUMN_NAME_SCHEDULE_ID + ") REFERENCES " + ScheduleTable.TABLE_NAME + "(" + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + ") ON DELETE CASCADE"
                        + ");");

                db.execSQL("INSERT INTO " + ScheduleTable.TABLE_NAME + "("
                        + ScheduleTable.COLUMN_NAME_ID + ", "
                        + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + ", "
                        + ScheduleTable.COLUMN_NAME_DATA + ", "
                        + ScheduleTable.COLUMN_NAME_START + ", "
                        + ScheduleTable.COLUMN_NAME_END + ", "
                        + ScheduleTable.COLUMN_NAME_COUNT + ", "
                        + ScheduleTable.COLUMN_NAME_LIMIT + ", "
                        + ScheduleTable.COLUMN_NAME_PRIORITY + ", "
                        + ScheduleTable.COLUMN_NAME_GROUP + ", "
                        + ScheduleTable.COLUMN_NAME_EXECUTION_STATE + ", "
                        + ScheduleTable.COLUMN_NAME_DELAY_FINISH_DATE + ", "
                        + ScheduleTable.COLUMN_NAME_APP_STATE + ", "
                        + ScheduleTable.COLUMN_NAME_REGION_ID + ", "
                        + ScheduleTable.COLUMN_NAME_SCREEN + ", "
                        + ScheduleTable.COLUMN_NAME_SECONDS + ") " +
                        "SELECT "
                        + ScheduleTable.COLUMN_NAME_ID + ", "
                        + ScheduleTable.COLUMN_NAME_SCHEDULE_ID + ", "
                        + oldActionsColumn + ", "
                        + ScheduleTable.COLUMN_NAME_START + ", "
                        + ScheduleTable.COLUMN_NAME_END + ", "
                        + ScheduleTable.COLUMN_NAME_COUNT + ", "
                        + ScheduleTable.COLUMN_NAME_LIMIT + ", "
                        + "0, " // Default priority
                        + ScheduleTable.COLUMN_NAME_GROUP + ", "
                        + oldPendingExecutionColumn + ", "
                        + ScheduleTable.COLUMN_NAME_DELAY_FINISH_DATE + ", "
                        + ScheduleTable.COLUMN_NAME_APP_STATE + ", "
                        + ScheduleTable.COLUMN_NAME_REGION_ID + ", "
                        + ScheduleTable.COLUMN_NAME_SCREEN + ", "
                        + ScheduleTable.COLUMN_NAME_SECONDS + " " +
                        "FROM " + tempScheduleTableName + ";");

                db.execSQL("INSERT INTO " + TriggerTable.TABLE_NAME + "("
                        + TriggerTable.COLUMN_NAME_ID + ", "
                        + TriggerTable.COLUMN_NAME_TYPE + ", "
                        + TriggerTable.COLUMN_NAME_IS_CANCELLATION + ", "
                        + TriggerTable.COLUMN_NAME_SCHEDULE_ID + ", "
                        + TriggerTable.COLUMN_NAME_PREDICATE + ", "
                        + TriggerTable.COLUMN_NAME_PROGRESS + ", "
                        + TriggerTable.COLUMN_NAME_GOAL + ") " +
                        "SELECT "
                        + TriggerTable.COLUMN_NAME_ID + ", "
                        + TriggerTable.COLUMN_NAME_TYPE + ", "
                        + TriggerTable.COLUMN_NAME_IS_CANCELLATION + ", "
                        + TriggerTable.COLUMN_NAME_SCHEDULE_ID + ", "
                        + TriggerTable.COLUMN_NAME_PREDICATE + ", "
                        + TriggerTable.COLUMN_NAME_PROGRESS + ", "
                        + TriggerTable.COLUMN_NAME_GOAL +
                        " FROM " + tempTriggersTableName + ";");

                db.execSQL("DROP TABLE " + tempScheduleTableName + ";");
                db.execSQL("DROP TABLE " + tempTriggersTableName + ";");

                db.execSQL("COMMIT;");

            case 3:
                db.execSQL("BEGIN TRANSACTION;");
                db.execSQL("ALTER TABLE " + ScheduleTable.TABLE_NAME + " ADD COLUMN " + ScheduleTable.COLUMN_NAME_EXECUTION_STATE_CHANGE_DATE + " INTEGER;");
                db.execSQL("ALTER TABLE " + ScheduleTable.TABLE_NAME + " ADD COLUMN " + ScheduleTable.COLUMN_EDIT_GRACE_PERIOD + " INTEGER;");
                db.execSQL("ALTER TABLE " + ScheduleTable.TABLE_NAME + " ADD COLUMN " + ScheduleTable.COLUMN_NAME_INTERVAL + " INTEGER;");
                db.execSQL("COMMIT;");

            case 4:
                db.execSQL("BEGIN TRANSACTION;");
                db.execSQL("ALTER TABLE " + ScheduleTable.TABLE_NAME + " ADD COLUMN " + ScheduleTable.COLUMN_NAME_METADATA + " TEXT;");
                db.execSQL("COMMIT;");

                break;

            case 5:
                db.execSQL("BEGIN TRANSACTION;");
                db.execSQL("ALTER TABLE " + ScheduleTable.TABLE_NAME + " ADD COLUMN " + ScheduleTable.COLUMN_NAME_TRIGGER_CONTEXT + " TEXT;");
                db.execSQL("COMMIT;");

                break;
            default:
                // Kills the table and existing data
                db.execSQL("DROP TABLE IF EXISTS " + ScheduleTable.TABLE_NAME);
                db.execSQL("DROP TABLE IF EXISTS " + TriggerTable.TABLE_NAME);

                // Recreates the database with a new version
                onCreate(db);
        }
    }

    @Override
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Logs that the database is being downgraded
        Logger.debug("Dropping automation database. Downgrading from version %s to %s", oldVersion, newVersion);

        // Drop the table and recreate it
        db.execSQL("DROP TABLE IF EXISTS " + TriggerTable.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + ScheduleTable.TABLE_NAME);
        onCreate(db);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onConfigure(@NonNull SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * Deletes all schedules.
     */
    public void deleteAllSchedules() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.execSQL("DROP TABLE IF EXISTS " + TriggerTable.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ScheduleTable.TABLE_NAME);
            db.close();
        } catch (Exception e) {
            Logger.error(e, "Failed to delete schedules.");
        }
    }

    public Cursor querySchedules() {
        try {
            return rawQuery(GET_SCHEDULES_QUERY, null);
        } catch (SQLException e) {
            Logger.error(e, "LegacyAutomationDataManager - Unable to get schedules.");
        }

        return null;
    }

}
