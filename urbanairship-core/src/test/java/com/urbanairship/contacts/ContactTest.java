package com.urbanairship.contacts;

import android.content.Context;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.channel.AttributeEditor;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsEditor;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ContactTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeContactId = "fake_contact_id";

    private JobDispatcher mockDispatcher = mock(JobDispatcher.class);
    private AirshipChannel mockChannel = mock(AirshipChannel.class);
    private ContactApiClient mockContactApiClient = mock(ContactApiClient.class);
    private TestClock testClock = new TestClock();
    private TestActivityMonitor testActivityMonitor = new TestActivityMonitor();

    private final JobInfo updateJob =  JobInfo.newBuilder().setAction(Contact.ACTION_UPDATE_CONTACT).build();

    private Contact contact;
    private PrivacyManager privacyManager;

    @Before
    public void setUp() {
        Context context = TestApplication.getApplication();
        PreferenceDataStore dataStore = TestApplication.getApplication().preferenceDataStore;
        privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);

        contact = new Contact(context, dataStore, mockDispatcher, privacyManager, mockChannel,
                mockContactApiClient, testActivityMonitor, testClock);
    }

    @Test
    public void testChannelCreated() {
        ArgumentCaptor<AirshipChannelListener> argument = ArgumentCaptor.forClass(AirshipChannelListener.class);
        contact.init();
        verify(mockChannel).addChannelListener(argument.capture());
        AirshipChannelListener listener = argument.getValue();
        assertNotNull(listener);

        clearInvocations(mockDispatcher);

        listener.onChannelCreated(fakeChannelId);

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(Contact.ACTION_UPDATE_CONTACT);
            }
        }));
    }

    @Test
    public void testForeground() throws RequestException {
        // Set up fixed time
        testClock.currentTimeMillis = 0;

        contact.init();

        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();
        verify(mockDispatcher, times(2)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(Contact.ACTION_UPDATE_CONTACT);
            }
        }));

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
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

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
    }

    @Test
    public void testResolveFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 500 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(500).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();

        assertEquals(JobInfo.JOB_RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
    }

    @Test
    public void testResolveHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(403).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(response);

        contact.resolve();

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);
    }

    @Test
    public void testResetSucceed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, true, null)).build();
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).reset(fakeChannelId);
    }

    @Test
    public void testResetFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 500 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(500).build();
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobInfo.JOB_RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).reset(fakeChannelId);
    }

    @Test
    public void testResetHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(403).build();
        when(mockContactApiClient.reset(fakeChannelId)).thenReturn(response);

        contact.reset();

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
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

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);

        assertEquals(fakeContactId, contact.getLastContactIdentity().getContactId());
        verify(mockChannel).updateRegistration();
    }

    @Test
    public void testIdentifySkippedIfKnown() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 200 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity(fakeContactId, false, fakeNamedUserId)).build();
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);
        contact.identify(fakeNamedUserId);

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);
        assertNotNull(contact.getLastContactIdentity());

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verifyNoMoreInteractions(mockContactApiClient);

        assertEquals(fakeContactId, contact.getLastContactIdentity().getContactId());
        assertFalse(contact.getLastContactIdentity().isAnonymous());
        assertEquals(fakeNamedUserId, contact.getNamedUserId());
        verify(mockChannel).updateRegistration();
    }

    @Test
    public void testIdentifyFailed() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 500 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>((500)).build();
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);

        contact.identify(fakeNamedUserId);

        assertEquals(JobInfo.JOB_RETRY, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, null);
    }

    @Test
    public void testIdentifyHttpForbidden() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        // Set up a 403 response
        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>((403)).build();
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, null)).thenReturn(response);

        contact.identify(fakeNamedUserId);

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
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
        when(mockContactApiClient.update(fakeContactId, tagGroupsMutations, Collections.<AttributeMutation>emptyList())).thenReturn(updateResponse);

        tagGroupsEditor.apply();

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(fakeContactId, tagGroupsMutations, Collections.<AttributeMutation>emptyList());
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
        when(mockContactApiClient.update(fakeContactId, Collections.<TagGroupsMutation>emptyList(), attributeMutations)).thenReturn(updateResponse);

        attributeEditor.apply();

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).resolve(fakeChannelId);

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        verify(mockContactApiClient).update(fakeContactId, Collections.<TagGroupsMutation>emptyList(), attributeMutations);
    }

    @Test
    public void onConflictIdentify() throws RequestException {
        when(mockChannel.getId()).thenReturn(fakeChannelId);

        List<TagGroupsMutation> tagGroupsMutations = new ArrayList<>();
        tagGroupsMutations.add(TagGroupsMutation.newSetTagsMutation("group", new HashSet<>(Lists.newArrayList("tag"))));
        tagGroupsMutations = TagGroupsMutation.collapseMutations(tagGroupsMutations);

        List<AttributeMutation> attributeMutations = new ArrayList<>();
        attributeMutations.add(AttributeMutation.newSetAttributeMutation("one_attribute", JsonValue.wrap("attribute_value"), testClock.currentTimeMillis()));
        attributeMutations = AttributeMutation.collapseMutations(attributeMutations);

        ContactData contactData = new ContactData(Collections.singletonMap("one_attribute", JsonValue.wrap("attribute_value")), Collections.<String, Set<String>>singletonMap("group", new HashSet<>(Lists.newArrayList("tag"))));
        ContactConflictListener conflictListener = mock(ContactConflictListener.class);
        contact.setContactConflictListener(conflictListener);

        // Set up responses
        Response<ContactIdentity> resolveResponse = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity("some_contact_id", true, null)).build();
        when(mockContactApiClient.resolve(fakeChannelId)).thenReturn(resolveResponse);

        Response<Void> updateResponse = new Response.Builder<Void>(200).build();
        when(mockContactApiClient.update("some_contact_id", tagGroupsMutations, attributeMutations)).thenReturn(updateResponse);

        Response<ContactIdentity> response = new Response.Builder<ContactIdentity>(200).setResult(new ContactIdentity("some_other_contact_id", false, fakeNamedUserId)).build();
        when(mockContactApiClient.identify(fakeNamedUserId, fakeChannelId, "some_contact_id")).thenReturn(response);

        TagGroupsEditor tagGroupsEditor = contact.editTagGroups().setTag("group", "tag");
        tagGroupsEditor.apply();
        AttributeEditor attributeEditor = contact.editAttributes().setAttribute("one_attribute", "attribute_value");
        attributeEditor.apply();

        contact.identify(fakeNamedUserId);

        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));
        assertEquals(JobInfo.JOB_FINISHED, contact.onPerformJob(UAirship.shared(), updateJob));

        verify(mockContactApiClient).resolve(fakeChannelId);
        verify(mockContactApiClient).update("some_contact_id", tagGroupsMutations, attributeMutations);
        verify(mockContactApiClient).identify(fakeNamedUserId, fakeChannelId, "some_contact_id");

        ArgumentCaptor<ContactData> argument = ArgumentCaptor.forClass(ContactData.class);
        verify(conflictListener).onConflict(argument.capture(), eq(fakeNamedUserId));
        assertEquals(contactData, argument.getValue());
    }

}
