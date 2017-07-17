/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class JobDispatcherTest extends BaseTestCase {

    private JobInfo jobInfo;
    private JobDispatcher dispatcher;
    private Scheduler mockScheduler;
    private Scheduler mockFallbackScheduler;
    private TestActivityMonitor activityMonitor = new TestActivityMonitor();
    private TestApplication context;

    @Before
    public void setup() {
        mockScheduler = mock(Scheduler.class);
        mockFallbackScheduler = mock(Scheduler.class);
        context = TestApplication.getApplication();


        dispatcher = new JobDispatcher(TestApplication.getApplication(), new JobDispatcher.SchedulerFactory() {
            @NonNull
            @Override
            public Scheduler createScheduler(Context context) {
                return mockScheduler;
            }

            @NonNull
            @Override
            public Scheduler createFallbackScheduler(Context context) {
                return mockFallbackScheduler;
            }
        }, activityMonitor);

        jobInfo = JobInfo.newBuilder()
                         .setAction("test_action")
                         .setId(JobInfo.NAMED_USER_UPDATE_ID)
                         .setAirshipComponent(PushManager.class)
                         .setExtras(JsonMap.newBuilder()
                                           .put("custom key", "custom value")
                                           .build())
                         .build();
    }

    @Test
    public void testDispatch() throws SchedulerException {
        dispatcher.dispatch(jobInfo);
        verify(mockScheduler).schedule(context,jobInfo, 3000002);
    }

    @Test
    public void testDispatchImmediately() {
        activityMonitor.startActivity();
        dispatcher.dispatch(jobInfo);

        Intent intent = ShadowApplication.getInstance().getNextStartedService();

        Intent expectedIntent = AirshipService.createIntent(context, jobInfo, null);
        assertEquals(expectedIntent.getAction(), intent.getAction());
        assertEquals(expectedIntent.getComponent(), intent.getComponent());
        assertBundlesEquals(expectedIntent.getExtras(), intent.getExtras());
    }

    @Test
    public void testDispatchFallback() throws SchedulerException {
        doThrow(new SchedulerException("test"))
                .when(mockScheduler)
                .schedule(context, jobInfo, 3000002);

        dispatcher.dispatch(jobInfo);
        verify(mockFallbackScheduler).schedule(context,jobInfo, 3000002);
    }

    @Test
    public void testReschedule() throws SchedulerException {
        Bundle bundle = new Bundle();
        bundle.putString("key", "value");

        dispatcher.reschedule(jobInfo, bundle);
        verify(mockScheduler).reschedule(context, jobInfo, 3000002, bundle);
    }

    @Test
    public void testRescheduleFallback() throws SchedulerException {
        doThrow(new SchedulerException("test"))
                .when(mockScheduler)
                .reschedule(context, jobInfo, 3000002, null);

        dispatcher.reschedule(jobInfo, null);
        verify(mockFallbackScheduler).reschedule(context, jobInfo, 3000002, null);
    }

    @Test
    public void testCancel() throws SchedulerException {
        dispatcher.cancel(JobInfo.RICH_PUSH_UPDATE_USER);
        verify(mockScheduler).cancel(context, 3000007);
    }

    @Test
    public void testCancelFallback() throws SchedulerException {
        doThrow(new SchedulerException("test"))
                .when(mockScheduler)
                .cancel(context, 3000007);

        dispatcher.cancel(JobInfo.RICH_PUSH_UPDATE_USER);
        verify(mockFallbackScheduler).cancel(context, 3000007);
    }
}