/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.urbanairship.AirshipService;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import java.util.concurrent.Executor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobDispatcherTest extends BaseTestCase {

    private Job job;
    private JobDispatcher dispatcher;
    private ComponentName airshipServiceComponentName;
    private Scheduler mockScheduler;
    private PushManager mockPushManager;
    @Before
    public void setup() {
        mockScheduler = mock(Scheduler.class);

        job = Job.newBuilder()
                 .setAction("test_action")
                 .setTag("tag")
                 .setAirshipComponent(PushManager.class)
                 .putExtra("custom key", "custom value")
                 .build();

        dispatcher = new JobDispatcher(TestApplication.getApplication(), mockScheduler);
        dispatcher.executor = new Executor() {
            @Override
            public void execute(@NonNull Runnable runnable) {
                runnable.run();
            }
        };

        airshipServiceComponentName = new ComponentName(TestApplication.getApplication(), AirshipService.class);
    }

    @Test
    public void testDispatch() {
        when(mockScheduler.requiresScheduling(any(Context.class), eq(job))).thenReturn(false);

        dispatcher.dispatch(job);

        Intent intent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals(airshipServiceComponentName, intent.getComponent());
        assertEquals(AirshipService.ACTION_RUN_JOB, intent.getAction());
        assertBundlesEquals(job.toBundle(), intent.getBundleExtra(AirshipService.EXTRA_JOB_BUNDLE));
    }

    @Test
    public void testWakefulDispatch() throws SchedulerException {
        dispatcher.wakefulDispatch(job);

        Intent intent = ShadowApplication.getInstance().getNextStartedService();
        assertEquals(airshipServiceComponentName, intent.getComponent());
        assertEquals(AirshipService.ACTION_RUN_JOB, intent.getAction());
        assertBundlesEquals(job.toBundle(), intent.getBundleExtra(AirshipService.EXTRA_JOB_BUNDLE));

        // Verify it has a wakelock ID set by WakefulBroadcastReceiver.startWakefulService(Context, Intent)
        assertTrue(intent.getExtras().containsKey("android.support.content.wakelockid"));

        // Should cancel the job's tag
        verify(mockScheduler).cancel(any(Context.class), eq("tag"));
    }

    @Test
    public void testScheduleJob() throws SchedulerException {
        when(mockScheduler.requiresScheduling(any(Context.class), eq(job))).thenReturn(true);
        dispatcher.dispatch(job);
        verify(mockScheduler).schedule(any(Context.class), eq(job));
    }

    @Test
    public void testCancel() throws SchedulerException {
        dispatcher.cancel("tag");
        verify(mockScheduler).cancel(any(Context.class), eq("tag"));
    }
}