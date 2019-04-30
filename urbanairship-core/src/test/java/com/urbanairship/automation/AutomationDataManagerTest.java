/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AutomationDataManagerTest extends BaseTestCase {

    AutomationDataManager dataManager;

    @Before
    public void setUp() {
        dataManager = new AutomationDataManager(RuntimeEnvironment.application, "appKey", "test");
    }

    @After
    public void takeDown() {
        dataManager.deleteAllSchedules();
    }

    @Test
    public void testDeleteSchedule() {
        List<ScheduleEntry> schedules = createSchedules(20);

        dataManager.saveSchedules(schedules);
        List<ScheduleEntry> retrieved = dataManager.getScheduleEntries();
        assertEquals(20, retrieved.size());

        dataManager.deleteSchedule(dataManager.getScheduleEntries("group 5").get(0).scheduleId);
        retrieved = dataManager.getScheduleEntries();
        assertEquals(19, retrieved.size());
    }

    @Test
    public void testDeleteSchedules() {
        List<ScheduleEntry> schedules = createSchedules(20);
        dataManager.saveSchedules(schedules);
        List<ScheduleEntry> retrieved = dataManager.getScheduleEntries();
        assertEquals(20, retrieved.size());

        dataManager.deleteAllSchedules();
        retrieved = dataManager.getScheduleEntries();
        assertEquals(0, retrieved.size());
    }

    @Test
    public void testDeleteSchedulesByList() {
        List<ScheduleEntry> schedules = createSchedules(20);

        dataManager.saveSchedules(schedules);
        List<ScheduleEntry> retrieved = dataManager.getScheduleEntries();
        assertEquals(20, retrieved.size());

        dataManager.deleteSchedules(Arrays.asList(
                dataManager.getScheduleEntries("group 6").get(0).scheduleId,
                dataManager.getScheduleEntries("group 7").get(0).scheduleId,
                dataManager.getScheduleEntries("group 5").get(0).scheduleId));
        retrieved = dataManager.getScheduleEntries();
        assertEquals(17, retrieved.size());
    }

    @Test
    public void testGetSchedule() {
        ScheduleEntry scheduleEntry = createSchedules(1).get(0);
        TriggerEntry triggerEntry = scheduleEntry.triggerEntries.get(0);
        dataManager.saveSchedules(Collections.singletonList(scheduleEntry));

        ScheduleEntry retrieved = dataManager.getScheduleEntries(Collections.singleton(dataManager.getScheduleEntries("group 0").get(0).scheduleId)).get(0);
        assertEquals(scheduleEntry.group, retrieved.group);
        assertEquals(scheduleEntry.getData(), retrieved.getData());
        assertEquals(scheduleEntry.getEnd(), retrieved.getEnd());
        assertEquals(scheduleEntry.getStart(), retrieved.getStart());
        assertEquals(scheduleEntry.getLimit(), retrieved.getLimit());
        assertEquals(scheduleEntry.getPriority(), retrieved.getPriority());

        List<TriggerEntry> scheduleTriggers = retrieved.triggerEntries;
        Collections.sort(scheduleTriggers, new Comparator<TriggerEntry>() {
            @Override
            public int compare(TriggerEntry lhs, TriggerEntry rhs) {
                if (lhs.type == Trigger.LIFE_CYCLE_FOREGROUND) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        assertEquals(triggerEntry.goal, scheduleTriggers.get(0).goal, 0.0);
        assertEquals(triggerEntry.jsonPredicate, scheduleTriggers.get(0).jsonPredicate);
        assertEquals(triggerEntry.type, scheduleTriggers.get(0).type);
        assertEquals(0, retrieved.getCount());
    }

    @Test
    public void testGetSchedulesWithTag() {
        List<ScheduleEntry> schedules = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Trigger trigger = Triggers.newForegroundTriggerBuilder()
                                      .setGoal(10)
                                      .build();

            ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                            .setGroup("group")
                                                            .setStart(System.currentTimeMillis())
                                                            .setEnd(System.currentTimeMillis() + 100000)
                                                            .setLimit(100)
                                                            .addAction("test_action", JsonValue.wrap("action_value"))
                                                            .addTrigger(trigger)
                                                            .build();

            ScheduleEntry scheduleEntry = new ScheduleEntry("shedule_entry " + i, schedule, JsonMap.EMPTY_MAP);
            schedules.add(scheduleEntry);
        }

        dataManager.saveSchedules(schedules);
        List<ScheduleEntry> retrieved = dataManager.getScheduleEntries("group");
        assertEquals(20, retrieved.size());
    }

    @Test
    public void testGetSchedules() {
        List<ScheduleEntry> schedules = createSchedules(20);
        dataManager.saveSchedules(schedules);
        List<ScheduleEntry> retrieved = dataManager.getScheduleEntries();
        assertEquals(20, retrieved.size());
    }

    @Test
    public void testGetSchedulesWithList() {
        dataManager.saveSchedules(createSchedules(2));
        List<ScheduleEntry> retrieved = dataManager.getScheduleEntries(new HashSet<>(Arrays.asList(
                dataManager.getScheduleEntries().get(0).scheduleId,
                dataManager.getScheduleEntries().get(1).scheduleId)));
        assertEquals(2, retrieved.size());
    }

    @Test
    public void testGetTriggers() {
        // Triggers that have yet to start should not be returned by the data manager.
        ActionScheduleInfo futureSchedule = ActionScheduleInfo.newBuilder()
                                                              .addAction("test_action", JsonValue.wrap("action_value"))
                                                              .addTrigger(Triggers.newForegroundTriggerBuilder().setGoal(3).build())
                                                              .setLimit(5)
                                                              .setGroup("group")
                                                              .setStart(System.currentTimeMillis() + 1000000)
                                                              .build();
        ScheduleEntry scheduleEntry = new ScheduleEntry("schedule_entry", futureSchedule, JsonMap.EMPTY_MAP);

        dataManager.saveSchedules(Collections.singletonList(scheduleEntry));
        List<ScheduleEntry> schedules = createSchedules(20);
        dataManager.saveSchedules(schedules);
        List<TriggerEntry> retrieved = dataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        assertEquals(20, retrieved.size());
    }

    @Test
    public void testGetTriggersForSchedule() throws Exception {
        ActionScheduleInfo futureSchedule = ActionScheduleInfo.newBuilder()
                                                              .addAction("test_action", JsonValue.wrap("action_value"))
                                                              .addTrigger(Triggers.newForegroundTriggerBuilder().setGoal(3).build())
                                                              .setLimit(5)
                                                              .setGroup("group")
                                                              .setStart(System.currentTimeMillis())
                                                              .build();

        ScheduleEntry scheduleEntry = new ScheduleEntry("schedule_entry", futureSchedule, JsonMap.EMPTY_MAP);

        dataManager.saveSchedules(Collections.singletonList(scheduleEntry));
        List<ScheduleEntry> schedules = createSchedules(20);
        dataManager.saveSchedules(schedules);

        List<TriggerEntry> retrieved = dataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND, scheduleEntry.scheduleId);
        assertEquals(1, retrieved.size());
    }

    @Test
    public void testBulkInsertSchedules() throws Exception {
        Trigger firstTrigger = Triggers.newForegroundTriggerBuilder()
                                       .setGoal(10)
                                       .build();

        ActionScheduleInfo firstActionScheduleInfo = ActionScheduleInfo.newBuilder()
                                                                       .setGroup("group 0")
                                                                       .setStart(System.currentTimeMillis())
                                                                       .setEnd(System.currentTimeMillis() + 100000)
                                                                       .setLimit(100)
                                                                       .setPriority(1)
                                                                       .addAction("test_action", JsonValue.wrap("action_value"))
                                                                       .addTrigger(firstTrigger)
                                                                       .build();

        Trigger secondTrigger = Triggers.newBackgroundTriggerBuilder()
                                        .setGoal(10)
                                        .build();

        ActionScheduleInfo secondActionScheduleInfo = ActionScheduleInfo.newBuilder()
                                                                        .setGroup("group 1")
                                                                        .setStart(System.currentTimeMillis())
                                                                        .setEnd(System.currentTimeMillis() + 100000)
                                                                        .setLimit(100)
                                                                        .setPriority(2)
                                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                                        .addTrigger(secondTrigger)
                                                                        .build();

        ScheduleEntry firstScheduleEntry = new ScheduleEntry("schedule_id_1", firstActionScheduleInfo, JsonMap.EMPTY_MAP);
        ScheduleEntry secondScheduleEntry = new ScheduleEntry("schedule_id_2", secondActionScheduleInfo, JsonMap.EMPTY_MAP);
        List<ScheduleEntry> scheduleEntries = Arrays.asList(firstScheduleEntry, secondScheduleEntry);
        dataManager.saveSchedules(scheduleEntries);

        List<ScheduleEntry> schedules = dataManager.getScheduleEntries();
        Collections.sort(schedules, new Comparator<ScheduleEntry>() {
            @Override
            public int compare(ScheduleEntry lhs, ScheduleEntry rhs) {
                if (lhs.group.equals("first")) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        assertEquals(2, schedules.size());
        assertEquals(firstActionScheduleInfo.getGroup(), schedules.get(0).group);
        assertEquals(JsonValue.wrap(firstActionScheduleInfo.getActions()), schedules.get(0).getData());
        assertEquals(firstActionScheduleInfo.getEnd(), schedules.get(0).getEnd());
        assertEquals(firstActionScheduleInfo.getStart(), schedules.get(0).getStart());
        assertEquals(firstActionScheduleInfo.getLimit(), schedules.get(0).getLimit());
        assertEquals(firstActionScheduleInfo.getPriority(), schedules.get(0).getPriority());

        assertEquals(firstTrigger.getGoal(), schedules.get(0).triggerEntries.get(0).goal, 0.0);
        assertEquals(firstTrigger.getPredicate(), schedules.get(0).triggerEntries.get(0).jsonPredicate);
        assertEquals(firstTrigger.getType(), schedules.get(0).triggerEntries.get(0).type);
        assertEquals(0, schedules.get(0).getCount());

        assertEquals(secondActionScheduleInfo.getGroup(), schedules.get(1).group);
        assertEquals(JsonValue.wrap(secondActionScheduleInfo.getActions()), schedules.get(1).getData());
        assertEquals(secondActionScheduleInfo.getEnd(), schedules.get(1).getEnd());
        assertEquals(secondActionScheduleInfo.getStart(), schedules.get(1).getStart());
        assertEquals(secondActionScheduleInfo.getLimit(), schedules.get(1).getLimit());
        assertEquals(secondActionScheduleInfo.getPriority(), schedules.get(1).getPriority());

        assertEquals(secondTrigger.getGoal(), schedules.get(1).triggerEntries.get(0).goal, 0.0);
        assertEquals(secondTrigger.getPredicate(), schedules.get(1).triggerEntries.get(0).jsonPredicate);
        assertEquals(secondTrigger.getType(), schedules.get(1).triggerEntries.get(0).type);
        assertEquals(0, schedules.get(0).getCount());

        List<TriggerEntry> triggers = dataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        assertEquals(1, triggers.size());
        assertEquals(firstTrigger.getGoal(), triggers.get(0).goal, 0.0);
        assertEquals(firstTrigger.getPredicate(), triggers.get(0).jsonPredicate);
        assertEquals(firstTrigger.getType(), triggers.get(0).type);

        triggers = dataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_BACKGROUND);
        assertEquals(1, triggers.size());
        assertEquals(secondTrigger.getGoal(), triggers.get(0).goal, 0.0);
        assertEquals(secondTrigger.getPredicate(), triggers.get(0).jsonPredicate);
        assertEquals(secondTrigger.getType(), triggers.get(0).type);
    }

    @Test
    public void testInsertSchedule() {
        ScheduleEntry actionScheduleInfo = createSchedules(1).get(0);
        TriggerEntry trigger = actionScheduleInfo.triggerEntries.get(0);
        dataManager.saveSchedules(Collections.singletonList(actionScheduleInfo));

        List<ScheduleEntry> schedules = dataManager.getScheduleEntries();
        assertEquals(1, schedules.size());
        assertEquals(actionScheduleInfo.group, schedules.get(0).group);
        assertEquals(actionScheduleInfo.getData(), schedules.get(0).getData());
        assertEquals(actionScheduleInfo.getEnd(), schedules.get(0).getEnd());
        assertEquals(actionScheduleInfo.getStart(), schedules.get(0).getStart());
        assertEquals(actionScheduleInfo.getLimit(), schedules.get(0).getLimit());
        assertEquals(actionScheduleInfo.getPriority(), schedules.get(0).getPriority());

        List<TriggerEntry> scheduleTriggers = schedules.get(0).triggerEntries;
        Collections.sort(scheduleTriggers, new Comparator<TriggerEntry>() {
            @Override
            public int compare(TriggerEntry lhs, TriggerEntry rhs) {
                if (lhs.type == Trigger.LIFE_CYCLE_FOREGROUND) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        assertEquals(trigger.goal, scheduleTriggers.get(0).goal, 0.0);
        assertEquals(trigger.jsonPredicate, scheduleTriggers.get(0).jsonPredicate);
        assertEquals(trigger.type, scheduleTriggers.get(0).type);
        assertEquals(0, schedules.get(0).getCount());

        List<TriggerEntry> triggers = dataManager.getActiveTriggerEntries(Trigger.LIFE_CYCLE_FOREGROUND);
        assertEquals(1, triggers.size());
        assertEquals(trigger.goal, triggers.get(0).goal, 0.0);
        assertEquals(trigger.jsonPredicate, triggers.get(0).jsonPredicate);
        assertEquals(trigger.type, triggers.get(0).type);
    }

    @Test
    public void testGetActiveExpiredSchedules() {
        ScheduleEntry expired = createEntry("expired_entry", "expired_group", 0, 1);
        ScheduleEntry current = createEntry("current_entry", "expired_group", 0, System.currentTimeMillis() + 100000);

        dataManager.saveSchedules(Arrays.asList(expired, current));
        List<ScheduleEntry> retrieved = dataManager.getActiveExpiredScheduleEntries();
        assertEquals(1, retrieved.size());
        assertEquals("expired_entry", retrieved.get(0).scheduleId);

    }

    private List<ScheduleEntry> createSchedules(int amount) {
        List<ScheduleEntry> scheduleEntries = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            scheduleEntries.add(createEntry("schedule_id_" + i, "group " + i, System.currentTimeMillis(), System.currentTimeMillis() + 100000));
        }

        return scheduleEntries;
    }

    private ScheduleEntry createEntry(String id, String group, long start, long end) {

        Trigger foreground = Triggers.newForegroundTriggerBuilder()
                                     .setGoal(10)
                                     .build();

        Trigger background = Triggers.newBackgroundTriggerBuilder()
                                     .setGoal(3)
                                     .build();

        ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                        .setGroup(group)
                                                        .setStart(start)
                                                        .setEnd(end)
                                                        .setLimit(100)
                                                        .setPriority(2)
                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                        .addTrigger(foreground)
                                                        .addTrigger(background)
                                                        .build();

        return new ScheduleEntry(id, schedule, JsonMap.EMPTY_MAP);

    }

}
