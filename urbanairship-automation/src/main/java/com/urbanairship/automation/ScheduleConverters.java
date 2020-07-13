/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.automation.storage.FullSchedule;
import com.urbanairship.automation.storage.ScheduleEntity;
import com.urbanairship.automation.storage.TriggerEntity;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

/**
 * Storage to modal object converters for schedule and triggers.
 */
class ScheduleConverters {

    @NonNull
    static List<FullSchedule> convertSchedules(@NonNull Collection<Schedule> schedules) {
        List<FullSchedule> entries = new ArrayList<>();
        for (Schedule schedule : schedules) {
            entries.add(convert(schedule));
        }
        return entries;
    }

    @NonNull
    static Schedule convert(@NonNull FullSchedule entry) throws JsonException, IllegalArgumentException {
        Schedule.Builder scheduleBuilder =  createScheduleBuilder(entry.schedule.scheduleType, entry.schedule.data)
                       .setId(entry.schedule.scheduleId)
                       .setMetadata(entry.schedule.metadata)
                       .setGroup(entry.schedule.group)
                       .setEnd(entry.schedule.scheduleEnd)
                       .setStart(entry.schedule.scheduleStart)
                       .setLimit(entry.schedule.limit)
                       .setPriority(entry.schedule.priority)
                       .setInterval(entry.schedule.interval, TimeUnit.MILLISECONDS)
                       .setEditGracePeriod(entry.schedule.editGracePeriod, TimeUnit.MILLISECONDS)
                       .setAudience(entry.schedule.audience);

        ScheduleDelay.Builder delayBuilder = ScheduleDelay.newBuilder()
                .setAppState(entry.schedule.appState)
                .setRegionId(entry.schedule.regionId)
                .setScreens(entry.schedule.screens)
                .setSeconds(entry.schedule.seconds);

        for (TriggerEntity entity : entry.triggers) {
            if (entity.isCancellation) {
                delayBuilder.addCancellationTrigger(convert(entity));
            } else {
                scheduleBuilder.addTrigger(convert(entity));
            }
        }

        return scheduleBuilder.setDelay(delayBuilder.build()).build();
    }

    @NonNull
    static Trigger convert(@NonNull TriggerEntity entry) {
        return new Trigger(entry.triggerType, entry.goal, entry.jsonPredicate);
    }

    @NonNull
    static FullSchedule convert(@NonNull Schedule schedule) {
        ScheduleEntity entity = new ScheduleEntity();
        List<TriggerEntity> triggerEntities = new ArrayList<>();

        entity.scheduleId = schedule.getId();
        entity.group = schedule.getGroup();
        entity.metadata = schedule.getMetadata();
        entity.scheduleEnd = schedule.getEnd();
        entity.scheduleStart = schedule.getStart();
        entity.limit = schedule.getLimit();
        entity.priority = schedule.getPriority();
        entity.interval = schedule.getInterval();
        entity.editGracePeriod = schedule.getEditGracePeriod();
        entity.audience = schedule.getAudience();
        entity.scheduleType = schedule.getType();
        entity.data = ((JsonSerializable) schedule.requireData()).toJsonValue();

        for (Trigger trigger : schedule.getTriggers()) {
            triggerEntities.add(convert(trigger, false, schedule.getId()));
        }

        ScheduleDelay delay = schedule.getDelay();
        if (delay != null) {
            entity.screens = delay.getScreens();
            entity.regionId = delay.getRegionId();
            entity.appState = delay.getAppState();
            entity.seconds = delay.getSeconds();

            for (Trigger trigger : delay.getCancellationTriggers()) {
                triggerEntities.add(convert(trigger, true, schedule.getId()));
            }
        }

        return new FullSchedule(entity, triggerEntities);
    }

    @NonNull
    private static TriggerEntity convert(@NonNull Trigger trigger,
                                         boolean isCancellation,
                                         @NonNull String parentScheduleId) {
        TriggerEntity entity = new TriggerEntity();
        entity.goal = trigger.getGoal();
        entity.isCancellation = isCancellation;
        entity.triggerType = trigger.getType();
        entity.jsonPredicate = trigger.getPredicate();
        entity.parentScheduleId = parentScheduleId;
        return entity;
    }

    @NonNull
    private static Schedule.Builder createScheduleBuilder(@NonNull String scheduleType, @NonNull JsonValue json) throws JsonException {
        switch (scheduleType) {
            case Schedule.TYPE_ACTION:
                return Schedule.newActionScheduleBuilder(json.optMap());
            case Schedule.TYPE_IN_APP_MESSAGE:
                return Schedule.newMessageScheduleBuilder(InAppMessage.fromJson(json));
            default:
                throw new IllegalArgumentException("Invalid type: " + scheduleType);
        }
    }
}
