/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.data.EventManager;
import com.urbanairship.job.JobInfo;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class AnalyticsJobHandlerTest extends BaseTestCase {

    private AnalyticsJobHandler jobHandler;
    private PushManager mockPushManager;
    private Analytics mockAnalytics;
    private EventManager mockEventManager;
    private String channelId;

    @Before
    public void setUp() {
        mockPushManager = mock(PushManager.class);
        mockEventManager = mock(EventManager.class);
        mockAnalytics = mock(Analytics.class);

        Mockito.when(mockPushManager.getChannelId()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return channelId;
            }
        });

        TestApplication.getApplication().setAnalytics(mockAnalytics);
        TestApplication.getApplication().setPushManager(mockPushManager);

        channelId = "some channel ID";
        jobHandler = new AnalyticsJobHandler(TestApplication.getApplication(), UAirship.shared(), mockEventManager);
    }

    /**
     * Tests sending events
     */
    @Test
    public void testSendingEvents() {
        when(mockAnalytics.isEnabled()).thenReturn(true);
        when(mockEventManager.uploadEvents(UAirship.shared())).thenReturn(true);

        // Start the upload process
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(Analytics.ACTION_SEND)
                                 .build();
        
        assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));
        Mockito.verify(mockEventManager).uploadEvents(UAirship.shared());
    }

    /**
     * Test sending events when there's no channel ID present
     */
    @Test
    public void testSendingWithNoChannelID() {
        when(mockAnalytics.isEnabled()).thenReturn(true);

        // Return null when channel ID is expected
        channelId = null;

        // Start the upload process
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(Analytics.ACTION_SEND)
                                 .build();

        assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        // Verify uploadEvents is not called
        Mockito.verify(mockEventManager, never()).uploadEvents(UAirship.shared());
    }

    /**
     * Test sending events when analytics is disabled.
     */
    @Test
    public void testSendingWithAnalyticsDisabled() {
        when(mockAnalytics.isEnabled()).thenReturn(false);

        // Start the upload process
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(Analytics.ACTION_SEND)
                                 .build();

        assertEquals(JobInfo.JOB_FINISHED, jobHandler.performJob(jobInfo));

        Mockito.verify(mockEventManager, never()).uploadEvents(UAirship.shared());
    }

    /**
     * Test sending events when the upload fails
     */
    @Test
    public void testSendEventsFails() {
        when(mockAnalytics.isEnabled()).thenReturn(true);
        when(mockEventManager.uploadEvents(UAirship.shared())).thenReturn(false);

        // Start the upload process
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(Analytics.ACTION_SEND)
                                 .build();

        assertEquals(JobInfo.JOB_RETRY, jobHandler.performJob(jobInfo));
    }
}
