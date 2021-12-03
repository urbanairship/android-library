/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class InboxApiClientTest {

    private AirshipChannel mockChannel;

    private User user;
    private PreferenceDataStore dataStore;

    private TestRequest testRequest;
    private RequestFactory mockRequestFactory;
    private TestAirshipRuntimeConfig runtimeConfig;

    private InboxApiClient inboxApiClient;

    @Before
    public void setup() {
        mockChannel = Mockito.mock(AirshipChannel.class);

        Context context = ApplicationProvider.getApplicationContext();
        dataStore = PreferenceDataStore.inMemoryStore(context);

        user = new User(dataStore, mockChannel);
        // Set a valid user
        user.setUser("fakeUserId", "password");

        testRequest = new TestRequest();

        mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest()).thenReturn(testRequest);

        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder()
                .setDeviceUrl("https://example.com")
                .build());

        inboxApiClient = new InboxApiClient(runtimeConfig, mockRequestFactory);
    }

    @Test
    public void testUpdateMessagesSucceeds() throws RequestException, JsonException {
        testRequest.responseStatus = 200;
        testRequest.responseBody = "{ \"messages\": [ {\"message_id\": \"some_mesg_id\"," +
                "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," +
                "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," +
                "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," +
                "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
                "\"title\": \"Message title\", \"extra\": { \"some_key\": \"some_value\"}," +
                "\"content_type\": \"text/html\", \"content_size\": \"128\"}]}";

        Response<JsonList> response = inboxApiClient.fetchMessages(user, "channelId", 300L);

        assertEquals(200, response.getStatus());
        assertEquals("GET", testRequest.getRequestMethod());
        assertEquals(300L, testRequest.getIfModifiedSince());
        assertEquals("https://example.com/api/user/fakeUserId/messages/", testRequest.getUrl().toString());
        assertEquals("channelId", testRequest.getRequestHeaders().get("X-UA-Channel-ID"));
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
        assertEquals(JsonValue.parseString(testRequest.responseBody).optMap().opt("messages").getList(), response.getResult());
    }

    /**
     * Test update messages with null URL.
     */
    @Test(expected = RequestException.class)
    public void testNullUrlUpdateMessages() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        inboxApiClient.fetchMessages(user, "channelId", 0);
    }

    @Test
    public void testSyncDeletedMessageStateSucceeds() throws JsonException, RequestException {
        testRequest.responseStatus = 200;

        List<JsonValue> reportings = new ArrayList<>();
        reportings.add(JsonValue.parseString("{\"message_id\":\"testId1\"}"));
        reportings.add(JsonValue.parseString("{\"message_id\":\"testId2\"}"));

        JsonMap expectedJsonMap = JsonMap.newBuilder()
                .put("messages", JsonValue.wrapOpt(reportings))
                .build();

        Response<Void> response = inboxApiClient.syncDeletedMessageState(user, "channelId", reportings);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/user/fakeUserId/messages/delete/", testRequest.getUrl().toString());
        assertEquals("channelId", testRequest.getRequestHeaders().get("X-UA-Channel-ID"));
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
        assertEquals(expectedJsonMap, JsonValue.parseString(testRequest.getRequestBody()));
    }

    @Test(expected = RequestException.class)
    public void testNullUrlSyncDeletedMessageState() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        inboxApiClient.syncDeletedMessageState(user, "channelId", Collections.emptyList());
    }

    @Test
    public void testSyncReadMessageStateSucceeds() throws JsonException, RequestException {
        testRequest.responseStatus = 200;

        List<JsonValue> reportings = new ArrayList<>();
        reportings.add(JsonValue.parseString("{\"message_id\":\"testId1\"}"));
        reportings.add(JsonValue.parseString("{\"message_id\":\"testId2\"}"));

        JsonMap expectedJsonMap = JsonMap.newBuilder()
                                         .put("messages", JsonValue.wrapOpt(reportings))
                                         .build();

        Response<Void> response = inboxApiClient.syncReadMessageState(user, "channelId", reportings);

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/user/fakeUserId/messages/unread/", testRequest.getUrl().toString());
        assertEquals("channelId", testRequest.getRequestHeaders().get("X-UA-Channel-ID"));
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
        assertEquals(expectedJsonMap, JsonValue.parseString(testRequest.getRequestBody()));
    }

    @Test(expected = RequestException.class)
    public void testNullUrlSyncReadMessageState() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        inboxApiClient.syncReadMessageState(user, "channelId", Collections.emptyList());
    }

    @Test
    public void testCreateUserAndroidChannelsSucceeds() throws RequestException {
        testRequest.responseStatus = 200;
        testRequest.responseBody = "{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }";
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);

        Response<UserCredentials> response = inboxApiClient.createUser("channelId");

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/user/", testRequest.getUrl().toString());
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
        assertEquals("{\"android_channels\":[\"channelId\"]}", testRequest.getRequestBody());

        UserCredentials userCredentials = response.getResult();
        assertEquals("someUserId", userCredentials.getUsername());
        assertEquals("someUserToken", userCredentials.getPassword());
    }

    @Test
    public void testCreateUserAmazonChannelsSucceeds() throws RequestException {
        testRequest.responseStatus = 200;
        testRequest.responseBody = "{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }";
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);

        Response<UserCredentials> response = inboxApiClient.createUser("channelId");

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/user/", testRequest.getUrl().toString());
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
        assertEquals("{\"amazon_channels\":[\"channelId\"]}", testRequest.getRequestBody());

        UserCredentials userCredentials = response.getResult();
        assertEquals("someUserId", userCredentials.getUsername());
        assertEquals("someUserToken", userCredentials.getPassword());
    }

    @Test(expected = RequestException.class)
    public void testNullUrlCreateUser() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        runtimeConfig.setPlatform(0);
        inboxApiClient.createUser("channelId");
    }

    @Test
    public void testUpdateUserAndroidChannelsSucceeds() throws RequestException {
        testRequest.responseStatus = 200;
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);

        Response<Void> response = inboxApiClient.updateUser(user,"channelId");

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/user/fakeUserId/", testRequest.getUrl().toString());
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
        assertEquals("{\"android_channels\":{\"add\":[\"channelId\"]}}", testRequest.getRequestBody());
    }

    @Test
    public void testUpdateUserAmazonChannelsSucceeds() throws RequestException {
        testRequest.responseStatus = 200;
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);

        Response<Void> response = inboxApiClient.updateUser(user,"channelId");

        assertEquals(200, response.getStatus());
        assertEquals("POST", testRequest.getRequestMethod());
        assertEquals("https://example.com/api/user/fakeUserId/", testRequest.getUrl().toString());
        assertEquals("application/vnd.urbanairship+json; version=3;", testRequest.getRequestHeaders().get("Accept"));
        assertEquals("{\"amazon_channels\":{\"add\":[\"channelId\"]}}", testRequest.getRequestBody());
    }

    @Test(expected = RequestException.class)
    public void testNullUrlUpdateUser() throws RequestException {
        runtimeConfig.setUrlConfig(AirshipUrlConfig.newBuilder().build());
        runtimeConfig.setPlatform(0);
        inboxApiClient.updateUser(user,"channelId");
    }
}
