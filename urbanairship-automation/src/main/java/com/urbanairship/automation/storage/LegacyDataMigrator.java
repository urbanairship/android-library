/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;

import com.urbanairship.Logger;
import com.urbanairship.automation.Audience;
import com.urbanairship.automation.Schedule;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Migrates in-app and ua_automation into new database.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LegacyDataMigrator {

    private final Context context;
    private final AirshipRuntimeConfig config;

    public LegacyDataMigrator(@NonNull Context context, @NonNull AirshipRuntimeConfig runtimeConfig) {
        this.context = context.getApplicationContext();
        this.config = runtimeConfig;
    }

    public void migrateData(@NonNull AutomationDao dao) {
        LegacyDataManager actionDataManager = new LegacyDataManager(context, config.getConfigOptions().appKey, "ua_automation.db");
        if (actionDataManager.databaseExists(context)) {
            Logger.verbose("Migrating actions automation database.");
            migrateDatabase(actionDataManager, Schedule.TYPE_ACTION, dao);
        }

        LegacyDataManager iamDataManager = new LegacyDataManager(context, config.getConfigOptions().appKey, "in-app");
        if (iamDataManager.databaseExists(context)) {
            Logger.verbose("Migrating in-app message database.");
            migrateDatabase(iamDataManager, Schedule.TYPE_IN_APP_MESSAGE, dao);
        }
    }

    private void migrateDatabase(@NonNull LegacyDataManager dataManager, @NonNull @Schedule.Type String dataType, @NonNull AutomationDao dao) {

        Cursor cursor = null;
        try {
            cursor = dataManager.querySchedules();
            if (cursor != null) {
                migrateDataFromCursor(cursor, dataType, dao);
            }
        } catch (Exception e) {
            Logger.error(e, "Error when migrating database.");
        } finally {
            closeCursor(cursor);
            dataManager.deleteAllSchedules();
            dataManager.close();
            dataManager.deleteDatabase(context);
        }

    }

    private void migrateDataFromCursor(@NonNull Cursor cursor, @NonNull @Schedule.Type String dataType, @NonNull AutomationDao dao) {
        ScheduleEntity scheduleEntity = null;
        List<TriggerEntity> triggerEntities = new ArrayList<>();
        String currentScheduleId = null;

        List<String> messageIds = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String scheduleId = cursor.getString(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_SCHEDULE_ID));

            if (!UAStringUtil.equals(currentScheduleId, scheduleId)) {
                if (scheduleEntity != null) {
                    Logger.verbose("Migrating schedule: %s triggers: %s", scheduleEntity, triggerEntities);
                    dao.insert(new FullSchedule(scheduleEntity, triggerEntities));
                }

                currentScheduleId = scheduleId;
                triggerEntities.clear();
                scheduleEntity = null;
            }

            if (scheduleEntity == null) {
                try {
                    scheduleEntity = new ScheduleEntity();
                    scheduleEntity.scheduleId = cursor.getString(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_SCHEDULE_ID));
                    scheduleEntity.metadata = JsonValue.parseString(cursor.getString(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_METADATA))).optMap();
                    scheduleEntity.count = cursor.getInt(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_COUNT));
                    scheduleEntity.limit = cursor.getInt(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_LIMIT));
                    scheduleEntity.priority = cursor.getInt(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_PRIORITY));
                    scheduleEntity.group = cursor.getString(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_GROUP));
                    scheduleEntity.editGracePeriod = cursor.getLong(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_EDIT_GRACE_PERIOD));
                    scheduleEntity.scheduleEnd = cursor.getLong(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_END));
                    scheduleEntity.scheduleStart = cursor.getLong(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_START));
                    scheduleEntity.executionState = cursor.getInt(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_EXECUTION_STATE));
                    scheduleEntity.executionStateChangeDate = cursor.getLong(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_EXECUTION_STATE_CHANGE_DATE));
                    scheduleEntity.appState = cursor.getInt(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_APP_STATE));
                    scheduleEntity.regionId = cursor.getString(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_REGION_ID));
                    scheduleEntity.interval = cursor.getLong(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_INTERVAL));
                    scheduleEntity.seconds = cursor.getLong(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_SECONDS));
                    scheduleEntity.screens = parseScreens(JsonValue.parseString(cursor.getString(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_SCREEN))));

                    JsonValue dataJson = JsonValue.parseString(cursor.getString(cursor.getColumnIndex(LegacyDataManager.ScheduleTable.COLUMN_NAME_DATA)));
                    scheduleEntity.scheduleType = dataType;
                    scheduleEntity.data = dataJson;

                    if (Schedule.TYPE_IN_APP_MESSAGE.equals(dataType)) {
                        // Set the message ID as the schedule ID
                        String messageId = dataJson.optMap().opt("message_id").getString(scheduleId);

                        if (InAppMessage.SOURCE_APP_DEFINED.equals(dataJson.optMap().opt("source").optString())) {
                            // Add the old schedule ID as metadata just in case devs have no way of
                            // mapping the old schedule ID.
                            scheduleEntity.metadata = JsonMap.newBuilder().putAll(scheduleEntity.metadata)
                                                             .put("com.urbanairship.original_schedule_id", scheduleEntity.scheduleId)
                                                             .put("com.urbanairship.original_message_id", messageId)
                                                             .build();

                            // Unique it
                            messageId = getUniqueId(messageId, messageIds);
                        }

                        scheduleEntity.scheduleId = messageId;
                        messageIds.add(messageId);

                        // Migrate audience to top level
                        JsonValue audienceJson = dataJson.optMap().get("audience");
                        if (audienceJson != null) {
                            scheduleEntity.audience = Audience.fromJson(audienceJson);
                        }
                    }

                } catch (JsonException e) {
                    Logger.error(e, "Failed to parse schedule entry.");
                    continue;
                }
            }

            // If the row contains triggers, parse and add them to the builder.
            if (cursor.getColumnIndex(LegacyDataManager.TriggerTable.COLUMN_NAME_TYPE) != -1) {
                TriggerEntity triggerEntity = new TriggerEntity();
                triggerEntity.parentScheduleId = scheduleEntity.scheduleId;
                triggerEntity.triggerType = cursor.getInt(cursor.getColumnIndex(LegacyDataManager.TriggerTable.COLUMN_NAME_TYPE));
                triggerEntity.goal = cursor.getDouble(cursor.getColumnIndex(LegacyDataManager.TriggerTable.COLUMN_NAME_GOAL));
                triggerEntity.progress = cursor.getDouble(cursor.getColumnIndex(LegacyDataManager.TriggerTable.COLUMN_NAME_PROGRESS));
                triggerEntity.jsonPredicate = parseJsonPredicate(cursor.getString(cursor.getColumnIndex(LegacyDataManager.TriggerTable.COLUMN_NAME_PREDICATE)));
                triggerEntity.isCancellation = cursor.getInt(cursor.getColumnIndex(LegacyDataManager.TriggerTable.COLUMN_NAME_IS_CANCELLATION)) == 1;
                triggerEntities.add(triggerEntity);
            }

            cursor.moveToNext();
        }

        if (scheduleEntity != null) {
            Logger.verbose("Migrating schedule: %s triggers: %s", scheduleEntity, triggerEntities);
            dao.insert(new FullSchedule(scheduleEntity, triggerEntities));
        }
    }

    private String getUniqueId(String messageId, List<String> messageIds) {
        String uniqueId = messageId;
        int i = 0;
        while (messageIds.contains(uniqueId)) {
            i++;
            uniqueId = messageId + "#" + i;
        }
        return uniqueId;
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

    @Nullable
    private JsonPredicate parseJsonPredicate(String payload) {
        try {
            JsonValue jsonValue = JsonValue.parseString(payload);
            if (!jsonValue.isNull()) {
                return JsonPredicate.parse(jsonValue);
            }
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse JSON predicate.");
            return null;
        }

        return null;
    }

    private void closeCursor(@Nullable Cursor cursor) {
        try {
            if (cursor != null) {
                cursor.close();
            }
        } catch (SQLException e) {
            Logger.error(e, "Failed to close cursor.");
        }
    }

}
