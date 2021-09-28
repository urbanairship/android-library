package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AttributeRegistrarTest extends BaseTestCase {

    private PendingAttributeMutationStore store;
    private AttributeApiClient mockClient;
    private AttributeRegistrar registrar;

    @Before
    public void setup() {
        mockClient = mock(AttributeApiClient.class);
        store = new PendingAttributeMutationStore(TestApplication.getApplication().preferenceDataStore, "AttributeRegistrarTest");
        registrar = new AttributeRegistrar(mockClient, store);
    }

    @Test
    public void testSetId() {
        registrar.setId(null, false);

        AttributeMutation mutation = AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100);
        List<AttributeMutation> pendingAttributeMutations = Collections.singletonList(mutation);

        registrar.addPendingMutations(pendingAttributeMutations);

        registrar.setId("what", false);
        assertEquals(store.peek(), pendingAttributeMutations);

        registrar.setId("what", true);
        assertEquals(store.peek(), pendingAttributeMutations);

        registrar.setId("something-else", true);
        assertNull(store.peek());
    }

    @Test
    public void testAdd() {
        AttributeMutation mutation = AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100);
        List<AttributeMutation> pendingAttributeMutations = Collections.singletonList(mutation);
        registrar.addPendingMutations(pendingAttributeMutations);

        assertEquals(pendingAttributeMutations, store.peek());
    }

    @Test
    public void testClear() {
        AttributeMutation mutation = AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100);
        List<AttributeMutation> pendingAttributeMutations = Collections.singletonList(mutation);

        registrar.addPendingMutations(pendingAttributeMutations);
        assertFalse(store.getList().isEmpty());

        registrar.clearPendingMutations();
        assertTrue(store.getList().isEmpty());
    }

    @Test
    public void testUpload() throws RequestException {
        verifyRequest(200, true);
    }

    @Test
    public void testUpdate500Response() throws RequestException {
        verifyRequest(500, false);
    }

    @Test
    public void testUpdate429Response() throws RequestException {
        verifyRequest(429, false);
    }

    @Test
    public void testUpdate400Response() throws RequestException {
        verifyRequest(400, true);
    }

    private void verifyRequest(int status, boolean expectedResult) throws RequestException {
        final Response<Void> response = new Response.Builder<Void>(status)
                .build();

        AttributeListener mockListener = mock(AttributeListener.class);
        registrar.addAttributeListener(mockListener);

        registrar.setId("identifier", true);

        AttributeMutation mutation = AttributeMutation.newSetAttributeMutation("expected_key", JsonValue.wrapOpt("expected_value"), 100);
        List<AttributeMutation> pendingAttributeMutations = Collections.singletonList(mutation);

        registrar.addPendingMutations(pendingAttributeMutations);

        when(mockClient.updateAttributes("identifier", pendingAttributeMutations)).thenReturn(response);

        assertEquals(expectedResult, registrar.uploadPendingMutations());
        assertEquals(expectedResult, store.getList().isEmpty());

        if (UAHttpStatusUtil.inSuccessRange(status)) {
            verify(mockListener).onAttributeMutationsUploaded(pendingAttributeMutations);
        }
    }
}
