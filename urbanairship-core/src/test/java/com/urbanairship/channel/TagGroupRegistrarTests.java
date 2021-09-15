/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TagGroupRegistrar}.
 */
public class TagGroupRegistrarTests extends BaseTestCase {

    private PendingTagGroupMutationStore store;
    private TagGroupApiClient mockClient;
    private TagGroupRegistrar registrar;

    @Before
    public void setup() {
        mockClient = mock(TagGroupApiClient.class);
        store = new PendingTagGroupMutationStore(TestApplication.getApplication().preferenceDataStore, "TagGroupRegistrarTests.named-user");
        registrar = new TagGroupRegistrar(mockClient, store);
    }

    @Test
    public void testSetId() {
        registrar.setId(null, false);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        registrar.addPendingMutations(Collections.singletonList(mutation));

        registrar.setId("what", false);
        assertEquals(store.peek(), mutation);

        registrar.setId("what", true);
        assertEquals(store.peek(), mutation);

        registrar.setId("something-else", true);
        assertNull(store.peek());
    }

    @Test
    public void testAdd() {
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        registrar.addPendingMutations(Collections.singletonList(mutation));
        assertEquals(store.peek(), mutation);
    }

    @Test
    public void testClear() {
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        registrar.addPendingMutations(Collections.singletonList(mutation));
        assertFalse(store.getList().isEmpty());
        assertEquals(store.getList(), registrar.getPendingMutations());

        registrar.clearPendingMutations();
        assertTrue(registrar.getPendingMutations().isEmpty());
        assertTrue(store.getList().isEmpty());
        ;
    }

    /**
     * Test 200 response clears tags.
     */
    @Test
    public void testUpload() throws RequestException {
        verifyRequest(200, true);
    }

    /**
     * Test 500 response does not clear the tags.
     */
    @Test
    public void testUpdate500Response() throws RequestException {
        verifyRequest(500, false);
    }

    /**
     * Test 429 response does not clear the tags.
     */
    @Test
    public void testUpdate429Response() throws RequestException {
        verifyRequest(429, false);
    }

    /**
     * Test 400 response clears tags and returns true for being done.
     */
    @Test
    public void testUpdate400Response() throws RequestException {
        verifyRequest(400, true);
    }

    @Test
    public void testClearTagsDuringUpload() throws RequestException {
        final Response<Void> response = new Response.Builder<Void>(200)
                .build();

        TestListener listener = new TestListener();
        registrar.addTagGroupListener(listener);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        registrar.addPendingMutations(Collections.singletonList(mutation));

        when(mockClient.updateTags("identifier", mutation)).thenAnswer(new Answer<Response>() {
            @Override
            public Response answer(InvocationOnMock invocation) {
                registrar.clearPendingMutations();
                return response;
            }
        });

        registrar.setId("identifier", false);
        registrar.uploadPendingMutations();

        assertEquals(1, listener.mutations.size());
        assertEquals(mutation, listener.mutations.get(0));
    }

    private void verifyRequest(int status, boolean expectedResult) throws RequestException {
        final Response<Void> response = new Response.Builder<Void>(status)
                .build();

        registrar.setId("identifier", true);

        TestListener listener = new TestListener();
        registrar.addTagGroupListener(listener);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        registrar.addPendingMutations(Collections.singletonList(mutation));

        when(mockClient.updateTags("identifier", mutation)).thenReturn(response);

        assertEquals(expectedResult, registrar.uploadPendingMutations());
        assertEquals(expectedResult, registrar.getPendingMutations().isEmpty());

        if (status == 200) {
            assertEquals(1, listener.mutations.size());
            assertEquals(mutation, listener.mutations.get(0));
        }
    }

    private static class TestListener implements TagGroupListener {
        List<TagGroupsMutation> mutations = new ArrayList<>();

        @Override
        public void onTagGroupsMutationUploaded(@NonNull List<TagGroupsMutation> mutations) {
            this.mutations.addAll(mutations);
        }
    }

}
