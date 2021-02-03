/* Copyright Airship and Contributors */

package com.urbanairship.job;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class JobDispatcherTest extends BaseTestCase {

    private JobInfo jobInfo;
    private JobDispatcher dispatcher;
    private Scheduler mockScheduler;
    private TestApplication context;

    @Before
    public void setup() {
        mockScheduler = mock(Scheduler.class);
        context = TestApplication.getApplication();

        dispatcher = new JobDispatcher(TestApplication.getApplication(), mockScheduler);

        jobInfo = JobInfo.newBuilder()
                         .setAction("test_action")
                         .setAirshipComponent(PushManager.class)
                         .setExtras(JsonMap.newBuilder()
                                           .put("custom key", "custom value")
                                           .build())
                         .build();
    }

    @Test
    public void testDispatch() throws SchedulerException {
        dispatcher.dispatch(jobInfo);
        verify(mockScheduler).schedule(context, jobInfo);
    }

}
