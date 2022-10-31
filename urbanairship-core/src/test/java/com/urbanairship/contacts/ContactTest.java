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
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.channel.AttributeEditor;
import com.urbanairship.channel.AttributeListener;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.channel.SubscriptionListMutation;
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
import org.mockito.ArgumentMatchers;
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
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final ContactChangeListener changeListener = mock(ContactChangeListener.class);
    private final TagGroupListener tagGroupListener = mock(TagGroupListener.class);
    private final AttributeListener attributeListener = mock(AttributeListener.class);
    private final CachedValue<Map<String, Set<Scope>>> subscriptionCache = new CachedValue<>(testClock);
    private final List<CachedValue<ScopedSubscriptionListMutation>> subscriptionListLocalHistory = new CopyOnWriteArrayList<>();

    private final JobInfo updateJob = JobInfo.newBuilder().setAction(Contact.ACTION_UPDATE_CONTACT).build();

    private Contact contact;
    private PrivacyManager privacyManager;
    private PreferenceDataStore dataStore;

    @Before
    public void setUp() {
        Context context = TestApplication.getApplication();
        dataStore = TestApplication.getApplication().preferenceDataStore;
        privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);

        contact = new Contact(context, dataStore, mockDispatcher, privacyManager, mockChannel,
                mockContactApiClient, testActivityMonitor, testClock, subscriptionCache, subscriptionListLocalHistory,
                command -> command.run());
        contact.addContactChangeListener(changeListener);
        contact.addTagGroupListener(tagGroupListener);
        contact.addAttributeListener(attributeListener);
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
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
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
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
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
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        verify(changeListener).onContactChanged();
    }

    @Test
    public void testResolveFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 500 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(500).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
    }

    @Test
    public void testResolveHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(403).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
    }

    @Test
    public void testResetSucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).reset(fakeChannelId);
        verify(changeListener).onContactChanged();
    }

    @Test
    public void testResetFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 500 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(500).build();
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).reset(fakeChannelId);
    }

    @Test
    public void testResetHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(403).build();
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).reset(fakeChannelId);
    }

    @Test
    public void testIdentifySucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, false, fakeNamedUserId)).build();
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);

        assertNull(contact.getLastContactIdentity());
        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);

        assertEquals(fakeContactId, contact.getLastContactIdentity().getContactId());
        assertEquals(fakeNamedUserId, contact.getLastContactIdentity().getNamedUserId());

        verify(mockChannel).updateRegistration();

        verify(changeListener).onContactChanged();
    }

    @Test
    public void testIdentifySkippedIfKnown() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, false, fakeNamedUserId)).build();
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

        Response<ContactIdentity> identifyResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, false, fakeNamedUserId)).build();
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(identifyResponse);
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, resetContactId)).thenReturn(identifyResponse);

        Response<ContactIdentity> resetResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity("some other fake id", true, null)).build();
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
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        final String resetContactId = "some other fake id";

        // Set up a 200 response
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response.Builder<Void>(200).build();
        when(mockContactApiClient.update(eq(fakeContactId), anyList(), anyList(), anyList())).thenReturn(updateResponse);
        when(mockContactApiClient.update(eq(resetContactId), anyList(), anyList(), anyList())).thenReturn(updateResponse);

        Response<ContactIdentity> resetResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(resetContactId, true, null)).build();
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(resetResponse);

        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply();
        List<ScopedSubscriptionListMutation> pending = contact.getPendingSubscriptionListUpdates();

        contact.reset();
        contact.reset();
        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply();

        // Resolve
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient, times(1)).resolve(fakeChannelId);

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
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>((500)).build();
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);

        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);
    }

    @Test
    public void testIdentifyHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>((403)).build();
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
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response.Builder<Void>(200).build();
        when(mockContactApiClient.update(fakeContactId, tagGroupsMutations, Collections.emptyList(), Collections.emptyList())).thenReturn(updateResponse);

        tagGroupsEditor.apply();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(fakeContactId, tagGroupsMutations, Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void testEditSubscriptionLists() throws RequestException, JsonException {
        ScopedSubscriptionListMutation expectedMutation = ScopedSubscriptionListMutation.newSubscribeMutation("some list", Scope.APP, testClock.currentTimeMillis);

        assertEquals(0, subscriptionListLocalHistory.size());

        contact.editSubscriptionLists().subscribe("some list", Scope.APP).apply();

        List<ScopedSubscriptionListMutation> pending = contact.getPendingSubscriptionListUpdates();
        assertEquals(1, pending.size());
        assertEquals(expectedMutation, pending.get(0));

        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response.Builder<Void>(200).build();
        when(mockContactApiClient.update(fakeContactId, Collections.emptyList(), Collections.emptyList(), pending)).thenReturn(updateResponse);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(fakeContactId, Collections.emptyList(), Collections.emptyList(), pending);

        assertTrue(contact.getPendingSubscriptionListUpdates().isEmpty());

        // Verify that the local history was updated
        assertEquals(1, subscriptionListLocalHistory.size());
        assertEquals(expectedMutation, subscriptionListLocalHistory.get(0).get());
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
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response.Builder<Void>(200).build();
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

        List<TagGroupsMutation> tagGroupsMutations = contact.getPendingTagUpdates();
        List<AttributeMutation> attributeMutations = contact.getPendingAttributeUpdates();
        List<ScopedSubscriptionListMutation> subscriptionListMutations = contact.getPendingSubscriptionListUpdates();

        ContactConflictListener conflictListener = mock(ContactConflictListener.class);
        contact.setContactConflictListener(conflictListener);

        // Set up responses
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity("some_contact_id", true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response.Builder<Void>(200).build();
        when(mockContactApiClient.update("some_contact_id", tagGroupsMutations, attributeMutations, subscriptionListMutations)).thenReturn(updateResponse);

        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity("some_other_contact_id", false, fakeNamedUserId)).build();
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, "some_contact_id")).thenReturn(response);

        contact.identify(fakeNamedUserId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        verify(mockContactApiClient).resolve(fakeChannelId);
        verify(mockContactApiClient).update("some_contact_id", tagGroupsMutations, attributeMutations, subscriptionListMutations);
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
    public void testAudienceListeners() throws RequestException {
        testClock.currentTimeMillis = 0;
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        contact.editTagGroups()
               .setTag("group", "tag")
               .apply();

        contact.editAttributes()
               .setAttribute("cool", "story")
               .apply();

        List<TagGroupsMutation> expectedTags = Collections.singletonList(TagGroupsMutation.newSetTagsMutation("group", Collections.singleton("tag")));
        List<AttributeMutation> expectedAttributes = Collections.singletonList(AttributeMutation.newSetAttributeMutation("cool", JsonValue.wrap("story"), testClock.currentTimeMillis));

        // Set up a 200 response
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response.Builder<Void>(200).build();
        when(mockContactApiClient.update(fakeContactId, expectedTags, expectedAttributes, Collections.emptyList())).thenReturn(updateResponse);

        // Resolve
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        // Update
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(anyString(), ArgumentMatchers.anyList(), ArgumentMatchers.anyList(), ArgumentMatchers.anyList());

        verify(tagGroupListener).onTagGroupsMutationUploaded(expectedTags);
        verify(attributeListener).onAttributeMutationsUploaded(expectedAttributes);

        assertTrue(contact.getPendingTagUpdates().isEmpty());
        assertTrue(contact.getPendingAttributeUpdates().isEmpty());
    }

    @Test
    public void testGetPendingTagUpdates() {
        contact.editTagGroups()
               .setTag("group", "tag")
               .apply();

        contact.editTagGroups()
               .addTag("some-other-group", "some-tag")
               .apply();

        List<TagGroupsMutation> expected = new ArrayList<>();
        expected.add(TagGroupsMutation.newSetTagsMutation("group", Collections.singleton("tag")));
        expected.add(TagGroupsMutation.newAddTagsMutation("some-other-group", Collections.singleton("some-tag")));

        assertEquals(expected, contact.getPendingTagUpdates());
    }

    @Test
    public void testGetPendingAttributes() {
        testClock.currentTimeMillis = 100;
        contact.editAttributes()
               .setAttribute("cool", "story")
               .removeAttribute("some-attribute")
               .apply();

        contact.editAttributes()
               .setAttribute("something", "neat")
               .apply();

        List<AttributeMutation> expected = new ArrayList<>();
        expected.add(AttributeMutation.newSetAttributeMutation("cool", JsonValue.wrap("story"), 100));
        expected.add(AttributeMutation.newRemoveAttributeMutation("some-attribute", 100));
        expected.add(AttributeMutation.newSetAttributeMutation("something", JsonValue.wrap("neat"), 100));

        assertEquals(expected, contact.getPendingAttributeUpdates());
    }

    @Test
    public void testGetPendingSubscriptionLists() {
        testClock.currentTimeMillis = 100;

        contact.editSubscriptionLists()
               .subscribe("cool", Scope.SMS)
               .unsubscribe("cool", Scope.APP)
               .apply();

        contact.editSubscriptionLists()
               .subscribe("something", Scope.EMAIL)
               .apply();

        List<ScopedSubscriptionListMutation> expected = Arrays.asList(
                ScopedSubscriptionListMutation.newSubscribeMutation("cool", Scope.SMS, 100L),
                ScopedSubscriptionListMutation.newUnsubscribeMutation("cool", Scope.APP, 100L),
                ScopedSubscriptionListMutation.newSubscribeMutation("something", Scope.EMAIL, 100L)
        );

        assertEquals(expected, contact.getPendingSubscriptionListUpdates());
    }

    @Test
    public void testMigrate() {
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

        assertEquals("some-named-user", contact.getNamedUserId());
        assertEquals(pendingAttributes, contact.getPendingAttributeUpdates());
        assertEquals(pendingTags, contact.getPendingTagUpdates());

        assertFalse(dataStore.isSet(Contact.LEGACY_NAMED_USER_ID_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY));
    }

    @Test
    public void testMigrateAttributesAndTagsDisabled() {
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
        assertTrue(contact.getPendingAttributeUpdates().isEmpty());
        assertTrue(contact.getPendingTagUpdates().isEmpty());

        assertFalse(dataStore.isSet(Contact.LEGACY_NAMED_USER_ID_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY));
    }

    @Test
    public void testMigrateContactDisabled() {
        PreferenceDataStore dataStore = TestApplication.getApplication().preferenceDataStore;
        privacyManager.disable(PrivacyManager.FEATURE_CONTACTS);

        dataStore.put(Contact.LEGACY_NAMED_USER_ID_KEY, "some-named-user");

        List<TagGroupsMutation> pendingTags = new ArrayList<>();
        pendingTags.add(TagGroupsMutation.newSetTagsMutation("group", Collections.singleton("tag")));
        dataStore.put(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY, JsonValue.wrapOpt(pendingTags));

        List<AttributeMutation> pendingAttributes = new ArrayList<>();
        pendingAttributes.add(AttributeMutation.newSetAttributeMutation("cool", JsonValue.wrap("story"), 100));
        dataStore.put(Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY, JsonValue.wrapOpt(pendingAttributes));

        contact.init();

        assertNull(contact.getNamedUserId());
        assertTrue(contact.getPendingAttributeUpdates().isEmpty());
        assertTrue(contact.getPendingTagUpdates().isEmpty());

        assertFalse(dataStore.isSet(Contact.LEGACY_NAMED_USER_ID_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_TAG_GROUP_MUTATIONS_KEY));
        assertFalse(dataStore.isSet(Contact.LEGACY_ATTRIBUTE_MUTATION_STORE_KEY));
    }

    @Test
    public void testGetSubscriptionListsFromCache() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
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

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists(false);

        result.addResultCallback(result1 -> {
            assertEquals(subscriptions, result1);
        });

        verify(mockContactApiClient, never()).getSubscriptionLists(anyString());
    }

    @Test
    public void testGetSubscriptionListsFromCacheWithLocalHistory() throws RequestException, JsonException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        Map<String, Set<Scope>> cachedSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", new HashSet<>(Collections.singleton(Scope.APP)));
            put("bar", new HashSet<>(Collections.singleton(Scope.SMS)));
        }};

        assertNull(subscriptionCache.get());

        subscriptionCache.set(cachedSubscriptions, 100);

        CachedValue<ScopedSubscriptionListMutation> localMutation1 = new CachedValue<>(testClock);
        localMutation1.set(ScopedSubscriptionListMutation.newSubscribeMutation("local", Scope.APP, 1000), 100);
        CachedValue<ScopedSubscriptionListMutation> localMutation2 = new CachedValue<>(testClock);
        localMutation2.set(ScopedSubscriptionListMutation.newUnsubscribeMutation("foo", Scope.APP, 1000), 100);

        assertEquals(0, subscriptionListLocalHistory.size());

        subscriptionListLocalHistory.addAll(Arrays.asList(localMutation1, localMutation2));

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists(false);

        Map<String, Set<Scope>> expectedSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("bar", new HashSet<>(Collections.singleton(Scope.SMS)));
            put("local", new HashSet<>(Collections.singleton(Scope.APP)));
        }};

        // Result should be available immediately when returning from the cache.
        assertEquals(expectedSubscriptions, result.get());

        verify(mockContactApiClient, never()).getSubscriptionLists(anyString());
    }

    @Test
    public void testGetSubscriptionListsNamedContact() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, false, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Always return empty list from server
        when(mockContactApiClient.getSubscriptionLists(fakeContactId)).thenReturn(new Response.Builder<Map<String, Set<Scope>>>(200)
                .setResult(new HashMap<>())
                .build()
        );

        // Updates
        when(mockContactApiClient.update(eq(fakeContactId), anyList(), anyList(), anyList()))
                .thenReturn(new Response.Builder<Void>(200).build());

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

        assertTrue(contact.getSubscriptionLists(false).get().isEmpty());
        assertEquals(expected, contact.getSubscriptionLists(true).get());

        // Send updates
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        // Check local history is applied
        assertEquals(expected, contact.getSubscriptionLists(true).get());
        assertEquals(expected, contact.getSubscriptionLists(false).get());
    }

    @Test
    public void testGetSubscriptionListsFromNetworkWithLocalHistory() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        Map<String, Set<Scope>> networkSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", new HashSet<>(Collections.singleton(Scope.SMS)));
            put("buzz", new HashSet<>(Collections.singleton(Scope.SMS)));
        }};

        when(mockContactApiClient.getSubscriptionLists(fakeContactId)).thenReturn(new Response.Builder<Map<String, Set<Scope>>>(200)
                .setResult(networkSubscriptions)
                .build()
        );

        CachedValue<ScopedSubscriptionListMutation> localMutation1 = new CachedValue<>(testClock);
        localMutation1.set(ScopedSubscriptionListMutation.newSubscribeMutation("local", Scope.APP, 1000), 100);
        CachedValue<ScopedSubscriptionListMutation> localMutation2 = new CachedValue<>(testClock);
        localMutation2.set(ScopedSubscriptionListMutation.newUnsubscribeMutation("foo", Scope.SMS, 1000), 100);

        assertEquals(0, subscriptionListLocalHistory.size());

        subscriptionListLocalHistory.addAll(Arrays.asList(localMutation1, localMutation2));

        Map<String, Set<Scope>> expectedSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("buzz", new HashSet<>(Collections.singleton(Scope.SMS)));
            put("local", new HashSet<>(Collections.singleton(Scope.APP)));
        }};

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists(false);
        result.addResultCallback(result1 -> {
            assertEquals(expectedSubscriptions, result1);

            // Verify that the cache was updated
            assertEquals(networkSubscriptions, subscriptionCache.get());
        });

        verify(mockContactApiClient).getSubscriptionLists(fakeContactId);
    }

    @Test
    public void testGetSubscriptionListsFromNetworkIfCacheExpired() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
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

        when(mockContactApiClient.getSubscriptionLists(fakeContactId)).thenReturn(new Response.Builder<Map<String, Set<Scope>>>(200)
                .setResult(networkSubscriptions)
                .build()
        );

        // Prime the cache
        testClock.currentTimeMillis = 100;
        subscriptionCache.set(cachedSubscriptions, 10);

        // Advance the time past the subscription cache lifetime
        testClock.currentTimeMillis += 10;
        assertNull(subscriptionCache.get());

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists(false);
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
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        Map<String, Set<Scope>> networkSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.SMS));
            put("buzz", Collections.singleton(Scope.SMS));
        }};

        when(mockContactApiClient.getSubscriptionLists(fakeContactId)).thenReturn(new Response.Builder<Map<String, Set<Scope>>>(200)
                .setResult(networkSubscriptions)
                .build()
        );

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists(false);
        result.addResultCallback(result1 -> {
            assertEquals(networkSubscriptions, result1);

            // Verify that the cache was updated
            assertEquals(networkSubscriptions, subscriptionCache.get());
        });

        verify(mockContactApiClient).getSubscriptionLists(fakeContactId);
    }

    @Test
    public void testGetSubscriptionListsIncludesPending() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
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

        when(mockContactApiClient.getSubscriptionLists(fakeContactId)).thenReturn(new Response.Builder<Map<String, Set<Scope>>>(200)
                .setResult(networkSubscriptions)
                .build()
        );

        Map<String, Set<Scope>> expectedSubscriptions = new HashMap<String, Set<Scope>>() {{
            put("foo", Collections.singleton(Scope.SMS));
            put("fizz", Collections.singleton(Scope.SMS));
        }};

        Map<String, Set<Scope>> result = contact.getSubscriptionLists(true).get();
        assertEquals(expectedSubscriptions, result);

        assertEquals(networkSubscriptions, subscriptionCache.get());
        verify(mockContactApiClient).getSubscriptionLists(fakeContactId);
    }

    @Test
    public void testGetSubscriptionNoContact() throws RequestException, ExecutionException, InterruptedException {
        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists(false);
        assertNull(result.get());
        verify(mockContactApiClient, never()).getSubscriptionLists(anyString());
    }

    @Test
    public void testGetSubscriptionPendingReset() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, false, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        contact.reset();

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists(false);
        assertNull(result.get());
        verify(mockContactApiClient, never()).getSubscriptionLists(anyString());
    }

    @Test
    public void testGetSubscriptionPendingIdentify() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, "some user id")).build();
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
        assertEquals(subscriptions, contact.getSubscriptionLists(false).get());

        // Identify same named user, still have results
        contact.identify("some user id");
        assertEquals(subscriptions, contact.getSubscriptionLists(false).get());

        // Different named user, results should be null
        contact.identify("some other user id");
        assertNull(contact.getSubscriptionLists(false).get());
    }

    @Test
    public void testGetSubscriptionErrorResult() throws RequestException, ExecutionException, InterruptedException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        // Resolve contact
        contact.resolve();
        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));

        when(mockContactApiClient.getSubscriptionLists(fakeContactId)).thenReturn(new Response.Builder<Map<String, Set<Scope>>>(400)
                .build()
        );

        PendingResult<Map<String, Set<Scope>>> result = contact.getSubscriptionLists(false);
        result.addResultCallback(result1 -> {
            assertNull(result1);
            assertNull(subscriptionCache.get());
        });

        verify(mockContactApiClient).getSubscriptionLists(fakeContactId);
    }

    @Test
    public void testRegisterEmail() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        JsonMap properties = JsonMap.newBuilder().put("place", "paris").build();
        EmailRegistrationOptions options = EmailRegistrationOptions.commercialOptions(new Date(), new Date(), properties);

        Response<AssociatedChannel> registerResponse = new Response.Builder<AssociatedChannel>(200).setResult(new AssociatedChannel("email-channel-id", ChannelType.EMAIL)).build();
        when(mockContactApiClient.registerEmail(eq(fakeContactId), eq("ua@airship.com"), any(EmailRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerEmail("ua@airship.com", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerEmail(eq(fakeContactId), eq("ua@airship.com"), any(EmailRegistrationOptions.class));

        verify(changeListener).onContactChanged();
    }

    @Test
    public void testRegisterEmailFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        JsonMap properties = JsonMap.newBuilder().put("place", "paris").build();
        EmailRegistrationOptions options = EmailRegistrationOptions.commercialOptions(new Date(), new Date(), properties);

        Response<AssociatedChannel> registerResponse = new Response.Builder<AssociatedChannel>(500).build();
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

        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        SmsRegistrationOptions options = SmsRegistrationOptions.options("12345");

        Response<AssociatedChannel> registerResponse = new Response.Builder<AssociatedChannel>(200).setResult(new AssociatedChannel("sms-channel-id", ChannelType.SMS)).build();
        when(mockContactApiClient.registerSms(eq(fakeContactId), eq("12345678"), any(SmsRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerSms("12345678", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerSms(eq(fakeContactId), eq("12345678"), any(SmsRegistrationOptions.class));

        verify(changeListener).onContactChanged();
    }

    @Test
    public void testRegisterSmsFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        SmsRegistrationOptions options = SmsRegistrationOptions.options("12345");

        Response<AssociatedChannel> registerResponse = new Response.Builder<AssociatedChannel>(500).build();
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

        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        OpenChannelRegistrationOptions options = OpenChannelRegistrationOptions.options("platform-name", Collections.singletonMap("number", "1"));

        Response<AssociatedChannel> registerResponse = new Response.Builder<AssociatedChannel>(200).setResult(new AssociatedChannel("open-channel-id", ChannelType.OPEN)).build();
        when(mockContactApiClient.registerOpenChannel(eq(fakeContactId), eq("open-channel-address"), any(OpenChannelRegistrationOptions.class))).thenReturn(registerResponse);

        contact.registerOpenChannel("open-channel-address", options);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).registerOpenChannel(eq(fakeContactId), eq("open-channel-address"), any(OpenChannelRegistrationOptions.class));

        verify(changeListener).onContactChanged();
    }

    @Test
    public void testRegisterOpenFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        OpenChannelRegistrationOptions options = OpenChannelRegistrationOptions.options("platform-name", Collections.singletonMap("number", "1"));

        Response<AssociatedChannel> registerResponse = new Response.Builder<AssociatedChannel>(500).build();
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

        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<AssociatedChannel> associatedResponse = new Response.Builder<AssociatedChannel>(200).setResult(new AssociatedChannel("new-fake-channel-id", ChannelType.EMAIL)).build();
        when(mockContactApiClient.associatedChannel(fakeContactId, "new-fake-channel-id", ChannelType.EMAIL)).thenReturn(associatedResponse);

        contact.associateChannel("new-fake-channel-id", ChannelType.EMAIL);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).associatedChannel(fakeContactId, "new-fake-channel-id", ChannelType.EMAIL);

        verify(changeListener).onContactChanged();
    }

    @Test
    public void testAssociateChannelFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<AssociatedChannel> associatedResponse = new Response.Builder<AssociatedChannel>(500).build();
        when(mockContactApiClient.associatedChannel(fakeContactId, "new-fake-channel-id", ChannelType.EMAIL)).thenReturn(associatedResponse);

        contact.associateChannel("new-fake-channel-id", ChannelType.EMAIL);

        assertEquals(JobResult.SUCCESS, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobResult.RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).associatedChannel(fakeContactId, "new-fake-channel-id", ChannelType.EMAIL);

        verify(changeListener).onContactChanged();
    }

    @Test
    public void testForwardAppScopeSubscriptionsToChannel() throws RequestException {
        contact.editSubscriptionLists()
               .subscribe("app 1", Scope.APP)
               .subscribe("web 1", Scope.WEB)
               .unsubscribe("web 2", Scope.WEB)
               .unsubscribe("app 2", Scope.APP)
               .apply();

        List<SubscriptionListMutation> expected = new ArrayList<SubscriptionListMutation>() {{
            add(SubscriptionListMutation.newSubscribeMutation("app 1", testClock.currentTimeMillis));
            add(SubscriptionListMutation.newUnsubscribeMutation("app 2", testClock.currentTimeMillis));
        }};

        verify(mockChannel).processContactSubscriptionListMutations(expected);
    }

}
