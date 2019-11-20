/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import androidx.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.urbanairship.iam.tags.TestUtils.tagSet;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TagGroupManager} tests.
 */
public class TagGroupManagerTest extends BaseTestCase {

    private TagGroupManager manager;

    private TagGroupLookupApiClient mockClient;
    private AirshipChannel mockChannel;
    private TagGroupHistorian mockHistorian;

    private TestCallback callback;
    private String channelId;

    private Map<String, Set<String>> requestTags;
    private Map<String, Set<String>> callbackResponseTags;
    private Map<String, Set<String>> clientResponseTags;

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

        channelId = "some-channel-id";
        when(mockChannel.getId()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return channelId;
            }
        });

        mockHistorian = mock(TagGroupHistorian.class);
        manager = new TagGroupManager(mockClient, mockChannel, mockHistorian, TestApplication.getApplication().preferenceDataStore, clock);

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
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        TagGroupResult result = manager.getTags(requestTags);

        // Verify the historian was consulted
        verify(mockHistorian).applyLocalData(clientResponseTags, clock.currentTimeMillis - manager.getPreferLocalTagDataTime());

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
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        TagGroupResult result = manager.getTags(requestTags);

        TagGroupResult cachedResult = manager.getTags(requestTags);

        verify(mockClient).lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null);
        verifyNoMoreInteractions(mockClient);

        // Results should be the same
        assertEquals(result.success, cachedResult.success);
        assertEquals(result.tagGroups, cachedResult.tagGroups);
    }

    /**
     * Test getTags uses the local data applied by the historian.
     */
    @Test
    public void getTagsUsesLocalData() {
        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        // Add the tag "story" so we should get "cool" "story" back instead of just "cool".
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Map<String, Set<String>> tags = invocation.getArgument(0);
                tags.get("some-group").add("story");
                return null;
            }
        }).when(mockHistorian).applyLocalData(eq(clientResponseTags), anyLong());

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
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
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
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
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

        verifyZeroInteractions(mockClient);
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

        verifyZeroInteractions(mockClient);
    }

    /**
     * Test requesting tags when the channel is not set returns a failed result.
     */
    @Test
    public void getTagsNoChannel() {
        channelId = null;

        TagGroupResult result = manager.getTags(requestTags);
        assertFalse(result.success);

        verifyZeroInteractions(mockClient);
    }

    /**
     * Test requesting tags when the client fails to fetch tags.
     */
    @Test
    public void getTagsBadResponse() {
        // Set up a 400 response
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(400, null, null));

        TagGroupResult result = manager.getTags(requestTags);

        assertFalse(result.success);
    }

    /**
     * Test requesting tags will refresh the cache if its been longer than the max cache age.
     */
    @Test
    public void testRefreshCache() {
        manager.setCacheMaxAgeTime(TagGroupManager.MIN_CACHE_MAX_AGE_TIME_MS, TimeUnit.MILLISECONDS);

        TagGroupResponse response = new TagGroupResponse(200, clientResponseTags, "lastModifiedTime");

        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
                .thenReturn(response);

        TagGroupResult result = manager.getTags(requestTags);
        assertTrue(result.success);

        verify(mockClient, times(1)).lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null);

        // Time travel past the cache max age
        clock.currentTimeMillis += TagGroupManager.MIN_CACHE_MAX_AGE_TIME_MS + 1;

        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), response))
                .thenReturn(response);

        // Fetch tag again
        result = manager.getTags(requestTags);
        assertTrue(result.success);

        // Verify the second call was made with the previous cached response
        verify(mockClient, times(1)).lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), response);
    }

    /**
     * Test requesting tags will still use the cache if fails to update as long as its age is less
     * than the stale read time.
     */
    @Test
    public void getTagsStaleCache() {
        manager.setCacheMaxAgeTime(TagGroupManager.MIN_CACHE_MAX_AGE_TIME_MS, TimeUnit.MILLISECONDS);
        manager.setCacheStaleReadTime(TagGroupManager.MIN_CACHE_MAX_AGE_TIME_MS + 10, TimeUnit.MILLISECONDS);

        TagGroupResponse response = new TagGroupResponse(200, clientResponseTags, "lastModifiedTime");

        // Set up a response that returns all the tags
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
                .thenReturn(response);

        TagGroupResult result = manager.getTags(requestTags);
        assertTrue(result.success);

        verify(mockClient, times(1)).lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null);

        // Time travel past the cache max age
        clock.currentTimeMillis += TagGroupManager.MIN_CACHE_MAX_AGE_TIME_MS + 1;

        // Set up a 400 response
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), response))
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
        when(mockClient.lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null))
                .thenReturn(new TagGroupResponse(200, clientResponseTags, "lastModifiedTime"));

        manager.getTags(requestTags);

        verify(mockClient, times(1))
                .lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null);

        // Request a new tag that the manager did not previously request.
        requestTags.get("some-group").add("new-tag");

        manager.getTags(requestTags);

        verify(mockClient, times(1))
                .lookupTagGroups(channelId, UAirship.ANDROID_PLATFORM, getExpectedClientRequestTags(), null);

        verifyNoMoreInteractions(mockClient);
    }

    /**
     * Test setting local data time and cache stale read time updates the historians max record age.
     */
    @Test
    public void historianMaxRecordAge() {
        manager.setPreferLocalTagDataTime(10, TimeUnit.MILLISECONDS);
        manager.setCacheStaleReadTime(10, TimeUnit.MILLISECONDS);

        reset(mockHistorian);

        manager.setPreferLocalTagDataTime(20, TimeUnit.MILLISECONDS);
        verify(mockHistorian).setMaxRecordAge(30, TimeUnit.MILLISECONDS);

        manager.setCacheStaleReadTime(20, TimeUnit.MILLISECONDS);
        verify(mockHistorian).setMaxRecordAge(40, TimeUnit.MILLISECONDS);
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

        verifyZeroInteractions(mockClient);
    }

    private Map<String, Set<String>> getExpectedClientRequestTags() {
        return TagGroupUtils.union(callbackResponseTags, requestTags);
    }

    private class TestCallback implements TagGroupManager.RequestTagsCallback {

        Map<String, Set<String>> tags;

        @NonNull
        @Override
        public Map<String, Set<String>> getTags() throws Exception {
            return tags;
        }

    }

}