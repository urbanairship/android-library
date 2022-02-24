/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.ShadowAirshipExecutorsLegacy;
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
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

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
@Config(
        sdk = 28,
        shadows = { ShadowAirshipExecutorsLegacy.class },
        application = TestApplication.class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4.class)
public class AudienceManagerTest {

    private AudienceManager manager;

    private AirshipChannel mockChannel;
    private AudienceHistorian mockHistorian;
    private Contact mockContact;

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

        mockChannel = mock(AirshipChannel.class);
        mockContact = mock(Contact.class);

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
        manager = new AudienceManager(mockChannel, mockContact,
                mockHistorian, TestApplication.getApplication().preferenceDataStore, clock);

        callback = new TestCallback();

        // Request the current tags
        callback.tags = callbackResponseTags;
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

}
