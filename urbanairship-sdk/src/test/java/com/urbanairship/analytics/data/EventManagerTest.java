package com.urbanairship.analytics.data;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.location.RegionEvent;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import java.util.HashMap;
import java.util.Map;

import static com.urbanairship.analytics.data.EventManager.MIN_BATCH_INTERVAL_KEY;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class EventManagerTest extends BaseTestCase {
    private EventManager eventManager;
    private EventApiClient mockClient;
    private EventResolver mockEventResolver;
    private JobDispatcher mockDispatcher;
    private ActivityMonitor mockActivityMonitor;
    private PreferenceDataStore dataStore;

    @Before
    public void setUp() {
        mockDispatcher = mock(JobDispatcher.class);
        mockEventResolver = mock(EventResolver.class);
        mockClient = mock(EventApiClient.class);
        mockActivityMonitor = mock(ActivityMonitor.class);

        dataStore = TestApplication.getApplication().preferenceDataStore;

        eventManager = new EventManager.Builder()
                .setEventResolver(mockEventResolver)
                .setActivityMonitor(mockActivityMonitor)
                .setApiClient(mockClient)
                .setPreferenceDataStore(dataStore)
                .setJobDispatcher(mockDispatcher)
                .setBackgroundReportingIntervalMS(400)
                .setJobAction("upload")
                .build();
    }

    /**
     * Tests adding an event after the next send time schedules an upload with a 10 second delay.
     */
    @Test
    public void testAddEventAfterNextSendTime() {
        CustomEvent customEvent = new CustomEvent.Builder("event name").create();
        eventManager.addEvent(customEvent, "session");

        // Verify we add an event.
        verify(mockEventResolver, new Times(1)).insertEvent(customEvent, "session");

        // Check it schedules an upload
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("upload") && jobInfo.getInitialDelay() == 10000L;
            }
        }));
    }

    /**
     * Tests adding an event  before the next send time schedules an upload with the remaining delay.
     */
    @Test
    public void testAddEventBeforeNextSendTime() {
        // Set the last send time to the current time so the next send time is minBatchInterval
        dataStore.put(EventManager.LAST_SEND_KEY, System.currentTimeMillis());

        // Set the minBatchInterval to 20 seconds
        dataStore.put(MIN_BATCH_INTERVAL_KEY, 20000);

        CustomEvent customEvent = new CustomEvent.Builder("event name").create();
        eventManager.addEvent(customEvent, "session");

        // Check it schedules an upload with a time greater than 10 seconds
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("upload") && jobInfo.getInitialDelay() > 10000;
            }
        }));
    }


    /**
     * Tests sending events
     */
    @Test
    public void testSendingEvents() {
        Map<String, String> events = new HashMap<>();
        events.put("firstEvent", "{ 'firstEventBody' }");

        // Set up data manager to return 2 count for events.
        // Note: we only have one event, but it should only ask for one to upload
        // having it return 2 will make it schedule to upload events in the future
        when(mockEventResolver.getEventCount()).thenReturn(2);

        // Return 200 bytes in size.  It should only be able to do 100 bytes so only
        // the first event.
        when(mockEventResolver.getDatabaseSize()).thenReturn(200);

        // Return the event when it asks for 1
        when(mockEventResolver.getEvents(1)).thenReturn(events);

        // Set the max batch size to 100
        dataStore.put(EventManager.MAX_BATCH_SIZE_KEY, 100);

        // Set up the response
        EventResponse response = mock(EventResponse.class);
        when(response.getStatus()).thenReturn(200);
        when(response.getMaxTotalSize()).thenReturn(200);
        when(response.getMaxBatchSize()).thenReturn(300);
        when(response.getMinBatchInterval()).thenReturn(100);

        // Return the response
        when(mockClient.sendEvents(UAirship.shared(), events.values())).thenReturn(response);

        // Start the upload process
        assertTrue(eventManager.uploadEvents(UAirship.shared()));

        // Check mockClients receives the events
        verify(mockClient).sendEvents(UAirship.shared(), events.values());

        // Check data manager deletes events
        verify(mockEventResolver).deleteEvents(events.keySet());

        // Verify responses are being saved
        assertEquals(200, dataStore.getInt(EventManager.MAX_TOTAL_DB_SIZE_KEY, 0));
        assertEquals(300, dataStore.getInt(EventManager.MAX_BATCH_SIZE_KEY, 0));
        assertEquals(100, dataStore.getInt(EventManager.MIN_BATCH_INTERVAL_KEY, 0));

        // Check it schedules an upload
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("upload");
            }
        }));
    }

    /**
     * Test event batching only sends a max of 500 events.
     */
    @Test
    public void testSendEventMaxCount() {
        // Make the match batch size greater than 500
        dataStore.put(EventManager.MAX_BATCH_SIZE_KEY, 100000);

        // Fake the resolver to act like it has more than 500 events
        when(mockEventResolver.getDatabaseSize()).thenReturn(100000);
        when(mockEventResolver.getEventCount()).thenReturn(1000);

        eventManager.uploadEvents(UAirship.shared());

        // Verify it only asked for 500
        verify(mockEventResolver).getEvents(500);
    }

    /**
     * Test sending events when the upload fails.
     */
    @Test
    public void testSendEventsFails() {
        Map<String, String> events = new HashMap<>();
        events.put("firstEvent", "{ 'firstEventBody' }");
        when(mockEventResolver.getEventCount()).thenReturn(1);
        when(mockEventResolver.getDatabaseSize()).thenReturn(100);
        when(mockEventResolver.getEvents(1)).thenReturn(events);

        dataStore.put(EventManager.MAX_BATCH_SIZE_KEY, 100);


        // Start the upload process
        when(mockClient.sendEvents(UAirship.shared(), events.values())).thenReturn(null);

        assertFalse(eventManager.uploadEvents(UAirship.shared()));

        // Check mockClient receives the events
        verify(mockClient).sendEvents(UAirship.shared(), events.values());

        // If it fails, it should skip deleting events
        verify(mockEventResolver, never()).deleteEvents(events.keySet());
    }

    /**
     * Test adding a region event schedules an upload immediately.
     */
    @Test
    public void testAddingHighPriorityEvents() {
        // Set last send time to year 3005 so next send time is way in the future
        dataStore.put(EventManager.LAST_SEND_KEY, 32661446400000L);

        RegionEvent regionEvent = new RegionEvent("id", "source", RegionEvent.BOUNDARY_EVENT_ENTER);

        eventManager.addEvent(regionEvent, "session");

        // Check it schedules an upload
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals("upload") && jobInfo.getInitialDelay() == 0;
            }
        }));
    }

    /**
     * Test delete all.
     */
    @Test
    public void testDeleteAll() {
        eventManager.deleteEvents();
        verify(mockEventResolver).deleteAllEvents();
    }
}
