/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

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
    public void testSchedule() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction("Some action")
                                 .setInitialDelay(1, TimeUnit.MILLISECONDS)
                                 .build();

        scheduler.schedule(context, jobInfo, 20);
        verifyScheduledJob(jobInfo, 20, 1);
    }
    
    @Test
    public void testReschedule() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction("Some action")
                                 .build();

        Bundle extras = new Bundle();
        extras.putLong(AlarmScheduler.EXTRA_BACKOFF_DELAY, 1000);

        scheduler.reschedule(context, jobInfo, 30, extras);
        verifyScheduledJob(jobInfo, 30, 1000 * 2);
    }

    private void verifyScheduledJob(JobInfo jobInfo, int id, long delay) {
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull(alarm);

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(alarm.operation);
        assertTrue(shadowPendingIntent.isServiceIntent());
        assertEquals(id, shadowPendingIntent.getRequestCode());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals(new ComponentName(context, AirshipService.class), intent.getComponent());

        assertEquals(AirshipService.ACTION_RUN_JOB, intent.getAction());
        assertBundlesEquals(jobInfo.toBundle(), intent.getBundleExtra(AirshipService.EXTRA_JOB_INFO_BUNDLE));

        assertEquals(delay, intent.getBundleExtra(AirshipService.EXTRA_RESCHEDULE_EXTRAS).getLong(AlarmScheduler.EXTRA_BACKOFF_DELAY));

        long expectedTriggerTime = SystemClock.elapsedRealtime() + delay;
        // Verify the alarm is within 100 milliseconds
        assertTrue(expectedTriggerTime - alarm.triggerAtTime <= 100);
    }
}