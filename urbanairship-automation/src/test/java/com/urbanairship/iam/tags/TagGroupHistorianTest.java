/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import com.urbanairship.TestClock;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.channel.TagGroupListener;
import com.urbanairship.channel.TagGroupsMutation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.iam.tags.TestUtils.tagSet;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TagGroupHistorian} tests.
 */
@RunWith(AndroidJUnit4.class)
public class TagGroupHistorianTest {

    private TagGroupHistorian historian;
    private TestClock clock;

    private List<TagGroupsMutation> pendingNamedUserMutations;
    private List<TagGroupsMutation> pendingChannelMutations;

    private NamedUser mockNamedUser;
    private AirshipChannel mockChannel;
    private TagGroupListener channelListener;
    private TagGroupListener namedUserListener;

    @Before
    public void setup() {
        mockChannel = mock(AirshipChannel.class);
        pendingChannelMutations = new ArrayList<>();
        when(mockChannel.getPendingTagUpdates()).thenReturn(pendingChannelMutations);

        mockNamedUser = mock(NamedUser.class);
        pendingNamedUserMutations = new ArrayList<>();
        when(mockNamedUser.getPendingTagUpdates()).thenReturn(pendingNamedUserMutations);

        // Capture the listener on init
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                channelListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockChannel).addTagGroupListener(any(TagGroupListener.class));

        // Capture the listener on init
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                namedUserListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockNamedUser).addTagGroupListener(any(TagGroupListener.class));

        clock = new TestClock();

        historian = new TagGroupHistorian(mockChannel, mockNamedUser, clock);
        historian.init();
    }

    /**
     * Test applying local data includes pending and sent mutations.
     */
    @Test
    public void applyLocalData() {
        when(mockNamedUser.getId()).thenReturn("named-user-id");

        Map<String, Set<String>> tags = new HashMap<>();
        tags.put("cool-group", tagSet("cool-cool", "cool"));

        // Pending
        pendingChannelMutations.add(TagGroupsMutation.newAddTagsMutation("number-group", tagSet("one", "two")));
        pendingNamedUserMutations.add(TagGroupsMutation.newRemoveTagsMutation("cool-group", tagSet("cool")));

        // Historical
        channelListener.onTagGroupsMutationUploaded("channel-id", TagGroupsMutation.newSetTagsMutation("history-group", tagSet("tag", "tags")));

        // Apply all local data that was created since beginning of time
        historian.applyLocalData(tags, 0);

        // 3 groups
        assertEquals(3, tags.size());

        // cool-group: cool-cool - from the set pending operation
        Set<String> coolTags = tags.get("cool-group");
        assertEquals(1, coolTags.size());
        assertTrue(coolTags.contains("cool-cool"));

        // numbers-group: one, two - from the add pending operation
        Set<String> numberTags = tags.get("number-group");
        assertEquals(2, numberTags.size());
        assertTrue(numberTags.contains("one"));
        assertTrue(numberTags.contains("two"));

        // history-group: tag, tags - from the sent operation
        Set<String> historyTags = tags.get("history-group");
        assertEquals(2, historyTags.size());
        assertTrue(historyTags.contains("tag"));
        assertTrue(historyTags.contains("tags"));
    }

    /**
     * Test sent mutations are applied oldest first.
     */
    @Test
    public void applyLocalDataRecordsInOrder() {
        when(mockNamedUser.getId()).thenReturn("named-user-id");

        Map<String, Set<String>> tags = new HashMap<>();

        // Add a record
        channelListener.onTagGroupsMutationUploaded("channel-id", TagGroupsMutation.newSetTagsMutation("history-group", tagSet("one", "two")));

        // Time travel
        clock.currentTimeMillis += 10;

        // Add another record
        namedUserListener.onTagGroupsMutationUploaded("named-user-id", TagGroupsMutation.newAddTagsMutation("history-group", tagSet("three")));

        // Apply records in the past 10 seconds (should include both)
        historian.applyLocalData(tags, clock.currentTimeMillis - 10);

        // 1 groups
        assertEquals(1, tags.size());

        // history-group: one, two, three
        Set<String> historyTags = tags.get("history-group");
        assertEquals(3, historyTags.size());
        assertTrue(historyTags.contains("one"));
        assertTrue(historyTags.contains("two"));
        assertTrue(historyTags.contains("three"));

        tags.clear();

        // Apply records in the past 9 seconds (should exclude the first set)
        historian.applyLocalData(tags, clock.currentTimeMillis - 9);

        // history-group: three
        historyTags = tags.get("history-group");
        assertEquals(1, historyTags.size());
        assertTrue(historyTags.contains("three"));
    }

    /**
     * Test only current named user tags are applied.
     */
    @Test
    public void testApplyRecordsDifferentNamedUser() {
        Map<String, Set<String>> tags = new HashMap<>();

        // Add a record
        namedUserListener.onTagGroupsMutationUploaded("some-other-named-user", TagGroupsMutation.newSetTagsMutation("neat", tagSet("one", "two")));

        // Change named user
        when(mockNamedUser.getId()).thenReturn("named-user");

        // Add another record
        namedUserListener.onTagGroupsMutationUploaded("named-user", TagGroupsMutation.newSetTagsMutation("cool", tagSet("foo", "bar")));

        // Apply records in the past 10 seconds (should include only named-user tag groups)
        historian.applyLocalData(tags, clock.currentTimeMillis - 10);

        assertEquals(1, tags.size());

        // history-group: one, two, three
        Set<String> historyTags = tags.get("cool");
        assertEquals(2, historyTags.size());
        assertTrue(historyTags.contains("foo"));
        assertTrue(historyTags.contains("bar"));
    }
}
