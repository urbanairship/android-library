/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import android.net.Uri;

import com.google.common.collect.Lists;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class ContactApiClientTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeContactId = "fake_contact_id";
    private final String fakeEmail = "fake@email.com";
    private final String fakeMsisdn = "123456789";
    private final String fakeSenderId = "fake_sender_id";
    private ContactApiClient client;
    private TestAirshipRuntimeConfig runtimeConfig;
    private RequestFactory mockRequestFactory;

    @Before
    public void setUp() {
        mockRequestFactory = Mockito.mock(RequestFactory.class);

        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                .setDeviceUrl("https://example.com")
                .build());

        TimeZone usPacific = TimeZone.getTimeZone("US/Pacific");
        TimeZone.setDefault(usPacific);

        client = new ContactApiClient(runtimeConfig, mockRequestFactory);
    }

    /**
     * Test resolve contact request succeeds if status is 200.
     */
    @Test
    public void testResolveSucceeds() throws RequestException, JsonException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseBody = "{ \"ok\": true, \"contact_id\": \"fake_contact_id\", \"is_anonymous\": true }";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                .put("channel_id", fakeChannelId)
                .put("device_type", "android")
                .build();

        Response<ContactIdentity> response = client.resolve(fakeChannelId);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/resolve/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_contact_id", response.getResult().getContactId());
        assertEquals(true, response.getResult().isAnonymous());
    }

    /**
     * Test identify contact request succeeds if status is 200.
     */
    @Test
    public void testIdentifySucceeds() throws RequestException, JsonException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseBody = "{ \"ok\": true, \"contact_id\": \"fake_contact_id\"}";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                .put("named_user_id", fakeNamedUserId)
                .put("channel_id", fakeChannelId)
                .put("device_type", "android")
                .put("contact_id", fakeContactId)
                .build();

        Response<ContactIdentity> response = client.identify(fakeNamedUserId, fakeChannelId, fakeContactId);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/identify/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_contact_id", response.getResult().getContactId());
        assertEquals(false, response.getResult().isAnonymous());
    }

    /**
     * Test identify contact request succeeds if status is 200.
     */
    @Test
    public void testIdentifyContactIdNullSucceeds() throws RequestException, JsonException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseBody = "{ \"ok\": true, \"contact_id\": \"fake_contact_id\"}";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                                  .put("named_user_id", fakeNamedUserId)
                                  .put("channel_id", fakeChannelId)
                                  .put("device_type", "android")
                                  .build();

        Response<ContactIdentity> response = client.identify(fakeNamedUserId, fakeChannelId, null);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/identify/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_contact_id", response.getResult().getContactId());
        assertEquals(false, response.getResult().isAnonymous());
    }

    /**
     * Test register open channel request succeeds if status is 200.
     */
    @Test
    public void testRegisterOpenChannelSucceeds() throws RequestException, JsonException {
        TestRequest registerRequest = new TestRequest();
        TestRequest associateRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(registerRequest, associateRequest);
        registerRequest.responseBody = "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}";
        registerRequest.responseStatus = 200;
        associateRequest.responseStatus = 200;

        Map<String, String> identifiersMap = new HashMap<>();
        identifiersMap.put("identifier_key", "identifier_value");
        OpenChannelRegistrationOptions options = OpenChannelRegistrationOptions.options("email", identifiersMap);

        Response<AssociatedChannel> response = client.registerOpenChannel(fakeContactId, fakeEmail, options);
        assertEquals(200, response.getStatus());
        assertEquals("fake_channel_id", response.getResult().getChannelId());
        assertEquals(ChannelType.OPEN, response.getResult().getChannelType());

        // Verify register
        String expectedChannelPayload = "{\n" +
                "   \"channel\":{\n" +
                "      \"address\":\"fake@email.com\",\n" +
                "      \"timezone\":\"US\\/Pacific\",\n" +
                "      \"opt_in\":true,\n" +
                "      \"type\":\"open\",\n" +
                "      \"locale_language\":\"en\",\n" +
                "      \"open\":{\n" +
                "         \"open_platform_name\":\"email\",\n" +
                "         \"identifiers\":{\n" +
                "            \"identifier_key\":\"identifier_value\"\n" +
                "         }\n" +
                "      },\n" +
                "      \"locale_country\":\"US\"\n" +
                "   }\n" +
                "}";
        assertEquals("POST", registerRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/restricted/open/", registerRequest.getUrl().toString());
        assertEquals(JsonValue.parseString(expectedChannelPayload), JsonValue.parseString(registerRequest.getRequestBody()));

        // Verify update
        String expectedUpdatePayload = "{\n" +
                "   \"associate\":[\n" +
                "      {\n" +
                "         \"channel_id\":\"fake_channel_id\",\n" +
                "         \"device_type\":\"open\"\n" +
                "      }\n" +
                "   ]\n" +
                "}";
        assertEquals("POST", associateRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/fake_contact_id", associateRequest.getUrl().toString());
        assertEquals(JsonValue.parseString(expectedUpdatePayload), JsonValue.parseString(associateRequest.getRequestBody()));
    }

    /**
     * Test register email channel request succeeds if status is 200.
     */
    @Test
    public void testRegisterEmailSucceeds() throws RequestException, JsonException {
        TestRequest registerRequest = new TestRequest();
        TestRequest associateRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(registerRequest, associateRequest);
        registerRequest.responseBody = "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}";
        registerRequest.responseStatus = 200;
        associateRequest.responseStatus = 200;

        HashMap<String, JsonValue> propertiesMap = new HashMap<>();
        propertiesMap.put("properties_key", JsonValue.wrap("properties_value"));
        JsonMap properties = new JsonMap(propertiesMap);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date date = Calendar.getInstance().getTime();
        EmailRegistrationOptions options = EmailRegistrationOptions.options(date, properties, false);

        Response<AssociatedChannel> response = client.registerEmail(fakeContactId, fakeEmail, options);
        assertEquals(200, response.getStatus());
        assertEquals("fake_channel_id", response.getResult().getChannelId());
        assertEquals(ChannelType.EMAIL, response.getResult().getChannelType());

        // Verify register
        String expectedChannelPayload = "{\n" +
                "   \"channel\":{\n" +
                "      \"type\":\"email\",\n" +
                "      \"transactional_opted_in\":\"" + DateUtils.createIso8601TimeStamp(date.getTime()) + "\",\n" +
                "      \"address\":fake@email.com,\n" +
                "      \"timezone\":\"US\\/Pacific\",\n" +
                "      \"locale_language\":\"en\",\n" +
                "      \"locale_country\":\"US\"\n" +
                "   },\n" +
                "   \"properties\":{\n" +
                "      \"properties_key\":\"properties_value\"},\n" +
                "   \"opt_in_mode\":\"classic\"\n" +
                "}";
        assertEquals("POST", registerRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/restricted/email/", registerRequest.getUrl().toString());
        assertEquals(JsonValue.parseString(expectedChannelPayload), JsonValue.parseString(registerRequest.getRequestBody()));

        // Verify update
        String expectedUpdatePayload = "{\n" +
                "   \"associate\":[\n" +
                "      {\n" +
                "         \"channel_id\":\"fake_channel_id\",\n" +
                "         \"device_type\":\"email\"\n" +
                "      }\n" +
                "   ]\n" +
                "}";
        assertEquals("POST", associateRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/fake_contact_id", associateRequest.getUrl().toString());
        assertEquals(JsonValue.parseString(expectedUpdatePayload), JsonValue.parseString(associateRequest.getRequestBody()));
    }

    /**
     * Test register sms channel request succeeds if status is 200.
     */
    @Test
    public void testRegisterSmsSucceeds() throws RequestException, JsonException {
        TestRequest registerRequest = new TestRequest();
        TestRequest associateRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(registerRequest, associateRequest);
        registerRequest.responseBody = "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}";
        registerRequest.responseStatus = 200;
        associateRequest.responseStatus = 200;

        SmsRegistrationOptions options = SmsRegistrationOptions.options(fakeSenderId);

        Response<AssociatedChannel> response = client.registerSms(fakeContactId, fakeMsisdn, options);
        assertEquals(200, response.getStatus());
        assertEquals("fake_channel_id", response.getResult().getChannelId());
        assertEquals(ChannelType.SMS, response.getResult().getChannelType());

        // Verify register
        String expectedChannelPayload = "{\n" +
                "      \"msisdn\":\"123456789\",\n" +
                "      \"sender\":\"fake_sender_id\",\n" +
                "      \"timezone\":\"US\\/Pacific\",\n" +
                "      \"locale_language\":\"en\",\n" +
                "      \"locale_country\":\"US\"\n" +
                "}";
        assertEquals("POST", registerRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/restricted/sms/", registerRequest.getUrl().toString());
        assertEquals(JsonValue.parseString(expectedChannelPayload), JsonValue.parseString(registerRequest.getRequestBody()));

        // Verify update
        String expectedUpdatePayload = "{\n" +
                "   \"associate\":[\n" +
                "      {\n" +
                "         \"channel_id\":\"fake_channel_id\",\n" +
                "         \"device_type\":\"sms\"\n" +
                "      }\n" +
                "   ]\n" +
                "}";
        assertEquals("POST", associateRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/fake_contact_id", associateRequest.getUrl().toString());
        assertEquals(JsonValue.parseString(expectedUpdatePayload), JsonValue.parseString(associateRequest.getRequestBody()));
    }

    /**
     * Test reset contact request succeeds if status is 200.
     */
    @Test
    public void testResetSucceeds() throws RequestException, JsonException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseBody = "{ \"ok\": true, \"contact_id\": \"fake_contact_id\"}";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                .put("channel_id", fakeChannelId)
                .put("device_type", "android")
                .build();

        Response<ContactIdentity> response = client.reset(fakeChannelId);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/reset/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_contact_id", response.getResult().getContactId());
        assertTrue(response.getResult().isAnonymous());
    }

    /**
     * Test reset contact request succeeds if status is 200.
     */
    @Test
    public void testUpdateSucceeds() throws RequestException, JsonException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseBody = "{ \"ok\": true, \"tag_warnings\": \"The following tag groups do not exist: random-tag-group\", \"attribute_warnings\": \"Unable to process attribute change for attribute: 'random-attribute'. Attribute not found.\"}";
        testRequest.responseStatus = 200;

        TagGroupsMutation tagGroupsSet = TagGroupsMutation.newSetTagsMutation("categories", new HashSet<>(Lists.newArrayList("bonds")));
        TagGroupsMutation tagGroupsAdd = TagGroupsMutation.newAddTagsMutation("topics", new HashSet<>(Lists.newArrayList("stocks")));
        TagGroupsMutation tagGroupsRemove = TagGroupsMutation.newRemoveTagsMutation("topics", new HashSet<>(Lists.newArrayList("birthdays")));

        List<TagGroupsMutation> tagGroupsMutations = new ArrayList<>();
        tagGroupsMutations.add(tagGroupsSet);
        tagGroupsMutations.add(tagGroupsAdd);
        tagGroupsMutations.add(tagGroupsRemove);
        tagGroupsMutations = TagGroupsMutation.collapseMutations(tagGroupsMutations);

        JsonMap.Builder tagsBuilder = JsonMap.newBuilder();
        for (TagGroupsMutation tag : tagGroupsMutations) {
            tagsBuilder.putAll(tag.toJsonValue().optMap());
        }

        List<AttributeMutation> attributeMutations = new ArrayList<>();
        attributeMutations.add(AttributeMutation.newSetAttributeMutation("name", JsonValue.wrapOpt("Bob"), 100));
        attributeMutations.add(AttributeMutation.newSetAttributeMutation("random", JsonValue.wrapOpt("Bob"), 200));
        attributeMutations = AttributeMutation.collapseMutations(attributeMutations);

        List<ScopedSubscriptionListMutation> subscriptionListMutations = new ArrayList<>();
        subscriptionListMutations.add(ScopedSubscriptionListMutation.newSubscribeMutation("name", null, 100));
        subscriptionListMutations.add(ScopedSubscriptionListMutation.newUnsubscribeMutation("random", Scope.APP, 200));

        JsonMap expected = JsonMap.newBuilder()
                                  .put("tags", tagsBuilder.build())
                                  .put("attributes", JsonValue.wrap(attributeMutations))
                                  .put("subscription_lists", JsonValue.wrap(subscriptionListMutations))
                                  .build();

        Response<Void> response = client.update(fakeContactId, tagGroupsMutations, attributeMutations, subscriptionListMutations);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/contacts/fake_contact_id", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
    }

    /**
     * Test resolve with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlResolve() throws RequestException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.resolve(fakeChannelId);
    }

    /**
     * Test identify with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlIdentify() throws RequestException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.identify(fakeNamedUserId, fakeChannelId, fakeContactId);
    }

    /**
     * Test reset with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlReset() throws RequestException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.reset(fakeChannelId);
    }

    /**
     * Test update with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlUpdate() throws RequestException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.update(fakeContactId, null, null, null);
    }

    @Test
    public void testGetSubscriptionLists() throws RequestException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseStatus = 200;
        testRequest.responseBody = "{\n" +
                "   \"ok\":true,\n" +
                "   \"subscription_lists\":[\n" +
                "      {\n" +
                "         \"list_ids\":[\n" +
                "            \"example_listId-1\",\n" +
                "            \"example_listId-3\"\n" +
                "         ],\n" +
                "         \"scope\":\"sms\"\n" +
                "      },\n" +
                "      {\n" +
                "         \"list_ids\":[\n" +
                "            \"example_listId-2\",\n" +
                "            \"example_listId-4\"\n" +
                "         ],\n" +
                "         \"scope\":\"app\"\n" +
                "      },\n" +
                "      {\n" +
                "         \"list_ids\":[\n" +
                "            \"example_listId-2\"\n" +
                "         ],\n" +
                "         \"scope\":\"web\"\n" +
                "      }\n" +
                "   ]\n" +
                "}";

        Response<Map<String, Set<Scope>>> response = client.getSubscriptionLists("identifier");

        assertEquals(testRequest.responseBody, response.getResponseBody());
        assertEquals("https://example.com/api/subscription_lists/contacts/identifier", testRequest.getUrl().toString());
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals(200, response.getStatus());

        Map<String, Set<Scope>> expected = new HashMap<>();
        expected.put("example_listId-1", Collections.singleton(Scope.SMS));
        expected.put("example_listId-2", new HashSet<>(Arrays.asList(Scope.APP, Scope.WEB)));
        expected.put("example_listId-3", Collections.singleton(Scope.SMS));
        expected.put("example_listId-4", Collections.singleton(Scope.APP));
        assertEquals(expected, response.getResult());
    }

    @Test
    public void testGetSubscriptionListsEmptyResponse() throws RequestException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseStatus = 200;
        testRequest.responseBody = "{\n" +
                "   \"ok\":true,\n" +
                "   \"subscription_lists\": []\n" +
                "}";

        Response<Map<String, Set<Scope>>> response = client.getSubscriptionLists("identifier");

        assertEquals(testRequest.responseBody, response.getResponseBody());
        assertEquals("https://example.com/api/subscription_lists/contacts/identifier", testRequest.getUrl().toString());
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals(200, response.getStatus());
        assertTrue(response.getResult().isEmpty());
    }


    @Test(expected = RequestException.class)
    public void testGetSubscriptionFailure() throws RequestException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseStatus = 200;
        testRequest.responseBody = "what";
        client.getSubscriptionLists("identifier");
    }

    @Test
    public void testGetSubscriptionInvalidBody() throws RequestException {
        TestRequest testRequest = new TestRequest();
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        testRequest.responseStatus = 400;
        Response<Map<String, Set<Scope>>> response = client.getSubscriptionLists("identifier");
        assertEquals(testRequest.responseBody, response.getResponseBody());
        assertEquals("https://example.com/api/subscription_lists/contacts/identifier", testRequest.getUrl().toString());
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals(400, response.getStatus());
        assertNull(response.getResult());
    }
}
