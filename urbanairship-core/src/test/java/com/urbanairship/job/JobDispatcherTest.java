/* Copyright Airship and Contributors */

package com.urbanairship.job;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushManager;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class JobDispatcherTest extends BaseTestCase {

    private final RateLimiter mockRateLimiter = mock(RateLimiter.class);
    private final TestJobRunner jobRunner = new TestJobRunner();
    private final Scheduler mockScheduler = mock(Scheduler.class);
    private final TestApplication context = TestApplication.getApplication();
    private final JobDispatcher dispatcher = new JobDispatcher(context, mockScheduler, jobRunner, mockRateLimiter);
    private final Consumer<JobResult> mockConsumer = mock(Consumer.class);

    @Test
    public void testDispatch() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction("test_action")
                                 .setAirshipComponent(PushManager.class)
                                 .setMinDelay(10, TimeUnit.MILLISECONDS)
                                 .build();

        dispatcher.dispatch(jobInfo);
        verify(mockScheduler).schedule(context, jobInfo, 10);
    }

    @Test
    public void testStartJob() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction("test_action")
                                 .setAirshipComponent(PushManager.class)
                                 .build();


        jobRunner.result = JobResult.FAILURE;

        dispatcher.onStartJob(jobInfo, 1, mockConsumer);
        verify(mockConsumer).accept(JobResult.FAILURE);
        verifyNoInteractions(mockScheduler);
        assertEquals(jobInfo, jobRunner.lastJob);
    }

    @Test
    public void testMaxRetries() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction("test_action")
                                 .setAirshipComponent(PushManager.class)
                                 .build();

        jobRunner.result = JobResult.RETRY;

        dispatcher.onStartJob(jobInfo, 4, mockConsumer);
        verify(mockConsumer).accept(JobResult.RETRY);
        verifyNoInteractions(mockScheduler);

        dispatcher.onStartJob(jobInfo, 5, mockConsumer);
        verify(mockConsumer).accept(JobResult.FAILURE);
        verify(mockScheduler).schedule(context, jobInfo, JobDispatcher.RESCHEDULE_RETRY_DELAY_MS);
    }

    @Test
    public void testSetRateLimit() {
        dispatcher.setRateLimit("foo", 19, 100, TimeUnit.DAYS);
        verify(mockRateLimiter).setLimit("foo", 19, 100, TimeUnit.DAYS);
    }

    @Test
    public void testDispatchRateLimit() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction("test_action")
                                 .setAirshipComponent(PushManager.class)
                                 .setMinDelay(10, TimeUnit.MILLISECONDS)
                                 .addRateLimit("rateOne")
                                 .addRateLimit("rateTwo")
                                 .build();

        when(mockRateLimiter.status("rateOne")).thenReturn(new RateLimiter.Status(RateLimiter.LimitStatus.OVER, 100));
        when(mockRateLimiter.status("rateTwo")).thenReturn(new RateLimiter.Status(RateLimiter.LimitStatus.OVER, 300));

        dispatcher.dispatch(jobInfo);
        verify(mockScheduler).schedule(context, jobInfo, 300);
    }

    @Test
    public void testStartJobTracksRateLimits() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction("test_action")
                                 .setAirshipComponent(PushManager.class)
                                 .setMinDelay(10, TimeUnit.MILLISECONDS)
                                 .addRateLimit("rateOne")
                                 .addRateLimit("rateTwo")
                                 .build();

        dispatcher.onStartJob(jobInfo, 4, mockConsumer);
        verify(mockRateLimiter).track("rateOne");
        verify(mockRateLimiter).track("rateTwo");
    }

    @Test
    public void testStartJobOverRateLimit() throws SchedulerException {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction("test_action")
                                 .setAirshipComponent(PushManager.class)
                                 .setMinDelay(10, TimeUnit.MILLISECONDS)
                                 .addRateLimit("rateOne")
                                 .addRateLimit("rateTwo")
                                 .build();

        when(mockRateLimiter.status("rateOne")).thenReturn(new RateLimiter.Status(RateLimiter.LimitStatus.OVER, 100));
        when(mockRateLimiter.status("rateTwo")).thenReturn(new RateLimiter.Status(RateLimiter.LimitStatus.UNDER, 0));

        dispatcher.onStartJob(jobInfo, 4, mockConsumer);
        verify(mockConsumer).accept(JobResult.FAILURE);
        verify(mockRateLimiter, never()).track("rateOne");
        verify(mockRateLimiter, never()).track("rateTwo");
        assertNull(jobRunner.lastJob);

        verify(mockScheduler).schedule(context, jobInfo, 100);
    }

    private static class TestJobRunner implements JobRunner {

        public JobResult result = JobResult.SUCCESS;
        public JobInfo lastJob;
        @Override
        public void run(@NonNull JobInfo jobInfo, @NonNull Consumer<JobResult> resultConsumer) {
            lastJob = jobInfo;
            resultConsumer.accept(result);
        }
    }
}
