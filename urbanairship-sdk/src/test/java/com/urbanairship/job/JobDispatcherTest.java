package com.urbanairship.job;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.urbanairship.AirshipService;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

public class JobDispatcherTest extends BaseTestCase {

    private Job job;
    private JobDispatcher dispatcher;
    private ComponentName airshipServiceComponentName;

    @Before
    public void setup() {
        job = Job.newBuilder("test_action")
                 .setAirshipComponent(PushManager.class)
                 .putExtra("custom key", "custom value")
                 .build();

        dispatcher = new JobDispatcher(TestApplication.getApplication());

        airshipServiceComponentName = new ComponentName(TestApplication.getApplication(), AirshipService.class);
    }

    @Test
    public void testDispatch() throws Exception {
        dispatcher.dispatch(job);

        Intent intent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals(airshipServiceComponentName, intent.getComponent());
        assertEquals(job.getAction(), intent.getAction());
        assertEquals(job.getExtras(), intent.getBundleExtra(AirshipService.EXTRA_JOB_EXTRAS));
        assertEquals(job.getAirshipComponentName(), intent.getStringExtra(AirshipService.EXTRA_AIRSHIP_COMPONENT));
        assertEquals(0, intent.getLongExtra(AirshipService.EXTRA_DELAY, 0));
    }

    @Test
    public void testDispatchWithDelay() throws Exception {
        dispatcher.dispatch(job, 300L, TimeUnit.MILLISECONDS);

        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull("Alarm should be schedule for more uploads", alarm);

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(alarm.operation);
        assertTrue(shadowPendingIntent.isServiceIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals(airshipServiceComponentName, intent.getComponent());
        assertEquals(job.getAction(), intent.getAction());
        assertEquals(job.getExtras(), intent.getBundleExtra(AirshipService.EXTRA_JOB_EXTRAS));
        assertEquals(job.getAirshipComponentName(), intent.getStringExtra(AirshipService.EXTRA_AIRSHIP_COMPONENT));
        assertEquals(300L, intent.getLongExtra(AirshipService.EXTRA_DELAY, 0));
    }

    @Test
    public void testWakefulDispatch() throws Exception {
        dispatcher.wakefulDispatch(job);

        Intent intent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals(airshipServiceComponentName, intent.getComponent());
        assertEquals(job.getAction(), intent.getAction());
        assertEquals(job.getExtras(), intent.getBundleExtra(AirshipService.EXTRA_JOB_EXTRAS));
        assertEquals(job.getAirshipComponentName(), intent.getStringExtra(AirshipService.EXTRA_AIRSHIP_COMPONENT));
        assertEquals(0, intent.getLongExtra(AirshipService.EXTRA_DELAY, 0));

        // Verify it has a wakelock ID set by WakefulBroadcastReceiver.startWakefulService(Context, Intent)
        assertTrue(intent.getExtras().containsKey("android.support.content.wakelockid"));
    }

    @Test
    public void testCancel() throws Exception {
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);

        dispatcher.dispatch(job, 100L, TimeUnit.DAYS);
        assertFalse(shadowAlarmManager.getScheduledAlarms().isEmpty());

        dispatcher.cancel(job.getAction());
        assertTrue(shadowAlarmManager.getScheduledAlarms().isEmpty());
    }
}