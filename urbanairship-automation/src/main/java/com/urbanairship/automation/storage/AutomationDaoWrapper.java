/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import com.urbanairship.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Wrapper around the AutomationDao that catches any SQL exceptions.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutomationDaoWrapper extends AutomationDao {

    private final AutomationDao dao;

    public AutomationDaoWrapper(@NonNull AutomationDao dao) {
        this.dao = dao;
    }

    @Override
    public void insert(@NonNull ScheduleEntity entity, @NonNull List<TriggerEntity> entities) {
        try {
            dao.insert(entity, entities);
        } catch (Exception e) {
            Logger.error(e, "Failed to insert schedule %s triggers %s", entity, entities);
        }
    }

    @Override
    public void update(@NonNull ScheduleEntity entity, @NonNull List<TriggerEntity> entities) {
        try {
            dao.update(entity, entities);
        } catch (Exception e) {
            Logger.error(e, "Failed to update schedule %s triggers %s", entity, entities);
        }
    }

    @Override
    public void updateTriggers(@NonNull List<TriggerEntity> entities) {
        try {
            dao.updateTriggers(entities);
        } catch (Exception e) {
            Logger.error(e, "Failed to update triggers %s", entities);
        }
    }

    @Override
    public void delete(@NonNull ScheduleEntity entity) {
        try {
            dao.delete(entity);
        } catch (Exception e) {
            Logger.error(e, "Failed to delete schedule %s", entity);
        }
    }

    @Override
    public int getScheduleCount() {
        try {
            return dao.getScheduleCount();
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedule count");
            return -1;
        }
    }

    @NonNull
    @Override
    public List<FullSchedule> getSchedules() {
        try {
            return dao.getSchedules();
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedules");
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public List<FullSchedule> getSchedulesByType(@NonNull String type) {
        try {
            return dao.getSchedulesByType(type);
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedules by type %s", type);
            return Collections.emptyList();
        }
    }

    @Nullable
    @Override
    public FullSchedule getSchedule(@NonNull String scheduleId) {
        try {
            return dao.getSchedule(scheduleId);
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedule with id %s", scheduleId);
            return null;
        }
    }

    @Nullable
    @Override
    public FullSchedule getSchedule(@NonNull String scheduleId, @NonNull String type) {
        try {
            return dao.getSchedule(scheduleId, type);
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedule with id %s type %s", scheduleId, type);
            return null;
        }
    }

    @NonNull
    @Override
    public List<FullSchedule> getSchedules(@NonNull Collection<String> scheduleIds) {
        try {
            return dao.getSchedules(scheduleIds);
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedules with ids %s", scheduleIds);
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public List<FullSchedule> getSchedules(@NonNull Collection<String> scheduleIds, @NonNull String type) {
        try {
            return dao.getSchedules(scheduleIds, type);
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedules with ids %s type %s", scheduleIds, type);
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public List<FullSchedule> getSchedulesWithGroup(@NonNull String group, @NonNull String type) {
        try {
            return dao.getSchedulesWithGroup(group, type);
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedules with group %s type %s", group, type);
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public List<FullSchedule> getSchedulesWithGroup(@NonNull String group) {
        try {
            return dao.getSchedulesWithGroup(group);
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedules with group %s", group);
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public List<FullSchedule> getSchedulesWithStates(int... executionStates) {
        try {
            return dao.getSchedulesWithStates(executionStates);
        } catch (Exception e) {
            Logger.error(e, "Failed to get schedules with state %s", executionStates);
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public List<FullSchedule> getActiveExpiredSchedules() {
        try {
            return dao.getActiveExpiredSchedules();
        } catch (Exception e) {
            Logger.error(e, "Failed to get active expired schedules");
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public List<TriggerEntity> getActiveTriggers(int type, @NonNull String scheduleId) {
        try {
            return dao.getActiveTriggers(type, scheduleId);
        } catch (Exception e) {
            Logger.error(e, "Failed to get active triggers %s %s", type, scheduleId);
            return Collections.emptyList();
        }
    }

    @NonNull
    @Override
    public List<TriggerEntity> getActiveTriggers(int type) {
        try {
            return dao.getActiveTriggers(type);
        } catch (Exception e) {
            Logger.error(e, "Failed to get active triggers %s", type);
            return Collections.emptyList();
        }
    }

}
