/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.channel.TagGroupRegistrar;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class TagGroupHistorianTest extends BaseTestCase {

    private TagGroupHistorian historian;
    private TagGroupRegistrar mockRegistrar;
    private TestClock clock;

    private List<TagGroupsMutation> pendingNamedUserMutations;
    private List<TagGroupsMutation> pendingChannelMutations;
    private TagGroupRegistrar.Listener listener;

    @Before
    public void setup() {
        pendingChannelMutations = new ArrayList<>();
        pendingNamedUserMutations = new ArrayList<>();
        mockRegistrar = mock(TagGroupRegistrar.class);
        when(mockRegistrar.getPendingMutations(TagGroupRegistrar.NAMED_USER)).thenReturn(pendingNamedUserMutations);
        when(mockRegistrar.getPendingMutations(TagGroupRegistrar.CHANNEL)).thenReturn(pendingChannelMutations);

        // Capture the listener on init
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                listener = invocation.getArgument(0);
                return null;
            }
        }).when(mockRegistrar).addListener(any(TagGroupRegistrar.Listener.class));

        clock = new TestClock();

        historian = new TagGroupHistorian(mockRegistrar, TestApplication.getApplication().preferenceDataStore, clock);
        historian.init();
    }

    /**
     * Test applying local data includes pending and sent mutations.
     */
    @Test
    public void applyLocalData() {
        Map<String, Set<String>> tags = new HashMap<>();
        tags.put("cool-group", tagSet("cool-cool", "cool"));

        // Pending
        pendingChannelMutations.add(TagGroupsMutation.newAddTagsMutation("number-group", tagSet("one", "two")));
        pendingNamedUserMutations.add(TagGroupsMutation.newRemoveTagsMutation("cool-group", tagSet("cool")));

        // Historical
        listener.onMutationUploaded(TagGroupsMutation.newSetTagsMutation("history-group", tagSet("tag", "tags")));

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
        Map<String, Set<String>> tags = new HashMap<>();

        // Add a record
        listener.onMutationUploaded(TagGroupsMutation.newSetTagsMutation("history-group", tagSet("one", "two")));

        // Time travel
        clock.currentTimeMillis += 10;

        // Add another record
        listener.onMutationUploaded(TagGroupsMutation.newAddTagsMutation("history-group", tagSet("three")));

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

    @Test
    public void testNullRecord() {
        PreferenceDataStore dataStore = TestApplication.getApplication().preferenceDataStore;
        dataStore.put(TagGroupHistorian.RECORDS_KEY, JsonValue.wrapOpt(Arrays.asList(JsonMap.EMPTY_MAP)));

        Map<String, Set<String>> tags = new HashMap<>();

        // Apply records in the past 10 seconds (should include both)
        historian.applyLocalData(tags, clock.currentTimeMillis - 10);
    }

}

