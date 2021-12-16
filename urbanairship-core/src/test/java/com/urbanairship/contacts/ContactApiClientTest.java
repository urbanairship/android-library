/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class ContactApiClientTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeContactId = "fake_contact_id";
    private final String fakeEmail = "fake@email.com";
    private ContactApiClient client;
    private TestRequest testRequest;
    private TestAirshipRuntimeConfig runtimeConfig;
    private RequestFactory mockRequestFactory;

    @Before
    public void setUp() {
        testRequest = new TestRequest();

        mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                .setDeviceUrl("https://example.com")
                .build());

        client = new ContactApiClient(runtimeConfig, mockRequestFactory);
    }

    /**
     * Test resolve contact request succeeds if status is 200.
     */
    @Test
    public void testResolveSucceeds() throws RequestException, JsonException {
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
     * Test register email channel request succeeds if status is 200.
     */
    @Test
    public void testRegisterEmailSucceeds() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}";
        testRequest.responseStatus = 200;

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        String date = df.format(Calendar.getInstance().getTime());

        JsonMap payloadContent = JsonMap.newBuilder()
                                  .put("type", "email")
                                  .put("commercial_opted_in", date)
                                  .put("address", fakeEmail)
                                  .put("timezone", TimeZone.getDefault().getID())
                                  .put("locale_country", "US")
                                  .put("locale_language", "en")
                                  .build();

        JsonMap expected = JsonMap.newBuilder()
                                    .put("channel", payloadContent)
                                    .build();

        Response<String> response = client.registerEmail(fakeEmail, "commercial_opted_in");

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/email/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_channel_id", response.getResult());
    }

    /**
     * Test update email channel request succeeds if status is 200.
     */
    @Test
    public void testUpdateEmailSucceeds() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}";
        testRequest.responseStatus = 200;

        JsonMap contentPayload = JsonMap.newBuilder()
                                  .put("type", "email")
                                  .put("address", fakeEmail)
                                  .build();

        JsonMap expected = JsonMap.newBuilder()
                                    .put("channel", contentPayload)
                                    .build();

        Response<String> response = client.updateEmail(fakeEmail, "fake_channel_id");

        assertEquals(200, response.getStatus());
        assertEquals("PUT", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/email/fake_channel_id", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_channel_id", response.getResult());
    }


    /**
     * Test uninstall email channel request succeeds if status is 200.
     */
    @Test
    public void testUninstallEmail() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true }";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                                  .put("email_address", fakeEmail)
                                  .build();

        Response<Void> response = client.uninstallEmail(fakeEmail);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/email/uninstall", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
    }

    /**
     * Test register sms channel request succeeds if status is 200.
     */
    @Test
    public void testRegisterSmsSucceeds() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true, \"operation_id\": \"fake_operation_id\", \"channel_id\": \"fake_channel_id\"}";
        testRequest.responseStatus = 200;

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        String date = df.format(Calendar.getInstance().getTime());

        JsonMap expected = JsonMap.newBuilder()
                                        .put("msisdn", "123456789")
                                        .put("sender", "28855")
                                        .put("opted_in", date)
                                        .put("timezone", TimeZone.getDefault().getID())
                                        .put("locale_country", "US")
                                        .put("locale_language", "en")
                                        .build();

        Response<String> response = client.registerSms("123456789", "28855", true);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/sms/", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
        assertEquals("fake_channel_id", response.getResult());
    }

    /**
     * Test update sms channel request succeeds if status is 200.
     */
    @Test
    public void testUpdateSmsSucceeds() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true, \"channel_id\": \"fake_channel_id\"}";
        testRequest.responseStatus = 200;

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        String date = df.format(Calendar.getInstance().getTime());

        JsonMap expected = JsonMap.newBuilder()
                                  .put("msisdn", "123456789")
                                  .put("sender", "28855")
                                  .put("opted_in", date)
                                  .put("timezone", TimeZone.getDefault().getID())
                                  .put("locale_country", "US")
                                  .put("locale_language", "en")
                                  .build();

        Response<Void> response = client.updateSms("123456789", "28855", true, "fake_channel_id");

        assertEquals(200, response.getStatus());
        assertEquals("PUT", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/sms/fake_channel_id", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
    }

    /**
     * Test optout sms channel request succeeds if status is 200.
     */
    @Test
    public void testOptOutSms() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true }";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                                  .put("sender", "55555")
                                  .put("msisdn", "123456789")
                                  .build();

        Response<Void> response = client.optOutSms("123456789","55555");

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/sms/opt-out", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
    }


    /**
     * Test uninstall sms channel request succeeds if status is 200.
     */
    @Test
    public void testUninstallSms() throws RequestException, JsonException {
        testRequest.responseBody = "{ \"ok\": true }";
        testRequest.responseStatus = 200;

        JsonMap expected = JsonMap.newBuilder()
                                  .put("sender", "55555")
                                  .put("msisdn", "123456789")
                                  .build();

        Response<Void> response = client.uninstallSms("123456789","55555");

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/channels/sms/uninstall", testRequest.getUrl().toString());
        assertEquals(expected, JsonValue.parseString(testRequest.getRequestBody()).optMap());
    }

    /**
     * Test reset contact request succeeds if status is 200.
     */
    @Test
    public void testResetSucceeds() throws RequestException, JsonException {
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

        JsonMap expected = JsonMap.newBuilder()
                .put("tags", tagsBuilder.build())
                .put("attributes", JsonValue.wrap(attributeMutations))
                .build();

        Response<Void> response = client.update(fakeContactId, tagGroupsMutations, attributeMutations);

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
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.resolve(fakeChannelId);
    }

    /**
     * Test identify with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlIdentify() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.identify(fakeNamedUserId, fakeChannelId, fakeContactId);
    }

    /**
     * Test reset with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlReset() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.reset(fakeChannelId);
    }

    /**
     * Test update with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlUpdate() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        client.update(fakeContactId, null, null);
    }
}
