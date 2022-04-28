package com.urbanairship.analytics.data;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestApplication;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.analytics.location.RegionEvent;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private EventDao mockEventDao;
    private JobDispatcher mockDispatcher;
    private ActivityMonitor mockActivityMonitor;
    private PreferenceDataStore dataStore;

    private TestAirshipRuntimeConfig testAirshipRuntimeConfig;

    @Before
    public void setUp() {
        mockDispatcher = mock(JobDispatcher.class);
        mockEventDao = mock(EventDao.class);

        testAirshipRuntimeConfig = TestAirshipRuntimeConfig.newTestConfig();

        mockClient = mock(EventApiClient.class);
        mockActivityMonitor = mock(ActivityMonitor.class);

        dataStore = TestApplication.getApplication().preferenceDataStore;

        eventManager = new EventManager(dataStore, testAirshipRuntimeConfig, mockDispatcher, mockActivityMonitor, mockEventDao, mockClient);
    }

    /**
     * Tests adding an event after the next send time schedules an upload with a 10 second delay.
     */
    @Test
    public void testAddEventAfterNextSendTime() throws JsonException {
        CustomEvent customEvent = CustomEvent.newBuilder("event name").build();
        EventEntity entity = EventEntity.create(customEvent, "session");

        eventManager.addEvent(customEvent, "session");
        // Verify we add an event.
        verify(mockEventDao, new Times(1)).insert(entity);

        // Check it schedules an upload
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(EventManager.ACTION_SEND) && jobInfo.getMinDelayMs() == 10000L;
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

        CustomEvent customEvent = CustomEvent.newBuilder("event name").build();
        eventManager.addEvent(customEvent, "session");

        // Check it schedules an upload with a time greater than 10 seconds
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(EventManager.ACTION_SEND) && jobInfo.getMinDelayMs() > 10000;
            }
        }));
    }

    /**
     * Tests sending events
     */
    @Test
    public void testSendingEvents() throws RequestException, JsonException {
        JsonValue data = JsonValue.parseString("{ \"body\": \"firstEventBody\" }");
        EventEntity.EventIdAndData payload = new EventEntity.EventIdAndData(1, "firstEvent", data);
        List<EventEntity.EventIdAndData> events = Collections.singletonList(payload);
        List<JsonValue> eventPayloads = Collections.singletonList(payload.data);

        Map<String, String> headers = new HashMap<>();
        headers.put("foo", "bar");

        // Set up data manager to return 2 count for events.
        // Note: we only have one event, but it should only ask for one to upload
        // having it return 2 will make it schedule to upload events in the future
        when(mockEventDao.count()).thenReturn(2);

        // Return 200 bytes in size.  It should only be able to do 100 bytes so only
        // the first event.
        when(mockEventDao.databaseSize()).thenReturn(200);

        // Return the event when it asks for 1
        when(mockEventDao.getBatch(1)).thenReturn(events);

        // Set the max batch size to 100
        dataStore.put(EventManager.MAX_BATCH_SIZE_KEY, 100);

        // Set up the response
        EventResponse eventResponse = mock(EventResponse.class);
        when(eventResponse.getMaxTotalSize()).thenReturn(200);
        when(eventResponse.getMaxBatchSize()).thenReturn(300);
        when(eventResponse.getMinBatchInterval()).thenReturn(100);

        // Return the response
        when(mockClient.sendEvents(eventPayloads, headers))
                .thenReturn(new Response.Builder<EventResponse>(HttpURLConnection.HTTP_OK)
                        .setResult(eventResponse)
                        .build());

        // Start the upload process
        assertTrue(eventManager.uploadEvents(headers));

        // Check mockClients receives the events
        verify(mockClient).sendEvents(eventPayloads, headers);

        // Check data manager deletes events
        verify(mockEventDao).deleteBatch(events);

        // Verify responses are being saved
        assertEquals(200, dataStore.getInt(EventManager.MAX_TOTAL_DB_SIZE_KEY, 0));
        assertEquals(300, dataStore.getInt(EventManager.MAX_BATCH_SIZE_KEY, 0));
        assertEquals(100, dataStore.getInt(EventManager.MIN_BATCH_INTERVAL_KEY, 0));

        // Check it schedules an upload
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(EventManager.ACTION_SEND);
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
        when(mockEventDao.databaseSize()).thenReturn(100000);
        when(mockEventDao.count()).thenReturn(1000);

        eventManager.uploadEvents(Collections.<String, String>emptyMap());

        // Verify it only asked for 500
        verify(mockEventDao).getBatch(500);
    }

    /**
     * Test sending events when the upload fails.
     */
    @Test
    public void testSendEventsFails() throws RequestException, JsonException {
        JsonValue data = JsonValue.parseString("{ \"body\": \"firstEventBody\" }");
        EventEntity.EventIdAndData payload = new EventEntity.EventIdAndData(1, "firstEvent", data);
        List<EventEntity.EventIdAndData> events = Collections.singletonList(payload);
        List<JsonValue> eventPayloads = Collections.singletonList(payload.data);

        Map<String, String> headers = new HashMap<>();
        headers.put("foo", "bar");

        when(mockEventDao.count()).thenReturn(1);
        when(mockEventDao.databaseSize()).thenReturn(100);
        when(mockEventDao.getBatch(1)).thenReturn(events);

        dataStore.put(EventManager.MAX_BATCH_SIZE_KEY, 100);

        EventResponse eventResponse = mock(EventResponse.class);

        when(mockClient.sendEvents(eventPayloads, headers))
                .thenReturn(new Response.Builder<EventResponse>(HttpURLConnection.HTTP_BAD_REQUEST)
                        .setResult(eventResponse)
                        .build());

        assertFalse(eventManager.uploadEvents(headers));

        // Check mockClient receives the events
        verify(mockClient).sendEvents(eventPayloads, headers);

        // If it fails, it should skip deleting events
        verify(mockEventDao, never()).deleteBatch(events);
    }

    /**
     * Test adding a region event schedules an upload immediately.
     */
    @Test
    public void testAddingHighPriorityEvents() {
        // Set last send time to year 3005 so next send time is way in the future
        dataStore.put(EventManager.LAST_SEND_KEY, 32661446400000L);

        RegionEvent regionEvent = RegionEvent.newBuilder()
                                             .setRegionId("id")
                                             .setSource("source")
                                             .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_ENTER)
                                             .build();

        eventManager.addEvent(regionEvent, "session");

        // Check it schedules an upload
        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(EventManager.ACTION_SEND) && jobInfo.getMinDelayMs() == 0;
            }
        }));
    }

    /**
     * Test delete all.
     */
    @Test
    public void testDeleteAll() {
        eventManager.deleteEvents();
        verify(mockEventDao).deleteAll();
    }
}
