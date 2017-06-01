/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.urbanairship.AirshipService;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

public class AlarmSchedulerTest extends BaseTestCase {

    private AlarmScheduler scheduler;
    private Context context;

    @Before
    public void setup() {
        scheduler = new AlarmScheduler();
        context = TestApplication.getApplication();
    }

    @Test
    public void testRequiresScheduling() {
        Job jobWithDelay = Job.newBuilder().setInitialDelay(1, TimeUnit.MILLISECONDS).build();
        assertTrue(scheduler.requiresScheduling(context, jobWithDelay));

        Job job = Job.newBuilder().build();
        assertFalse(scheduler.requiresScheduling(context, job));
    }

    @Test
    public void testSchedule() throws SchedulerException {
        Job job = Job.newBuilder().setInitialDelay(1, TimeUnit.MILLISECONDS).build();

        scheduler.schedule(context, job);
        verifyScheduledJob(job, 1);
    }

    @Test
    public void testScheduleWithTag() throws SchedulerException {
        Job job = Job.newBuilder()
                     .setInitialDelay(1, TimeUnit.MILLISECONDS)
                     .setTag("tag")
                     .build();

        scheduler.schedule(context, job);
        verifyScheduledJob(job, 1);
    }

    @Test
    public void testReschedule() throws SchedulerException {
        Job job = Job.newBuilder().setInitialDelay(1, TimeUnit.MILLISECONDS).build();

        // Check 10 retries. The delay should double each time
        long delay = 10000;
        for (int i = 0; i < 10; i++) {
            scheduler.reschedule(context, job);
            verifyScheduledJob(job, delay);
            delay = delay * 2;
        }
    }

    private void verifyScheduledJob(Job job, long delay) {
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull(alarm);

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(alarm.operation);
        assertTrue(shadowPendingIntent.isServiceIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals(new ComponentName(context, AirshipService.class), intent.getComponent());
        assertEquals(AirshipService.ACTION_RUN_JOB, intent.getAction());
        assertBundlesEquals(job.toBundle(), intent.getBundleExtra(AirshipService.EXTRA_JOB_BUNDLE));

        if (job.getTag() != null) {
            assertEquals("tag", intent.getCategories().iterator().next());
        } else {
            assertEquals(1, intent.getCategories().size());
        }

        long expectedTriggerTime = SystemClock.elapsedRealtime() + delay;
        // Verify the alarm is within 100 milliseconds
        assertTrue(expectedTriggerTime - alarm.triggerAtTime <= 100);
    }
}