/* Copyright Airship and Contributors */

package com.urbanairship.automation.alarms;

import android.app.PendingIntent;

import com.urbanairship.TestClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(AndroidJUnit4.class)
public class AlarmOperationSchedulerTest {

    private AlarmOperationScheduler.AlarmManagerDelegate mockDelegate;
    private AlarmOperationScheduler scheduler;
    private TestClock testClock;

    @Before
    public void before() {
        this.testClock = new TestClock();
        this.mockDelegate = mock(AlarmOperationScheduler.AlarmManagerDelegate.class);
        this.scheduler = new AlarmOperationScheduler(ApplicationProvider.getApplicationContext(), testClock, mockDelegate);
    }

    @Test
    public void testSchedule() {
        testClock.elapsedRealtime = 1;
        scheduler.schedule(1000, mock(Runnable.class));

        verify(mockDelegate).onSchedule(eq(1001l), any(PendingIntent.class));

        scheduler.schedule(100000, mock(Runnable.class));

        verify(mockDelegate, times(2)).onSchedule(eq(1001l), any(PendingIntent.class));

        scheduler.schedule(20, mock(Runnable.class));

        verify(mockDelegate).onSchedule(eq(21l), any(PendingIntent.class));
    }

    @Test
    public void testExecute() {
        testClock.elapsedRealtime = 0;

        Runnable operation1 = mock(Runnable.class);
        scheduler.schedule(1000, operation1);

        Runnable operation2 = mock(Runnable.class);
        scheduler.schedule(20, operation2);

        Runnable operation3 = mock(Runnable.class);
        scheduler.schedule(100000, operation3);

        clearInvocations(mockDelegate);

        testClock.elapsedRealtime = 20;
        scheduler.onAlarmFired();

        verify(operation2).run();
        verifyNoInteractions(operation1, operation3);
        verify(mockDelegate).onSchedule(eq(1000l), any(PendingIntent.class));

        testClock.elapsedRealtime = 100000;
        scheduler.onAlarmFired();

        verifyNoMoreInteractions(mockDelegate);
        verify(operation1).run();
        verify(operation3).run();
    }

}
