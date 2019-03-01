package com.urbanairship.app;

import android.app.Activity;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.Predicate;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class FilteredActivityListenerTest extends BaseTestCase {

    FilteredActivityListener filteredActivityListener;
    Predicate<Activity> mockPredicate;
    ActivityListener mockListener;

    @Before
    public void setup() {
        //noinspection unchecked
        mockPredicate = (Predicate<Activity>) mock(Predicate.class);
        mockListener = mock(ActivityListener.class);

        filteredActivityListener = new FilteredActivityListener(mockListener, mockPredicate);
    }

    @Test
    public void testAcceptActivity() {
        Activity activity = new Activity();
        Bundle bundle = new Bundle();

        when(mockPredicate.apply(activity)).thenReturn(true);

        filteredActivityListener.onActivityCreated(activity, bundle);
        verify(mockListener).onActivityCreated(activity, bundle);

        filteredActivityListener.onActivityStarted(activity);
        verify(mockListener).onActivityStarted(activity);

        filteredActivityListener.onActivityResumed(activity);
        verify(mockListener).onActivityStarted(activity);

        filteredActivityListener.onActivityPaused(activity);
        verify(mockListener).onActivityStarted(activity);

        filteredActivityListener.onActivityStopped(activity);
        verify(mockListener).onActivityStarted(activity);

        filteredActivityListener.onActivitySaveInstanceState(activity, bundle);
        verify(mockListener).onActivitySaveInstanceState(activity, bundle);

        filteredActivityListener.onActivityDestroyed(activity);
        verify(mockListener).onActivityDestroyed(activity);
    }

    @Test
    public void testRejectActivity() {
        Activity activity = new Activity();
        Bundle bundle = new Bundle();

        when(mockPredicate.apply(activity)).thenReturn(false);

        filteredActivityListener.onActivityCreated(activity, bundle);
        filteredActivityListener.onActivityStarted(activity);
        filteredActivityListener.onActivityResumed(activity);
        filteredActivityListener.onActivityPaused(activity);
        filteredActivityListener.onActivityStopped(activity);
        filteredActivityListener.onActivitySaveInstanceState(activity, bundle);
        filteredActivityListener.onActivityDestroyed(activity);

        verifyZeroInteractions(mockListener);
    }

}
