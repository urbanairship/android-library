/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.channel.PendingTagGroupMutationStore;
import com.urbanairship.channel.TagGroupApiClient;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TagGroupRegistrar}.
 */
public class TagGroupRegistrarTests extends BaseTestCase {

    private PendingTagGroupMutationStore namedUserStore;
    private PendingTagGroupMutationStore channelStore;
    private TagGroupApiClient mockClient;
    private TagGroupRegistrar registrar;

    @Before
    public void setup() {
        mockClient = mock(TagGroupApiClient.class);
        namedUserStore = new PendingTagGroupMutationStore(TestApplication.getApplication().preferenceDataStore, "TagGroupRegistrarTests.named-user");
        channelStore = new PendingTagGroupMutationStore(TestApplication.getApplication().preferenceDataStore, "TagGroupRegistrarTests.channel");

        namedUserStore.clear();
        channelStore.clear();

        registrar = new TagGroupRegistrar(mockClient, channelStore, namedUserStore);
    }

    @Test
    public void testAdd() {
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));

        registrar.addMutations(TagGroupRegistrar.NAMED_USER, Collections.singletonList(mutation));

        // Verify it was added to the right store
        assertEquals(namedUserStore.peek(), mutation);
        assertTrue(channelStore.getMutations().isEmpty());

        namedUserStore.clear();

        registrar.addMutations(TagGroupRegistrar.CHANNEL, Collections.singletonList(mutation));

        // Verify it was added to the right store
        assertEquals(channelStore.peek(), mutation);
        assertTrue(namedUserStore.getMutations().isEmpty());
    }

    @Test
    public void testClear() {
        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));

        // Add a mutation to each store
        registrar.addMutations(TagGroupRegistrar.NAMED_USER, Collections.singletonList(mutation));
        registrar.addMutations(TagGroupRegistrar.CHANNEL, Collections.singletonList(mutation));

        // Clear the named user
        registrar.clearMutations(TagGroupRegistrar.NAMED_USER);

        // Verify only the named user store was cleared
        assertFalse(channelStore.getMutations().isEmpty());
        assertTrue(namedUserStore.getMutations().isEmpty());

        // Add back to named user
        registrar.addMutations(TagGroupRegistrar.NAMED_USER, Collections.singletonList(mutation));

        // Clear channel
        registrar.clearMutations(TagGroupRegistrar.CHANNEL);

        // Verify only the channel store was cleared
        assertTrue(channelStore.getMutations().isEmpty());
        assertFalse(namedUserStore.getMutations().isEmpty());
    }

    /**
     * Test 200 response clears tags.
     */
    @Test
    public void testUpdate200Response() {
        // Set up a 200 response
        Response response = Response.newBuilder(HttpURLConnection.HTTP_OK)
                                    .setResponseMessage("OK")
                                    .setResponseBody("{ \"ok\": true}")
                                    .build();

        verifyRequest(response, TagGroupRegistrar.NAMED_USER, namedUserStore, true);
        verifyRequest(response, TagGroupRegistrar.CHANNEL, channelStore, true);
    }

    /**
     * Test 500 response does not clear the tags.
     */
    @Test
    public void testUpdate500Response() {
        // Set up a 500 response
        Response response = Response.newBuilder(500)
                                    .build();

        verifyRequest(response, TagGroupRegistrar.NAMED_USER, namedUserStore, false);
        verifyRequest(response, TagGroupRegistrar.CHANNEL, channelStore, false);
    }

    /**
     * Test 429 response does not clear the tags.
     */
    @Test
    public void testUpdate429Response() {
        // Set up a 429 response
        Response response = Response.newBuilder(429)
                                    .build();

        verifyRequest(response, TagGroupRegistrar.NAMED_USER, namedUserStore, false);
        verifyRequest(response, TagGroupRegistrar.CHANNEL, channelStore, false);
    }

    /**
     * Test 400 response clears tags and returns true for being done.
     */
    @Test
    public void testUpdate400Response() {
        // Set up a 400 response
        Response response = Response.newBuilder(400)
                                    .build();

        verifyRequest(response, TagGroupRegistrar.NAMED_USER, namedUserStore, true);
        verifyRequest(response, TagGroupRegistrar.CHANNEL, channelStore, true);
    }

    @Test
    public void testClearTagsDuringUpload() {
        final Response response = new Response.Builder(200)
                .build();

        TestListener listener = new TestListener();
        registrar.addListener(listener);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        registrar.addMutations(TagGroupRegistrar.NAMED_USER, Collections.singletonList(mutation));

        when(mockClient.updateTagGroups(TagGroupRegistrar.NAMED_USER, "identifier", mutation)).thenAnswer(new Answer<Response>() {
            @Override
            public Response answer(InvocationOnMock invocation) throws Throwable {
                namedUserStore.clear();
                return response;
            }
        });

        registrar.uploadMutations(TagGroupRegistrar.NAMED_USER, "identifier");

        assertEquals(1, listener.mutations.size());
        assertEquals(mutation, listener.mutations.get(0));
        registrar.removeListener(listener);
    }

    private void verifyRequest(Response response, @TagGroupRegistrar.TagGroupType int type, PendingTagGroupMutationStore store, boolean expectedResult) {

        TestListener listener = new TestListener();
        registrar.addListener(listener);

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        registrar.addMutations(type, Collections.singletonList(mutation));
        assertFalse(store.getMutations().isEmpty());

        when(mockClient.updateTagGroups(type, "identifier", mutation)).thenReturn(response);

        assertEquals(expectedResult, registrar.uploadMutations(type, "identifier"));
        assertEquals(expectedResult, store.getMutations().isEmpty());

        if (expectedResult) {
            assertEquals(1, listener.mutations.size());
            assertEquals(mutation, listener.mutations.get(0));
        }
        registrar.removeListener(listener);
    }

    private static class TestListener implements TagGroupRegistrar.Listener {

        List<TagGroupsMutation> mutations = new ArrayList<>();

        @Override
        public void onMutationUploaded(@NonNull TagGroupsMutation mutation) {
            mutations.add(mutation);
        }

    }

}