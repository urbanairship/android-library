/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import android.content.Context;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.UAirship;
import com.urbanairship.audience.AudienceOverrides;
import com.urbanairship.audience.AudienceOverridesProvider;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.channel.AttributeEditor;
import com.urbanairship.channel.AttributeListener;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.channel.TagGroupListener;
import com.urbanairship.channel.TagGroupsEditor;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.CachedValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class ContactTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeContactId = "fake_contact_id";

    private final JobDispatcher mockDispatcher = mock(JobDispatcher.class);
    private final AirshipChannel mockChannel = mock(AirshipChannel.class);
    private final ContactApiClient mockContactApiClient = mock(ContactApiClient.class);
    private final TestClock testClock = new TestClock();
    private final TestActivityMonitor testActivityMonitor = new TestActivityMonitor();
    private final TagGroupListener tagGroupListener = mock(TagGroupListener.class);
    private final AttributeListener attributeListener = mock(AttributeListener.class);
    private final CachedValue<Map<String, Set<Scope>>> subscriptionCache = new CachedValue<>(testClock);
    private final JobInfo updateJob = JobInfo.newBuilder().setAction(Contact.ACTION_UPDATE_CONTACT).build();
    private final AudienceOverridesProvider audienceOverridesProvider = new AudienceOverridesProvider();

    private Contact contact;
    private PrivacyManager privacyManager;
    private PreferenceDataStore dataStore;

    @Before
    public void setUp() {
        Context context = TestApplication.getApplication();
        dataStore = TestApplication.getApplication().preferenceDataStore;
        privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);

        contact = new Contact(context, dataStore, mockDispatcher, privacyManager, mockChannel,
                mockContactApiClient, testActivityMonitor, testClock, subscriptionCache,
                audienceOverridesProvider, command -> command.run());
    }

    @After
    public void tearDown() {
        dataStore.tearDown();
    }

    @Test
    public void testChannelCreated() {
        ArgumentCaptor<AirshipChannelListener> argument = ArgumentCaptor.forClass(AirshipChannelListener.class);
        contact.init();
        verify(mockChannel).addChannelListener(argument.capture());
        AirshipChannelListener listener = argument.getValue();
        assertNotNull(listener);

        clearInvocations(mockDispatcher);

        when(mockChannel.getId()).thenReturn(fakeChannelId);
        listener.onChannelCreated(fakeChannelId);

        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(Contact.ACTION_UPDATE_CONTACT)));
    }

    @Test
    public void testExtendChannelRegistration() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        // Set up a 200 response
        Response<ContactIdentity> response = new Response<>(
                200,
                new ContactIdentity(fakeContactId, true, null)
        );
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        ArgumentCaptor<AirshipChannel.ChannelRegistrationPayloadExtender> argument = ArgumentCaptor.forClass(AirshipChannel.ChannelRegistrationPayloadExtender.class);
        contact.init();
        verify(mockChannel).addChannelRegistrationPayloadExtender(argument.capture());

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();
        ChannelRegistrationPayload payload = argument.getValue().extend(builder).build();
        assertEquals(fakeContactId, payload.contactId);
    }

    @Test
    public void testForeground() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up fixed time
        testClock.currentTimeMillis = 0;

        contact.init();

        // Set up a 200 response
        Response<ContactIdentity> response = new Response<>(
                200,
                new ContactIdentity(fakeContactId, true, null)
        );
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();
        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(Contact.ACTION_UPDATE_CONTACT);
            }
        }));

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
        clearInvocations(mockDispatcher);

        // Fix time just before 24hrs
        testClock.currentTimeMillis = 24 * 60 * 60 * 1000 - 1;

        testActivityMonitor.foreground();

        verify(mockDispatcher, times(0)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(Contact.ACTION_UPDATE_CONTACT);
            }
        }));
        clearInvocations(mockDispatcher);

        // Fix time at 24hrs
        testClock.currentTimeMillis = 24 * 60 * 60 * 1000;

        testActivityMonitor.foreground();

        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(Contact.ACTION_UPDATE_CONTACT);
            }
        }));
    }

    @Test
    public void testResolveSucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
    }

    @Test
    public void testResolveFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 500 response
        Response<ContactIdentity> response = new Response<>(500, null);
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
    }

    @Test
    public void testResolveHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response<>(403, null);
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
    }

    @Test
    public void testResetSucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).reset(fakeChannelId);
    }

    @Test
    public void testResetFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 500 response
        Response<ContactIdentity> response = new Response<>(500, null);
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).reset(fakeChannelId);
    }

    @Test
    public void testResetHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response<>(403, null);
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).reset(fakeChannelId);
    }

    @Test
    public void testIdentifySucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response<>(200, new ContactIdentity(fakeContactId, false, fakeNamedUserId));
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);

        assertNull(contact.getLastContactIdentity());
        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);

        assertEquals(fakeContactId, contact.getLastContactIdentity().getContactId());
        assertEquals(fakeNamedUserId, contact.getLastContactIdentity().getNamedUserId());

        verify(mockChannel).updateRegistration();
    }

    @Test
    public void testIdentifySkippedIfKnown() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response

        Response<ContactIdentity> response = new Response<>(200, new ContactIdentity(fakeContactId, false, fakeNamedUserId));
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);
        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);
        assertNotNull(contact.getLastContactIdentity());

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verifyNoMoreInteractions(mockContactApiClient);

        assertEquals(fakeContactId, contact.getLastContactIdentity().getContactId());
        assertFalse(contact.getLastContactIdentity().isAnonymous());
        assertEquals(fakeNamedUserId, contact.getNamedUserId());
        verify(mockChannel).updateRegistration();
    }

    @Test
    public void testIdentifyDoesntSkipReset() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        final String resetContactId = "some other fake id";

        Response<ContactIdentity> identifyResponse = new Response<>(200, new ContactIdentity(fakeContactId, false, fakeNamedUserId));
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(identifyResponse);
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, resetContactId)).thenReturn(identifyResponse);

        Response<ContactIdentity> resetResponse = new Response<>(200, new ContactIdentity("some other fake id", true, null));
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(resetResponse);

        contact.identify(fakeNamedUserId);
        contact.reset();
        contact.reset();
        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient, times(1)).identify(fakeNamedUserId, fakeChannelId, null);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient, times(1)).reset(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient, times(1)).identify(fakeNamedUserId, fakeChannelId, resetContactId);
        assertNotNull(contact.getLastContactIdentity());

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verifyNoMoreInteractions(mockContactApiClient);
    }

    @Test
    public void testUpdateDoesntSkipReset() throws RequestException {
        contact.init();
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        final String resetContactId = "some other fake id";

        // Set up a 200 response
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response<>(200, null);
        when(mockContactApiClient.update(eq(fakeContactId), anyList(), anyList(), anyList())).thenReturn(updateResponse);
        when(mockContactApiClient.update(eq(resetContactId), anyList(), anyList(), anyList())).thenReturn(updateResponse);

        Response<ContactIdentity> resetResponse = new Response<>(200, new ContactIdentity(resetContactId, true, null));
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(resetResponse);

        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply();

        // Resolve
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient, times(1)).resolve(fakeChannelId);

        List<ScopedSubscriptionListMutation> pending = contact.getPendingAudienceOverrides(fakeContactId).getSubscriptions();

        contact.reset();
        contact.reset();
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient, times(1)).update(fakeContactId, Collections.emptyList(), Collections.emptyList(), pending);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient, times(1)).reset(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient, times(1)).update(resetContactId, Collections.emptyList(), Collections.emptyList(), pending);
        assertNotNull(contact.getLastContactIdentity());

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verifyNoMoreInteractions(mockContactApiClient);
    }

    @Test
    public void testIdentifyFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 500 response
        Response<ContactIdentity> response = new Response<>(500, null);
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);

        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);
    }

    @Test
    public void testIdentifyHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response<>(403, null);
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);

        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);
    }

    @Test
    public void testEditTagsSucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        TagGroupsEditor tagGroupsEditor = contact.editTagGroups().setTag("group", "tag");

        TagGroupsMutation mutation = TagGroupsMutation.newSetTagsMutation("group", new HashSet<>(Lists.newArrayList("tag")));
        List<TagGroupsMutation> tagGroupsMutations = new ArrayList<>();
        tagGroupsMutations.add(mutation);
        tagGroupsMutations = TagGroupsMutation.collapseMutations(tagGroupsMutations);

        // Set up a 200 response
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response<>(200, null);
        when(mockContactApiClient.update(fakeContactId, tagGroupsMutations, Collections.emptyList(), Collections.emptyList())).thenReturn(updateResponse);

        tagGroupsEditor.apply();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(fakeContactId, tagGroupsMutations, Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void testEditSubscriptionLists() throws RequestException, JsonException {
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        ScopedSubscriptionListMutation expectedMutation = ScopedSubscriptionListMutation.newSubscribeMutation("some list", Scope.APP, testClock.currentTimeMillis);
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply();

        // Resolve so we can query the pending items
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        List<ScopedSubscriptionListMutation> pending = contact.getPendingAudienceOverrides(fakeContactId).getSubscriptions();
        assertEquals(1, pending.size());
        assertEquals(expectedMutation, pending.get(0));

        // Set up a 200 response
        Response<Void> updateResponse = new Response<>(200, null);
        when(mockContactApiClient.update(fakeContactId, Collections.emptyList(), Collections.emptyList(), pending)).thenReturn(updateResponse);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(fakeContactId, Collections.emptyList(), Collections.emptyList(), pending);

        assertTrue(contact.getPendingAudienceOverrides(fakeContactId).getSubscriptions().isEmpty());

        // Verify that the local history was updated
        assertEquals(Collections.singletonList(expectedMutation), audienceOverridesProvider.contactOverridesSync(fakeContactId).getSubscriptions());
    }

    @Test
    public void testCollapseUpdates() throws RequestException {
        List<ScopedSubscriptionListMutation> expectedSubscriptions = Arrays.asList(
                ScopedSubscriptionListMutation.newUnsubscribeMutation("some list", Scope.APP, testClock.currentTimeMillis),
                ScopedSubscriptionListMutation.newSubscribeMutation("some list", Scope.APP, testClock.currentTimeMillis)
        );
        expectedSubscriptions = ScopedSubscriptionListMutation.collapseMutations(expectedSubscriptions);

        List<AttributeMutation> expectedAttributes = Arrays.asList(
                AttributeMutation.newRemoveAttributeMutation("some attribute", testClock.currentTimeMillis),
                AttributeMutation.newSetAttributeMutation("some attribute", JsonValue.wrap("some value"), testClock.currentTimeMillis)
        );
        expectedAttributes = AttributeMutation.collapseMutations(expectedAttributes);

        List<TagGroupsMutation> expectedTags = Arrays.asList(
                TagGroupsMutation.newAddTagsMutation("some group", Collections.singleton("some tag")),
                TagGroupsMutation.newRemoveTagsMutation("some group", Collections.singleton("some tag"))
        );
        expectedTags = TagGroupsMutation.collapseMutations(expectedTags);

        contact.editSubscriptionLists().unsubscribe("some list", Scope.APP).apply();
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply();

        contact.editAttributes().removeAttribute("some attribute").apply();
        contact.editAttributes().setAttribute("some attribute", "some value").apply();

        contact.editTagGroups().addTag("some group", "some tag").apply();
        contact.editTagGroups().removeTag("some group", "some tag").apply();

        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        Response<Void> updateResponse = new Response<>(200, null);
        when(mockContactApiClient.update(any(), anyList(), anyList(), anyList())).thenReturn(updateResponse);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(fakeContactId, expectedTags, expectedAttributes, expectedSubscriptions);
    }

    @Test
    public void testEditAttributesSucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        AttributeEditor attributeEditor = contact.editAttributes().setAttribute("one_attribute", "attribute_value");

        AttributeMutation mutation = AttributeMutation.newSetAttributeMutation("one_attribute", JsonValue.wrap("attribute_value"), testClock.currentTimeMillis());
        List<AttributeMutation> attributeMutations = new ArrayList<>();
        attributeMutations.add(mutation);
        attributeMutations = AttributeMutation.collapseMutations(attributeMutations);

        // Set up a 200 response
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response<>(200, null);
        when(mockContactApiClient.update(fakeContactId, Collections.emptyList(), attributeMutations, Collections.emptyList())).thenReturn(updateResponse);

        attributeEditor.apply();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(fakeContactId, Collections.emptyList(), attributeMutations, Collections.emptyList());
    }

    @Test
    public void onConflictIdentify() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        contact.editAttributes()
               .setAttribute("one_attribute", "attribute_value")
               .apply();

        contact.editTagGroups()
               .setTag("group", "tag")
               .apply();

        contact.editSubscriptionLists()
               .subscribe("some list", Scope.SMS)
               .apply();

        ContactConflictListener conflictListener = mock(ContactConflictListener.class);
        contact.setContactConflictListener(conflictListener);

        // Set up responses
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity("some_contact_id", true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response<>(200, null);
        when(mockContactApiClient.update(eq("some_contact_id"), anyList(), anyList(), anyList())).thenReturn(updateResponse);

        Response<ContactIdentity> response = new Response<>(200, new ContactIdentity("some_other_contact_id", false, fakeNamedUserId));
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, "some_contact_id")).thenReturn(response);

        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        verify(mockContactApiClient).resolve(fakeChannelId);
        verify(mockContactApiClient).update(eq("some_contact_id"), anyList(), anyList(), anyList());
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, "some_contact_id");

        ArgumentCaptor<ContactData> argument = ArgumentCaptor.forClass(ContactData.class);
        verify(conflictListener).onConflict(argument.capture(), eq(fakeNamedUserId));

        Map<String, JsonValue> expectedAttributes = Collections.singletonMap("one_attribute", JsonValue.wrap("attribute_value"));
        Map<String, Set<String>> expectedTags = Collections.singletonMap("group", Collections.singleton("tag"));
        Map<String, Set<Scope>> expectedSubscriptionLists = Collections.singletonMap("some list", Collections.singleton(Scope.SMS));

        ContactData expectedContactData = new ContactData(expectedAttributes, expectedTags, Collections.emptyList(), expectedSubscriptionLists);
        assertEquals(expectedContactData, argument.getValue());
    }

    @Test
    public void testGetPendingTagUpdates() throws RequestException {
        contact.editTagGroups()
               .setTag("group", "tag")
               .apply();

        contact.editTagGroups()
               .addTag("some-other-group", "some-tag")
               .apply();

        // Resolve so we can query pending
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        List<TagGroupsMutation> expected = new ArrayList<>();
        expected.add(TagGroupsMutation.newSetTagsMutation("group", Collections.singleton("tag")));
        expected.add(TagGroupsMutation.newAddTagsMutation("some-other-group", Collections.singleton("some-tag")));

        assertEquals(expected, contact.getPendingAudienceOverrides(fakeContactId).getTags());
    }

    @Test
    public void testGetPendingAttributes() throws RequestException {
        testClock.currentTimeMillis = 100;
        contact.editAttributes()
               .setAttribute("cool", "story")
               .removeAttribute("some-attribute")
               .apply();

        contact.editAttributes()
               .setAttribute("something", "neat")
               .apply();

        // Resolve so we can query pending
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        List<AttributeMutation> expected = new ArrayList<>();
        expected.add(AttributeMutation.newSetAttributeMutation("cool", JsonValue.wrap("story"), 100));
        expected.add(AttributeMutation.newRemoveAttributeMutation("some-attribute", 100));
        expected.add(AttributeMutation.newSetAttributeMutation("something", JsonValue.wrap("neat"), 100));

        assertEquals(expected, contact.getPendingAudienceOverrides(fakeContactId).getAttributes());
    }

    @Test
    public void testGetPendingSubscriptionLists() throws RequestException {
        testClock.currentTimeMillis = 100;

        contact.editSubscriptionLists()
               .subscribe("cool", Scope.SMS)
               .unsubscribe("cool", Scope.APP)
               .apply();

        contact.editSubscriptionLists()
               .subscribe("something", Scope.EMAIL)
               .apply();

        // Resolve so we can query pending
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        List<ScopedSubscriptionListMutation> expected = Arrays.asList(
                ScopedSubscriptionListMutation.newSubscribeMutation("cool", Scope.SMS, 100L),
                ScopedSubscriptionListMutation.newUnsubscribeMutation("cool", Scope.APP, 100L),
                ScopedSubscriptionListMutation.newSubscribeMutation("something", Scope.EMAIL, 100L)
        );

        assertEquals(expected, contact.getPendingAudienceOverrides(fakeContactId).getSubscriptions());
    }

    @Test
    public void testMigrate() throws RequestException {
        PreferenceDataStore dataStore = TestApplication.getApplication().preferenceDataStore;
        dataStore.put(Contact.LEGACY_NAMED_USER_ID_KEY, "some-named-user");

        List<TagGroupsMutation> pendingTags = new ArrayList<>();
        pendingTags.add(TagGroupsMutation.newSetTagsMutation("group", Collections.singleton("tag")));
        pendingTags.add(TagGroupsMutation.newAddTagsMutation("some-other-group", Collections.singleton("some-tag")));

        dataStore.put(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY, JsonValue.wrapOpt(pendingTags));

        List<AttributeMutation> pendingAttributes = new ArrayList<>();
        pendingAttributes.add(AttributeMutation.newSetAttributeMutation("cool", JsonValue.wrap("story"), 100));
        pendingAttributes.add(AttributeMutation.newRemoveAttributeMutation("some-attribute", 100));
        pendingAttributes.add(AttributeMutation.newSetAttributeMutation("something", JsonValue.wrap("neat"), 100));

        dataStore.put(Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY, JsonValue.wrapOpt(pendingAttributes));

        contact.init();

        // Identify so we can query pending
        Response<ContactIdentity> identityResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, "some-named-user"));
        when(mockContactApiClient.identify("some-named-user", fakeChannelId, null)).thenReturn(identityResponse);
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        assertEquals("some-named-user", contact.getNamedUserId());
        assertEquals(pendingAttributes, contact.getPendingAudienceOverrides(fakeContactId).getAttributes());
        assertEquals(pendingTags, contact.getPendingAudienceOverrides(fakeContactId).getTags());

        assertFalse(dataStore.isSet(Contact.LEGACY_NAMED_USER_ID_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY));
    }

    @Test
    public void testMigrateAttributesAndTagsDisabled() throws RequestException {
        PreferenceDataStore dataStore = TestApplication.getApplication().preferenceDataStore;
        privacyManager.disable(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES);

        dataStore.put(Contact.LEGACY_NAMED_USER_ID_KEY, "some-named-user");

        List<TagGroupsMutation> pendingTags = new ArrayList<>();
        pendingTags.add(TagGroupsMutation.newSetTagsMutation("group", Collections.singleton("tag")));
        dataStore.put(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY, JsonValue.wrapOpt(pendingTags));

        List<AttributeMutation> pendingAttributes = new ArrayList<>();
        pendingAttributes.add(AttributeMutation.newSetAttributeMutation("cool", JsonValue.wrap("story"), 100));
        dataStore.put(Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY, JsonValue.wrapOpt(pendingAttributes));

        contact.init();

        assertEquals("some-named-user", contact.getNamedUserId());

        // Identify so we can query pending
        Response<ContactIdentity> identityResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, "some-named-user"));
        when(mockContactApiClient.identify("some-named-user", fakeChannelId, null)).thenReturn(identityResponse);
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        assertTrue(contact.getPendingAudienceOverrides(fakeContactId).getAttributes().isEmpty());
        assertTrue(contact.getPendingAudienceOverrides(fakeContactId).getSubscriptions().isEmpty());

        assertFalse(dataStore.isSet(Contact.LEGACY_NAMED_USER_ID_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY));
    }

    @Test
    public void testGetSubscriptionListsFromCache() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        Map<String, Set<Scope>> subscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.APP));
            put("bar", Collections.singleton(Scope.SMS));
        }};

        assertNull(subscriptionCache.get());

        subscriptionCache.set(subscriptions, 100);

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists();

        result.addResultCallback(result1 -> {
            assertEquals(subscriptions, result1);
        });

        verify(mockContactApiClient, never()).getSubscriptionLists(anyString());
    }

    @Test
    public void testGetSubscriptionListsNamedContact() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, false, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        contact.init();

        // Always return empty list from server
        when(mockContactApiClient.getSubscriptionLists(fakeContactId))
                .thenReturn(new Response<>(200, new HashMap<>()));

        // Updates
        when(mockContactApiClient.update(eq(fakeContactId), anyList(), anyList(), anyList()))
                .thenReturn(new Response<>(200, null));

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        // Apply subscriptions
        contact.editSubscriptionLists()
               .subscribe("foo", Scope.APP)
               .apply();

        // Pending
        Map<String, Set<Scope>> expected = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.APP));
        }};

        assertEquals(expected, contact.getSubscriptionLists().get());

        // Send updates
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        // Check local history is applied
        assertEquals(expected, contact.getSubscriptionLists().get());
    }

    @Test
    public void testGetSubscriptionListsFromNetworkIfCacheExpired() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        Map<String, Set<Scope>> cachedSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.APP));
            put("bar", Collections.singleton(Scope.SMS));
        }};

        Map<String, Set<Scope>> networkSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.SMS));
            put("buzz", Collections.singleton(Scope.SMS));
        }};

        when(mockContactApiClient.getSubscriptionLists(fakeContactId))
                .thenReturn(new Response<>(200, networkSubscriptions));

        // Prime the cache
        testClock.currentTimeMillis = 100;
        subscriptionCache.set(cachedSubscriptions, 10);

        // Advance the time past the subscription cache lifetime
        testClock.currentTimeMillis += 10;
        assertNull(subscriptionCache.get());

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists();
        result.addResultCallback(result1 -> {
            assertEquals(networkSubscriptions, result1);

            // Verify that the cache was updated
            assertEquals(networkSubscriptions, subscriptionCache.get());
        });

        verify(mockContactApiClient).getSubscriptionLists(fakeContactId);
    }

    @Test
    public void testGetSubscriptionListsFromNetworkIfCacheEmpty() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        Map<String, Set<Scope>> networkSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.SMS));
            put("buzz", Collections.singleton(Scope.SMS));
        }};

        when(mockContactApiClient.getSubscriptionLists(fakeContactId))
                .thenReturn(new Response<>(200, networkSubscriptions));

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists();
        result.addResultCallback(result1 -> {
            assertEquals(networkSubscriptions, result1);

            // Verify that the cache was updated
            assertEquals(networkSubscriptions, subscriptionCache.get());
        });

        verify(mockContactApiClient).getSubscriptionLists(fakeContactId);
    }

    @Test
    public void testGetSubscriptionListsIncludesPending() throws RequestException, ExecutionException, InterruptedException {
        contact.init();

        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        contact.editSubscriptionLists()
               .subscribe("fizz", Scope.SMS)
               .unsubscribe("bar", Scope.APP)
               .apply();

        Map<String, Set<Scope>> networkSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", new HashSet<>(Collections.singleton(Scope.SMS)));
            put("bar", new HashSet<>(Collections.singleton(Scope.APP)));
        }};

        when(mockContactApiClient.getSubscriptionLists(fakeContactId))
                .thenReturn(new Response<>(200, networkSubscriptions));

        Map<String, Set<Scope>> expectedSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.SMS));
            put("fizz", Collections.singleton(Scope.SMS));
        }};

        Map<String, Set<Scope>> result = contact.getSubscriptionLists().get();
        assertEquals(expectedSubscriptions, result);

        assertEquals(networkSubscriptions, subscriptionCache.get());
        verify(mockContactApiClient).getSubscriptionLists(fakeContactId);
    }

    @Test
    public void testGetSubscriptionNoContact() throws RequestException, ExecutionException, InterruptedException {
        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists();
        assertNull(result.get());
        verify(mockContactApiClient, never()).getSubscriptionLists(anyString());
    }

    @Test
    public void testGetSubscriptionPendingReset() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, false, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        contact.reset();

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists();
        assertNull(result.get());
        verify(mockContactApiClient, never()).getSubscriptionLists(anyString());
    }

    @Test
    public void testGetSubscriptionPendingIdentify() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, "some user id"));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        Map<String, Set<Scope>> subscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.APP));
            put("bar", Collections.singleton(Scope.SMS));
        }};
        subscriptionCache.set(subscriptions, 100);

        // Verify we have results
        assertEquals(subscriptions, contact.getSubscriptionLists().get());

        // Identify same named user, still have results
        contact.identify("some user id");
        assertEquals(subscriptions, contact.getSubscriptionLists().get());

        // Different named user, results should be null
        contact.identify("some other user id");
        assertNull(contact.getSubscriptionLists().get());
    }

    @Test
    public void testGetSubscriptionErrorResult() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        when(mockContactApiClient.getSubscriptionLists(fakeContactId))
                .thenReturn(new Response<>(200, null));

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists();
        result.addResultCallback(result1 -> {
            assertNull(result1);
            assertNull(subscriptionCache.get());
        });

        verify(mockContactApiClient).getSubscriptionLists(fakeContactId);
    }

    @Test
    public void testRegisterEmail() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        JsonMap properties = JsonMap.newBuilder().put("place", "paris").build();
        EmailRegistrationOptions options = EmailRegistrationOptions.commercialOptions(new Date(), new Date(), properties);

        Response<AssociatedChannel> registerResponse = new Response<>(200, new AssociatedChannel("email-channel-id", ChannelType.EMAIL));
        when(mockContactApiClient.registerEmail(eq(fakeContactId), eq("ua@airship.com"), any(EmailRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerEmail("ua@airship.com", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerEmail(eq(fakeContactId), eq("ua@airship.com"), any(EmailRegistrationOptions.class));
    }

    @Test
    public void testRegisterEmailFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        JsonMap properties = JsonMap.newBuilder().put("place", "paris").build();
        EmailRegistrationOptions options = EmailRegistrationOptions.commercialOptions(new Date(), new Date(), properties);

        Response<AssociatedChannel> registerResponse = new Response<>(500, null);
        when(mockContactApiClient.registerEmail(eq(fakeContactId), eq("ua@airship.com"), Mockito.any(EmailRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerEmail("ua@airship.com", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerEmail(eq(fakeContactId), eq("ua@airship.com"), Mockito.any(EmailRegistrationOptions.class));
    }

    @Test
    public void testRegisterSms() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        SmsRegistrationOptions options = SmsRegistrationOptions.options("12345");

        Response<AssociatedChannel> registerResponse = new Response<>(200, new AssociatedChannel("sms-channel-id", ChannelType.SMS));
        when(mockContactApiClient.registerSms(eq(fakeContactId), eq("12345678"), any(SmsRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerSms("12345678", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerSms(eq(fakeContactId), eq("12345678"), any(SmsRegistrationOptions.class));
    }

    @Test
    public void testRegisterSmsFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        SmsRegistrationOptions options = SmsRegistrationOptions.options("12345");

        Response<AssociatedChannel> registerResponse = new Response<>(500, null);
        when(mockContactApiClient.registerSms(eq(fakeContactId), eq("12345678"), Mockito.any(SmsRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerSms("12345678", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerSms(eq(fakeContactId), eq("12345678"), Mockito.any(SmsRegistrationOptions.class));
    }

    @Test
    public void testRegisterOpen() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        OpenChannelRegistrationOptions options = OpenChannelRegistrationOptions.options("platform-name", Collections.singletonMap("number", "1"));

        Response<AssociatedChannel> registerResponse = new Response<>(200, new AssociatedChannel("open-channel-id", ChannelType.OPEN));
        when(mockContactApiClient.registerOpenChannel(eq(fakeContactId), eq("open-channel-address"), any(OpenChannelRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerOpenChannel("open-channel-address", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerOpenChannel(eq(fakeContactId), eq("open-channel-address"), any(OpenChannelRegistrationOptions.class));
    }

    @Test
    public void testRegisterOpenFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        OpenChannelRegistrationOptions options = OpenChannelRegistrationOptions.options("platform-name", Collections.singletonMap("number", "1"));

        Response<AssociatedChannel> registerResponse = new Response<>(500, null);
        when(mockContactApiClient.registerOpenChannel(eq(fakeContactId), eq("open-channel-address"), Mockito.any(OpenChannelRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerOpenChannel("open-channel-address", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerOpenChannel(eq(fakeContactId), eq("open-channel-address"), Mockito.any(OpenChannelRegistrationOptions.class));
    }

    @Test
    public void testAssociateChannel() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<AssociatedChannel> associatedResponse = new Response<>(200, new AssociatedChannel("new-fake-channel-id", ChannelType.EMAIL));
        when(mockContactApiClient.associatedChannel(fakeContactId, "new-fake-channel-id", ChannelType.EMAIL)).thenReturn(associatedResponse);

        contact.associateChannel("new-fake-channel-id", ChannelType.EMAIL);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).associatedChannel(fakeContactId, "new-fake-channel-id", ChannelType.EMAIL);
    }

    @Test
    public void testAssociateChannelFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<AssociatedChannel> associatedResponse = new Response<>(500, null);
        when(mockContactApiClient.associatedChannel(fakeContactId, "new-fake-channel-id", ChannelType.EMAIL)).thenReturn(associatedResponse);

        contact.associateChannel("new-fake-channel-id", ChannelType.EMAIL);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).associatedChannel(fakeContactId, "new-fake-channel-id", ChannelType.EMAIL);
    }

    @Test
    public void testOverrides() throws RequestException {
        contact.init();
        contact.editSubscriptionLists()
               .subscribe("app 1", Scope.APP)
               .apply();

        contact.editAttributes().setAttribute("neat", "story").apply();
        contact.editTagGroups().addTag("foo", "bar").apply();

        // Resolve so we can query the pending items
        Response<ContactIdentity> resolveResponse = new Response<>(200, new ContactIdentity(fakeContactId, true, null));
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);


        AudienceOverrides.Contact audienceOverrides = audienceOverridesProvider.contactOverridesSync(fakeContactId);

        AudienceOverrides.Contact expected = new AudienceOverrides.Contact(
                Collections.singletonList(
                        TagGroupsMutation.newAddTagsMutation("foo", Collections.singleton("bar"))
                ),
                Collections.singletonList(
                        AttributeMutation.newSetAttributeMutation("neat", JsonValue.wrap("story"), testClock.currentTimeMillis)
                ),
                Collections.singletonList(
                        ScopedSubscriptionListMutation.newSubscribeMutation("app 1", Scope.APP, testClock.currentTimeMillis)
                )
        );

        assertEquals(expected, audienceOverrides);
    }

}
