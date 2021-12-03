/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.contacts.Contact;
import com.urbanairship.contacts.ContactChangeListener;
import com.urbanairship.contacts.ContactConflictListener;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.automation.tags.TestUtils.tagSet;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AudienceManager} tests.
 */
@RunWith(AndroidJUnit4.class)
public class AudienceManagerTest {

    private AudienceManager manager;

    private TagGroupLookupApiClient mockClient;
    private AirshipChannel mockChannel;
    private AudienceHistorian mockHistorian;
    private Contact mockContact;
    private TagGroupLookupResponseCache spyCache;

    private TestCallback callback;
    private String channelId;

    private Map<String, Set<String>> requestTags;
    private Map<String, Set<String>> callbackResponseTags;
    private Map<String, Set<String>> clientResponseTags;

    private List<TagGroupsMutation> pendingNamedUserMutations;
    private List<TagGroupsMutation> pendingChannelMutations;

    private TestClock clock;

    @Before
    public void setup() {
        clock = new TestClock();

        requestTags = new HashMap<>();
        requestTags.put("some-group", tagSet("cool", "story"));

        callbackResponseTags = new HashMap<>();
        callbackResponseTags.put("some-group", tagSet("cool", "story"));
        callbackResponseTags.put("some-other-group", tagSet("not cool"));
        callbackResponseTags.put("yet-another-group", tagSet("so cool"));

        clientResponseTags = new HashMap<>();
        clientResponseTags.put("some-group", tagSet("cool"));
        clientResponseTags.put("some-other-group", tagSet("not cool"));

        mockClient = mock(TagGroupLookupApiClient.class);
        mockChannel = mock(AirshipChannel.class);
        mockContact = mock(Contact.class);
        spyCache = spy(new TagGroupLookupResponseCache(TestApplication.getApplication().preferenceDataStore, clock));

        pendingChannelMutations = new ArrayList<>();
        when(mockChannel.getPendingTagUpdates()).thenReturn(pendingChannelMutations);
        pendingNamedUserMutations = new ArrayList<>();
        when(mockContact.getPendingTagUpdates()).thenReturn(pendingNamedUserMutations);

        channelId = "some-channel-id";
        when(mockChannel.getId()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) {
                return channelId;
            }
        });

        mockHistorian = mock(AudienceHistorian.class);
        manager = new AudienceManager(mockClient, mockChannel, mockContact, spyCache,
                mockHistorian, TestApplication.getApplication().preferenceDataStore, clock);

        callback = new TestCallback();
        manager.setRequestTagsCallback(callback);

        // Request the current tags
        callback.tags = callbackResponseTags;
    }

    /**
     * Test getTags returns the intersection of tags from the server and
     * the requested tags.
     */
    @Test
    public void getTags() {
        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        TagGroupResult result = manager.getTags(requestTags);

        // Verify the historian was consulted
        verify(mockHistorian).getTagGroupHistory(clock.currentTimeMillis - manager.getPreferLocalTagDataTime());

        // Verify the result. Should contain only the single tag "cool" in "some-group".
        assertTrue(result.success);
        assertEquals(1, result.tagGroups.size());
        assertNotNull(result.tagGroups.get("some-group"));
        assertEquals(1, result.tagGroups.get("some-group").size());
        assertTrue(result.tagGroups.get("some-group").contains("cool"));
    }

    /**
     * Test getTags uses cache if available.
     */
    @Test
    public void getTagsUsesCache() {
        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        TagGroupResult result = manager.getTags(requestTags);

        TagGroupResult cachedResult = manager.getTags(requestTags);

        verify(mockClient).lookupTagGroups(channelId, getExpectedClientRequestTags(), null);
        verifyNoMoreInteractions(mockClient);

        // Results should be the same
        assertEquals(result.success, cachedResult.success);
        assertEquals(result.tagGroups, cachedResult.tagGroups);
    }

    /**
     * Test getTags uses the tag group history.
     */
    @Test
    public void getTagsUsesLocalData() {
        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        List<TagGroupsMutation> mutations = new ArrayList<>();
        mutations.add(TagGroupsMutation.newSetTagsMutation("some-group", Collections.singleton("story")));
        when(mockHistorian.getTagGroupHistory(anyLong())).thenReturn(mutations);

        pendingChannelMutations.add(TagGroupsMutation.newAddTagsMutation("some-group", Collections.singleton("cool")));
        TagGroupResult result = manager.getTags(requestTags);

        // Verify the result.
        assertTrue(result.success);
        assertEquals(1, result.tagGroups.size());
        assertNotNull(result.tagGroups.get("some-group"));
        assertEquals(2, result.tagGroups.get("some-group").size());
        assertTrue(result.tagGroups.get("some-group").contains("cool"));
        assertTrue(result.tagGroups.get("some-group").contains("story"));
    }

    /**
     * Test getTags overrides the device group with the push manager
     * tags if it channel tags are enabled.
     */
    @Test
    public void getTagsDeviceGroup() {
        // Setup push to return device tags
        Set<String> deviceTags = tagSet("local tag");
        when(mockChannel.getTags()).thenReturn(deviceTags);
        when(mockChannel.getChannelTagRegistrationEnabled()).thenReturn(true);

        // Have the response return a server side device tag
        clientResponseTags.put("device", tagSet("server tag"));

        // Request the local device tag as well as the other tag groups
        requestTags.put("device", tagSet("server tag", "local tag"));

        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        TagGroupResult result = manager.getTags(requestTags);

        // Verify the result.
        assertTrue(result.success);
        assertEquals(2, result.tagGroups.size());
        assertEquals(1, result.tagGroups.get("device").size());
        assertTrue(result.tagGroups.get("device").contains("local tag"));
    }

    /**
     * Test getTags does not overrides the device group with the push manager
     * tags if it channel tags are disabled.
     */
    @Test
    public void getTagsDeviceGroupPushManagerTagsDisabled() {
        when(mockChannel.getChannelTagRegistrationEnabled()).thenReturn(false);

        // Have the response return a server side device tag
        clientResponseTags.put("device", tagSet("server tag"));

        // Request the local device tag as well as the other tag groups
        requestTags.put("device", tagSet("server tag"));

        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        TagGroupResult result = manager.getTags(requestTags);

        // Verify the result.
        assertTrue(result.success);
        assertEquals(2, result.tagGroups.size());
        assertEquals(1, result.tagGroups.get("device").size());
        assertTrue(result.tagGroups.get("device").contains("server tag"));
    }

    /**
     * Test getting only device tags when channel tags are enabled does not
     * fetch tag groups.
     */
    @Test
    public void getTagsOnlyDeviceTags() {
        // Setup push to return device tags
        Set<String> deviceTags = tagSet("local tag");
        when(mockChannel.getTags()).thenReturn(deviceTags);
        when(mockChannel.getChannelTagRegistrationEnabled()).thenReturn(true);

        // Request only the local tag
        requestTags.clear();
        requestTags.put("device", tagSet("local tag"));

        TagGroupResult result = manager.getTags(requestTags);

        // Verify the result.
        assertTrue(result.success);
        assertEquals(1, result.tagGroups.size());
        assertEquals(1, result.tagGroups.get("device").size());
        assertTrue(result.tagGroups.get("device").contains("local tag"));

        verifyNoInteractions(mockClient);
    }

    /**
     * Test requesting an empty set of tags does not fetch tag groups.
     */
    @Test
    public void getTagsEmptyRequest() {
        requestTags.clear();

        TagGroupResult result = manager.getTags(requestTags);
        assertTrue(result.success);
        assertTrue(result.tagGroups.isEmpty());

        verifyNoInteractions(mockClient);
    }

    /**
     * Test requesting tags when the channel is not set returns a failed result.
     */
    @Test
    public void getTagsNoChannel() {
        channelId = null;

        TagGroupResult result = manager.getTags(requestTags);
        assertFalse(result.success);

        verifyNoInteractions(mockClient);
    }

    /**
     * Test requesting tags when the client fails to fetch tags.
     */
    @Test
    public void getTagsBadResponse() {
        // Set up a 400 response
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(400, null, null));

        TagGroupResult result = manager.getTags(requestTags);

        assertFalse(result.success);
    }

    /**
     * Test requesting tags will refresh the cache if its been longer than the max cache age.
     */
    @Test
    public void testRefreshCache() {
        manager.setCacheMaxAgeTime(TagGroupLookupResponseCache.MIN_MAX_AGE_TIME_MS, TimeUnit.MILLISECONDS);

        TagGroupResponse response = new TagGroupResponse(200, clientResponseTags, "lastModifiedTime");

        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(response);

        TagGroupResult result = manager.getTags(requestTags);
        assertTrue(result.success);

        verify(mockClient, times(1)).lookupTagGroups(channelId, getExpectedClientRequestTags(), null);

        // Time travel past the cache max age
        clock.currentTimeMillis += TagGroupLookupResponseCache.MIN_MAX_AGE_TIME_MS + 1;

        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), response))
                .thenReturn(response);

        // Fetch tag again
        result = manager.getTags(requestTags);
        assertTrue(result.success);

        // Verify the second call was made with the previous cached response
        verify(mockClient, times(1)).lookupTagGroups(channelId, getExpectedClientRequestTags(), response);
    }

    /**
     * Test requesting tags will still use the cache if fails to update as long as its age is less
     * than the stale read time.
     */
    @Test
    public void getTagsStaleCache() {
        manager.setCacheMaxAgeTime(TagGroupLookupResponseCache.MIN_MAX_AGE_TIME_MS, TimeUnit.MILLISECONDS);
        manager.setCacheStaleReadTime(TagGroupLookupResponseCache.MIN_MAX_AGE_TIME_MS + 10, TimeUnit.MILLISECONDS);

        TagGroupResponse response = new TagGroupResponse(200, clientResponseTags, "lastModifiedTime");

        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(response);

        TagGroupResult result = manager.getTags(requestTags);
        assertTrue(result.success);

        verify(mockClient, times(1)).lookupTagGroups(channelId, getExpectedClientRequestTags(), null);

        // Time travel past the cache max age
        clock.currentTimeMillis += TagGroupLookupResponseCache.MIN_MAX_AGE_TIME_MS + 1;

        // Set up a 400 response
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), response))
                .thenReturn(new TagGroupResponse(400, null, null));

        // Fetch tag again, should still be success
        result = manager.getTags(requestTags);
        assertTrue(result.success);

        // Time travel past the cache max stale read time
        clock.currentTimeMillis += 10;

        // Fetch tag again, should no longer use stale cache
        result = manager.getTags(requestTags);
        assertFalse(result.success);
    }

    /**
     * Test requesting new tags that were previously not fetched will cause the cache to be refreshed.
     */
    @Test
    public void getTagsNewTags() {
        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        manager.getTags(requestTags);

        verify(mockClient, times(1))
                .lookupTagGroups(channelId, getExpectedClientRequestTags(), null);

        // Request a new tag that the manager did not previously request.
        requestTags.get("some-group").add("new-tag");

        manager.getTags(requestTags);

        verify(mockClient, times(1))
                .lookupTagGroups(channelId, getExpectedClientRequestTags(), null);

        verifyNoMoreInteractions(mockClient);
    }

    /**
     * Getting tags without the callback set should throw an illegal state exception.
     */
    @Test(expected = IllegalStateException.class)
    public void getTagsNoCallback() {
        manager.setRequestTagsCallback(null);
        manager.getTags(new HashMap<String, Set<String>>());
    }

    /**
     * Test disabled will return a error result.
     */
    @Test
    public void getTagsDisabled() {
        manager.setEnabled(false);

        TagGroupResult result = manager.getTags(requestTags);
        assertFalse(result.success);

        verifyNoInteractions(mockClient);
    }

    private Map<String, Set<String>> getExpectedClientRequestTags() {
        return TagGroupUtils.union(callbackResponseTags, requestTags);
    }

    private class TestCallback implements AudienceManager.RequestTagsCallback {

        Map<String, Set<String>> tags;

        @NonNull
        @Override
        public Map<String, Set<String>> getTags() {
            return tags;
        }

    }

    @Test
    public void testGetTagOverrides() {
        clock.currentTimeMillis = System.currentTimeMillis();

        List<TagGroupsMutation> history = new ArrayList<>();
        history.add(TagGroupsMutation.newRemoveTagsMutation("foo", tagSet("one", "two")));
        history.add(TagGroupsMutation.newSetTagsMutation("bar", tagSet("a")));
        history.add(TagGroupsMutation.newSetTagsMutation("baz", tagSet("1")));

        when(mockHistorian.getTagGroupHistory(clock.currentTimeMillis - manager.getPreferLocalTagDataTime() )).thenReturn(history);

        pendingChannelMutations.add(TagGroupsMutation.newSetTagsMutation("baz", tagSet("2")));
        pendingChannelMutations.add(TagGroupsMutation.newAddTagsMutation("bar", tagSet("b")));

        pendingNamedUserMutations.add(TagGroupsMutation.newSetTagsMutation("baz", tagSet("3")));
        pendingNamedUserMutations.add(TagGroupsMutation.newAddTagsMutation("foo", tagSet("one")));

        List<TagGroupsMutation> expected = new ArrayList<>();
        expected.addAll(history);
        expected.addAll(pendingNamedUserMutations);
        expected.addAll(pendingChannelMutations);

        assertEquals(TagGroupsMutation.collapseMutations(expected), manager.getTagOverrides());
    }

    @Test
    public void testGetAttributeOverrides() {
        clock.currentTimeMillis = System.currentTimeMillis();

        List<AttributeMutation> history = new ArrayList<>();
        history.add(AttributeMutation.newRemoveAttributeMutation("foo", 100));
        history.add(AttributeMutation.newSetAttributeMutation("bar", JsonValue.wrapOpt(100), 100));
        history.add(AttributeMutation.newSetAttributeMutation("baz", JsonValue.wrapOpt("baz"), 100));

        when(mockHistorian.getAttributeHistory(clock.currentTimeMillis - manager.DEFAULT_PREFER_LOCAL_DATA_TIME_MS)).thenReturn(history);

        List<AttributeMutation> pendingChannelAttributes = Collections.singletonList(AttributeMutation.newSetAttributeMutation("baz", JsonValue.wrapOpt("updated baz"), 100));
        when(mockChannel.getPendingAttributeUpdates()).thenReturn(pendingChannelAttributes);

        List<AttributeMutation> pendingNamedUserAttributes = Collections.singletonList(AttributeMutation.newSetAttributeMutation("bar", JsonValue.wrapOpt("updated bar"), 100));
        when(mockContact.getPendingAttributeUpdates()).thenReturn(pendingNamedUserAttributes);

        List<AttributeMutation> expected = new ArrayList<>();
        expected.addAll(history);
        expected.addAll(pendingNamedUserAttributes);
        expected.addAll(pendingChannelAttributes);

        assertEquals(AttributeMutation.collapseMutations(expected), manager.getAttributeOverrides());
    }

    @Test
    public void testContactChangeClearsCache() {
        ArgumentCaptor<ContactChangeListener> argumentCaptor = ArgumentCaptor.forClass(ContactChangeListener.class);
        verify(mockContact).addContactChangeListener(argumentCaptor.capture());
        argumentCaptor.getValue().onContactChanged();
        verify(spyCache).clear();
    }

}
