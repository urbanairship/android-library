/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

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
import java.util.Collections;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.automation.tags.TestUtils.tagSet;
import static junit.framework.Assert.assertEquals;
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
    private NamedUser mockNamedUser;
    private AirshipChannel mockChannel;
    private TagGroupListener channelListener;
    private TagGroupListener namedUserListener;

    @Before
    public void setup() {
        mockChannel = mock(AirshipChannel.class);
        mockNamedUser = mock(NamedUser.class);

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

    @Test
    public void getTagGroupHistory() {
        when(mockNamedUser.getId()).thenReturn("named-user-id");

        clock.currentTimeMillis = 100;

        final TagGroupsMutation channelMutation = TagGroupsMutation.newSetTagsMutation("history-group", tagSet("tag", "tags"));
        channelListener.onTagGroupsMutationUploaded("channel-id", channelMutation);

        clock.currentTimeMillis = 200;
        final TagGroupsMutation namedUserMutation = TagGroupsMutation.newAddTagsMutation("history-group", tagSet("another-tag"));
        namedUserListener.onTagGroupsMutationUploaded("named-user-id", namedUserMutation);

        List<TagGroupsMutation> allMutations = new ArrayList<>();
        allMutations.add(channelMutation);
        allMutations.add(namedUserMutation);
        assertEquals(allMutations, historian.getTagGroupHistory(100));

        assertEquals(Collections.singletonList(namedUserMutation), historian.getTagGroupHistory(101));

        clock.currentTimeMillis = 201;
        assertEquals(Collections.emptyList(), historian.getTagGroupHistory(201));
    }

    @Test
    public void getTagGroupHistoryDifferentNamedUser() {
        when(mockNamedUser.getId()).thenReturn("foo");

        clock.currentTimeMillis = 100;

        TagGroupsMutation fooMutation = TagGroupsMutation.newAddTagsMutation("history-group", tagSet("another-tag"));
        namedUserListener.onTagGroupsMutationUploaded("foo", fooMutation);

        TagGroupsMutation barMutation = TagGroupsMutation.newAddTagsMutation("history-group", tagSet("another-tag"));
        namedUserListener.onTagGroupsMutationUploaded("bar", barMutation);

        assertEquals(Collections.singletonList(fooMutation), historian.getTagGroupHistory(0));
    }
}
