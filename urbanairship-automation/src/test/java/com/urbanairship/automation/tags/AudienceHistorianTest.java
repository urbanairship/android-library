/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.TestClock;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AttributeListener;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.channel.TagGroupListener;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.json.JsonValue;

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
 * {@link AudienceHistorian} tests.
 */
@RunWith(AndroidJUnit4.class)
public class AudienceHistorianTest {

    private AudienceHistorian historian;
    private TestClock clock;
    private NamedUser mockNamedUser;
    private AirshipChannel mockChannel;
    private TagGroupListener channelTagListener;
    private AttributeListener channelAttributeListener;
    private TagGroupListener namedUserTagListener;
    private AttributeListener namedUserAttributeListener;
    @Before
    public void setup() {
        mockChannel = mock(AirshipChannel.class);
        mockNamedUser = mock(NamedUser.class);

        // Capture the listeners son init
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                channelTagListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockChannel).addTagGroupListener(any(TagGroupListener.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                channelAttributeListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockChannel).addAttributeListener(any(AttributeListener.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                namedUserTagListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockNamedUser).addTagGroupListener(any(TagGroupListener.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                namedUserAttributeListener = invocation.getArgument(0);
                return null;
            }
        }).when(mockNamedUser).addAttributeListener(any(AttributeListener.class));

        clock = new TestClock();

        historian = new AudienceHistorian(mockChannel, mockNamedUser, clock);
        historian.init();
    }

    @Test
    public void getTagGroupHistory() {
        when(mockNamedUser.getId()).thenReturn("named-user-id");

        clock.currentTimeMillis = 100;

        final TagGroupsMutation channelMutation = TagGroupsMutation.newSetTagsMutation("history-group", tagSet("tag", "tags"));
        channelTagListener.onTagGroupsMutationUploaded("channel-id", channelMutation);

        clock.currentTimeMillis = 200;
        final TagGroupsMutation namedUserMutation = TagGroupsMutation.newAddTagsMutation("history-group", tagSet("another-tag"));
        namedUserTagListener.onTagGroupsMutationUploaded("named-user-id", namedUserMutation);

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
        namedUserTagListener.onTagGroupsMutationUploaded("foo", fooMutation);

        TagGroupsMutation barMutation = TagGroupsMutation.newAddTagsMutation("history-group", tagSet("another-tag"));
        namedUserTagListener.onTagGroupsMutationUploaded("bar", barMutation);

        assertEquals(Collections.singletonList(fooMutation), historian.getTagGroupHistory(0));
    }

    @Test
    public void getAttributeHistory() {
        when(mockNamedUser.getId()).thenReturn("named-user-id");

        clock.currentTimeMillis = 100;

        final AttributeMutation channelMutation = AttributeMutation.newSetAttributeMutation("channel attribute", JsonValue.wrapOpt("some value"), 100);
        channelAttributeListener.onAttributeMutationsUploaded("channel-id", Collections.singletonList(channelMutation));

        clock.currentTimeMillis = 200;
        final AttributeMutation namedUserMutation = AttributeMutation.newSetAttributeMutation("named user attribute", JsonValue.wrapOpt("some value"), 100);
        namedUserAttributeListener.onAttributeMutationsUploaded("named-user-id", Collections.singletonList(namedUserMutation));

        List<AttributeMutation> allMutations = new ArrayList<>();
        allMutations.add(channelMutation);
        allMutations.add(namedUserMutation);
        assertEquals(allMutations, historian.getAttributeHistory(100));

        assertEquals(Collections.singletonList(namedUserMutation), historian.getAttributeHistory(101));

        clock.currentTimeMillis = 201;
        assertEquals(Collections.emptyList(), historian.getAttributeHistory(201));
    }

    @Test
    public void getAttributeHistoryDifferentNamedUser() {
        when(mockNamedUser.getId()).thenReturn("foo");

        clock.currentTimeMillis = 100;

        AttributeMutation fooMutation = AttributeMutation.newSetAttributeMutation("foo attribute", JsonValue.wrapOpt("some value"), 100);
        namedUserAttributeListener.onAttributeMutationsUploaded("foo", Collections.singletonList(fooMutation));

        AttributeMutation barMutation = AttributeMutation.newSetAttributeMutation("bar attribute", JsonValue.wrapOpt("some value"), 100);
        namedUserAttributeListener.onAttributeMutationsUploaded("bar", Collections.singletonList(barMutation));

        assertEquals(Collections.singletonList(fooMutation), historian.getAttributeHistory(0));
    }
}
