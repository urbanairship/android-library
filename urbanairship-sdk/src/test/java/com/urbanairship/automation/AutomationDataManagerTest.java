/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AutomationDataManagerTest extends BaseTestCase {

    AutomationDataManager dataManager;

    @Before
    public void setUp() {
        dataManager = new AutomationDataManager(RuntimeEnvironment.application, "test");
    }

    @After
    public void takeDown() {
        dataManager.deleteSchedules();
    }

    @Test
    public void testDeleteSchedule() {
        List<ActionScheduleInfo> schedules = createSchedules(20);

        dataManager.insertSchedules(schedules);
        List<ActionSchedule> retrieved = dataManager.getSchedules();
        assertEquals(20, retrieved.size());

        dataManager.deleteSchedule(dataManager.getSchedules("group 5").get(0).getId());
        retrieved = dataManager.getSchedules();
        assertEquals(19, retrieved.size());
    }

    @Test
    public void testDeleteSchedulesByTag() {
        List<ActionScheduleInfo> schedules = createSchedules(20);

        dataManager.insertSchedules(schedules);
        List<ActionSchedule> retrieved = dataManager.getSchedules();
        assertEquals(20, retrieved.size());

        dataManager.deleteSchedules("group 5");
        retrieved = dataManager.getSchedules();
        assertEquals(19, retrieved.size());
    }

    @Test
    public void testDeleteSchedules() {
        List<ActionScheduleInfo> schedules = createSchedules(20);
        dataManager.insertSchedules(schedules);
        List<ActionSchedule> retrieved = dataManager.getSchedules();
        assertEquals(20, retrieved.size());

        dataManager.deleteSchedules();
        retrieved = dataManager.getSchedules();
        assertEquals(0, retrieved.size());
    }

    @Test
    public void testDeleteSchedulesByList() {
        List<ActionScheduleInfo> schedules = createSchedules(20);

        dataManager.insertSchedules(schedules);
        List<ActionSchedule> retrieved = dataManager.getSchedules();
        assertEquals(20, retrieved.size());

        dataManager.bulkDeleteSchedules(Arrays.asList(dataManager.getSchedules("group 6").get(0).getId(), dataManager.getSchedules("group 7").get(0).getId(), dataManager.getSchedules("group 5").get(0).getId()));
        retrieved = dataManager.getSchedules();
        assertEquals(17, retrieved.size());
    }

    @Test
    public void testGetSchedule() {
        ActionScheduleInfo actionScheduleInfo = createSchedules(1).get(0);
        Trigger trigger = actionScheduleInfo.getTriggers().get(0);
        dataManager.insertSchedules(Collections.singletonList(actionScheduleInfo));

        ActionSchedule retrieved = dataManager.getSchedule(dataManager.getSchedules("group 0").get(0).getId());
        assertEquals(actionScheduleInfo.getGroup(), retrieved.getInfo().getGroup());
        assertEquals(actionScheduleInfo.getActions(), retrieved.getInfo().getActions());
        assertEquals(actionScheduleInfo.getEnd(), retrieved.getInfo().getEnd());
        assertEquals(actionScheduleInfo.getStart(), retrieved.getInfo().getStart());
        assertEquals(actionScheduleInfo.getLimit(), retrieved.getInfo().getLimit());

        List<Trigger> scheduleTriggers = retrieved.getInfo().getTriggers();
        Collections.sort(scheduleTriggers, new Comparator<Trigger>() {
            @Override
            public int compare(Trigger lhs, Trigger rhs) {
                if (lhs.getType() == Trigger.LIFE_CYCLE_FOREGROUND) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        assertEquals(trigger.getGoal(), scheduleTriggers.get(0).getGoal(), 0.0);
        assertEquals(trigger.getPredicate(), scheduleTriggers.get(0).getPredicate());
        assertEquals(trigger.getType(), scheduleTriggers.get(0).getType());
        assertEquals(0, retrieved.getCount());
    }

    @Test
    public void testGetSchedulesWithTag() {
        List<ActionScheduleInfo> schedules = new ArrayList<>();
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
            schedules.add(schedule);
        }

        dataManager.insertSchedules(schedules);
        List<ActionSchedule> retrieved = dataManager.getSchedules("group");
        assertEquals(20, retrieved.size());
    }

    @Test
    public void testGetSchedules() {
        List<ActionScheduleInfo> schedules = createSchedules(20);
        dataManager.insertSchedules(schedules);
        List<ActionSchedule> retrieved = dataManager.getSchedules();
        assertEquals(20, retrieved.size());
    }

    @Test
    public void testGetSchedulesWithList() {
        dataManager.insertSchedules(createSchedules(2));
        List<ActionSchedule> retrieved = dataManager.getSchedules(new HashSet<>(Arrays.asList(dataManager.getSchedules().get(0).getId(), dataManager.getSchedules().get(1).getId())));
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
        dataManager.insertSchedules(Collections.singletonList(futureSchedule));
        List<ActionScheduleInfo> schedules = createSchedules(20);
        dataManager.insertSchedules(schedules);
        List<TriggerEntry> retrieved = dataManager.getActiveTriggers(Trigger.LIFE_CYCLE_FOREGROUND);
        assertEquals(20, retrieved.size());
    }

    @Test
    public void testBulkInsertSchedules() {
        Trigger firstTrigger = Triggers.newForegroundTriggerBuilder()
                                      .setGoal(10)
                                      .build();

        ActionScheduleInfo firstActionScheduleInfo = ActionScheduleInfo.newBuilder()
                                                                       .setGroup("first")
                                                                       .setStart(System.currentTimeMillis())
                                                                       .setEnd(System.currentTimeMillis() + 100000)
                                                                       .setLimit(100)
                                                                       .addAction("test_action", JsonValue.wrap("action_value"))
                                                                       .addTrigger(firstTrigger)
                                                                       .build();

        Trigger secondTrigger = Triggers.newBackgroundTriggerBuilder()
                                       .setGoal(10)
                                       .build();

        ActionScheduleInfo secondActionScheduleInfo = ActionScheduleInfo.newBuilder()
                                                                        .setGroup("second")
                                                                        .setStart(System.currentTimeMillis())
                                                                        .setEnd(System.currentTimeMillis() + 100000)
                                                                        .setLimit(100)
                                                                        .addAction("test_action", JsonValue.wrap("action_value"))
                                                                        .addTrigger(secondTrigger)
                                                                        .build();

        dataManager.insertSchedules(Arrays.asList(firstActionScheduleInfo, secondActionScheduleInfo));
        List<ActionSchedule> schedules = dataManager.getSchedules();
        Collections.sort(schedules, new Comparator<ActionSchedule>() {
            @Override
            public int compare(ActionSchedule lhs, ActionSchedule rhs) {
                if (lhs.getInfo().getGroup().equals("first")) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        assertEquals(2, schedules.size());
        assertEquals(firstActionScheduleInfo.getGroup(), schedules.get(0).getInfo().getGroup());
        assertEquals(firstActionScheduleInfo.getActions(), schedules.get(0).getInfo().getActions());
        assertEquals(firstActionScheduleInfo.getEnd(), schedules.get(0).getInfo().getEnd());
        assertEquals(firstActionScheduleInfo.getStart(), schedules.get(0).getInfo().getStart());
        assertEquals(firstActionScheduleInfo.getLimit(), schedules.get(0).getInfo().getLimit());

        assertEquals(firstTrigger.getGoal(), schedules.get(0).getInfo().getTriggers().get(0).getGoal(), 0.0);
        assertEquals(firstTrigger.getPredicate(), schedules.get(0).getInfo().getTriggers().get(0).getPredicate());
        assertEquals(firstTrigger.getType(), schedules.get(0).getInfo().getTriggers().get(0).getType());
        assertEquals(0, schedules.get(0).getCount());

        assertEquals(secondActionScheduleInfo.getGroup(), schedules.get(1).getInfo().getGroup());
        assertEquals(secondActionScheduleInfo.getActions(), schedules.get(1).getInfo().getActions());
        assertEquals(secondActionScheduleInfo.getEnd(), schedules.get(1).getInfo().getEnd());
        assertEquals(secondActionScheduleInfo.getStart(), schedules.get(1).getInfo().getStart());
        assertEquals(secondActionScheduleInfo.getLimit(), schedules.get(1).getInfo().getLimit());

        assertEquals(secondTrigger.getGoal(), schedules.get(1).getInfo().getTriggers().get(0).getGoal(), 0.0);
        assertEquals(secondTrigger.getPredicate(), schedules.get(1).getInfo().getTriggers().get(0).getPredicate());
        assertEquals(secondTrigger.getType(), schedules.get(1).getInfo().getTriggers().get(0).getType());
        assertEquals(0, schedules.get(0).getCount());

        List<TriggerEntry> triggers = dataManager.getActiveTriggers(Trigger.LIFE_CYCLE_FOREGROUND);
        assertEquals(1, triggers.size());
        assertEquals(firstTrigger.getGoal(), triggers.get(0).getGoal(), 0.0);
        assertEquals(firstTrigger.getPredicate(), triggers.get(0).getPredicate());
        assertEquals(firstTrigger.getType(), triggers.get(0).getType());

        triggers = dataManager.getActiveTriggers(Trigger.LIFE_CYCLE_BACKGROUND);
        assertEquals(1, triggers.size());
        assertEquals(secondTrigger.getGoal(), triggers.get(0).getGoal(), 0.0);
        assertEquals(secondTrigger.getPredicate(), triggers.get(0).getPredicate());
        assertEquals(secondTrigger.getType(), triggers.get(0).getType());
    }

    @Test
    public void testInsertSchedule() {
        ActionScheduleInfo actionScheduleInfo = createSchedules(1).get(0);
        Trigger trigger = actionScheduleInfo.getTriggers().get(0);
        dataManager.insertSchedules(Collections.singletonList(actionScheduleInfo));

        List<ActionSchedule> schedules = dataManager.getSchedules();
        assertEquals(1, schedules.size());
        assertEquals(actionScheduleInfo.getGroup(), schedules.get(0).getInfo().getGroup());
        assertEquals(actionScheduleInfo.getActions(), schedules.get(0).getInfo().getActions());
        assertEquals(actionScheduleInfo.getEnd(), schedules.get(0).getInfo().getEnd());
        assertEquals(actionScheduleInfo.getStart(), schedules.get(0).getInfo().getStart());
        assertEquals(actionScheduleInfo.getLimit(), schedules.get(0).getInfo().getLimit());

        List<Trigger> scheduleTriggers = schedules.get(0).getInfo().getTriggers();
        Collections.sort(scheduleTriggers, new Comparator<Trigger>() {
            @Override
            public int compare(Trigger lhs, Trigger rhs) {
                if (lhs.getType() == Trigger.LIFE_CYCLE_FOREGROUND) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        assertEquals(trigger.getGoal(), scheduleTriggers.get(0).getGoal(), 0.0);
        assertEquals(trigger.getPredicate(), scheduleTriggers.get(0).getPredicate());
        assertEquals(trigger.getType(), scheduleTriggers.get(0).getType());
        assertEquals(0, schedules.get(0).getCount());

        List<TriggerEntry> triggers = dataManager.getActiveTriggers(Trigger.LIFE_CYCLE_FOREGROUND);
        assertEquals(1, triggers.size());
        assertEquals(trigger.getGoal(), triggers.get(0).getGoal(), 0.0);
        assertEquals(trigger.getPredicate(), triggers.get(0).getPredicate());
        assertEquals(trigger.getType(), triggers.get(0).getType());
    }

    @Test
    public void testUpdateLists() {
        List<ActionScheduleInfo> schedules = createSchedules(20);

        List<ActionSchedule> inserted = dataManager.insertSchedules(schedules);
        List<String> ids = new ArrayList<>();
        for (ActionSchedule schedule : inserted) {
            ids.add(schedule.getId());
        }

        Map<String, List<String>> updateMap = new HashMap<>();
        updateMap.put(AutomationDataManager.SCHEDULES_TO_INCREMENT_QUERY, ids);
        dataManager.updateLists(updateMap);

        for (ActionSchedule actionSchedule : dataManager.getSchedules()) {
            assertEquals(1, actionSchedule.getCount());
        }

        updateMap.clear();
        updateMap.put(AutomationDataManager.SCHEDULES_TO_DELETE_QUERY, ids);
        dataManager.updateLists(updateMap);
        assertEquals(0, dataManager.getSchedules().size());
    }

    private List<ActionScheduleInfo> createSchedules(int amount) {
        List<ActionScheduleInfo> schedules = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            Trigger foreground = Triggers.newForegroundTriggerBuilder()
                                      .setGoal(10)
                                      .build();

            Trigger background = Triggers.newBackgroundTriggerBuilder()
                                      .setGoal(3)
                                      .build();

            ActionScheduleInfo schedule = ActionScheduleInfo.newBuilder()
                                                            .setGroup("group " + i)
                                                            .setStart(System.currentTimeMillis())
                                                            .setEnd(System.currentTimeMillis() + 100000)
                                                            .setLimit(100)
                                                            .addAction("test_action", JsonValue.wrap("action_value"))
                                                            .addTrigger(foreground)
                                                            .addTrigger(background)
                                                            .build();
            schedules.add(schedule);
        }

        return schedules;
    }
    
}
