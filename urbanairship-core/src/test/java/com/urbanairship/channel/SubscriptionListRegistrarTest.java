package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAHttpStatusUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SubscriptionListRegistrarTest extends BaseTestCase {

    private PendingSubscriptionListMutationStore store;
    private SubscriptionListApiClient client;
    private SubscriptionListRegistrar registrar;

    @Before
    public void setup() {
        client = mock(SubscriptionListApiClient.class);
        store = new PendingSubscriptionListMutationStore(TestApplication.getApplication().preferenceDataStore, "test");
        registrar = new SubscriptionListRegistrar(client, store);
    }

    @Test
    public void testSetId() {
        registrar.setId(null, false);

        SubscriptionListMutation mutation = SubscriptionListMutation.newSubscribeMutation("foo", 0L);
        List<SubscriptionListMutation> pendingMutations = Collections.singletonList(mutation);

        registrar.addPendingMutations(pendingMutations);

        registrar.setId("some-id", false);
        assertEquals(pendingMutations, store.peek());

        registrar.setId("some-id", true);
        assertEquals(pendingMutations, store.peek());

        registrar.setId("another-id", true);
        assertNull(store.peek());
    }

    @Test
    public void testAdd() {
        SubscriptionListMutation mutation = SubscriptionListMutation.newSubscribeMutation("foo", 0L);
        List<SubscriptionListMutation> pendingMutations = Collections.singletonList(mutation);

        registrar.addPendingMutations(pendingMutations);

        assertEquals(pendingMutations, store.peek());
    }

    @Test
    public void testClear() {
        SubscriptionListMutation mutation = SubscriptionListMutation.newSubscribeMutation("foo", 0L);
        List<SubscriptionListMutation> pendingMutations = Collections.singletonList(mutation);

        registrar.addPendingMutations(pendingMutations);
        assertFalse(store.getList().isEmpty());

        registrar.clearPendingMutations();
        assertTrue(store.getList().isEmpty());
    }

    @Test
    public void testUpload200Response() throws RequestException {
        verifyRequest(200, true);
    }

    @Test
    public void testUpload202Response() throws RequestException {
        verifyRequest(202, true);
    }

    @Test
    public void testUpload500Response() throws RequestException {
        verifyRequest(500, false);
    }

    @Test
    public void testUpload429Response() throws RequestException {
        verifyRequest(429, false);
    }

    @Test
    public void testUpload400Response() throws RequestException {
        verifyRequest(400, true);
    }

    private void verifyRequest(int status, boolean expectedResult) throws RequestException {
        final Response<Void> response = new Response.Builder<Void>(status).build();

        SubscriptionListListener listener = mock(SubscriptionListListener.class);
        registrar.addSubscriptionListListener(listener);

        registrar.setId("identifier",true);

        SubscriptionListMutation mutation = SubscriptionListMutation.newSubscribeMutation("foo", 0L);
        List<SubscriptionListMutation> pendingMutations = Collections.singletonList(mutation);

        registrar.addPendingMutations(pendingMutations);

        when(client.updateSubscriptionLists("identifier", pendingMutations)).thenReturn(response);

        assertEquals(expectedResult, registrar.uploadPendingMutations());
        assertEquals(expectedResult, store.getList().isEmpty());

        if (UAHttpStatusUtil.inSuccessRange(status)) {
            verify(listener).onSubscriptionListMutationUploaded("identifier", pendingMutations);
        }
    }
}
