package com.urbanairship.richpush;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class UserAPIClientTest extends BaseTestCase {

    private AirshipConfigOptions mockAirshipConfigOptions;
    private JSONObject newUserPayload = new JSONObject();
    private JSONObject updateUserPayload = new JSONObject();
    private JSONObject deleteMessagePayload = new JSONObject();
    private JSONObject markMessagesReadPayload = new JSONObject();
    private String MESSAGE_URL = "api/user/%s/messages/message/%s/";
    private UserAPIClient client;
    private TestRequest testRequest;

    @Before
    public void setUp() throws JSONException {
        mockAirshipConfigOptions = Mockito.mock(AirshipConfigOptions.class);
        when(mockAirshipConfigOptions.getAppKey()).thenReturn("appKey");
        when(mockAirshipConfigOptions.getAppSecret()).thenReturn("appSecret");

        testRequest = new TestRequest();

        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                String requestMethod = (String) invocation.getArguments()[0];
                URL url = (URL) invocation.getArguments()[1];

                testRequest.setURL(url);
                testRequest.setRequestMethod(requestMethod);

                return testRequest;
            }
        }).when(mockRequestFactory).createRequest(anyString(), any(URL.class));

        TestApplication.getApplication().setOptions(mockAirshipConfigOptions);

        // Set hostURL
        UAirship.shared().getAirshipConfigOptions().hostURL = "https://go-demo.urbanairship.com/";

        // Create the newUserPayload
        JSONArray array = new JSONArray();
        array.put("someChannelId");
        newUserPayload.putOpt("android_channels", array);

        // Create the userUpdatePayload
        JSONObject channelPayload = new JSONObject();
        JSONArray channels = new JSONArray();
        channels.put("newUpdatedChannelId");
        channelPayload.put("add", channels);
        updateUserPayload.put("android_channels", channelPayload);

        // Create the deleteMessagePayload
        deleteMessagePayload.put("delete", new JSONArray());
        deleteMessagePayload.accumulate("delete",
                this.formatUrl(MESSAGE_URL, new String[] { "someUserId", "qsn1QR4eSMCur7jlsz0sSQ" }));

        // Create the markMessagesReadPayload
        markMessagesReadPayload.put("mark_as_read", new JSONArray());
        markMessagesReadPayload.accumulate("mark_as_read",
                this.formatUrl(MESSAGE_URL, new String[] { "someUserId", "qsn1QR4eSMCur7jlsz0sSQ" }));

        client = new UserAPIClient(mockRequestFactory);
    }

    /**
     * Test create user request succeeds if the status is 201
     */
    @Test
    public void testCreateUserSucceedsRequest() throws Exception {

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_CREATED)
                .setResponseMessage("Created")
                .setResponseBody("{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }")
                .create();

        UserResponse response = client.createUser(newUserPayload);

        assertNotNull("User response should not be null", response);
        assertEquals("User create should be a POST", "POST", testRequest.getRequestMethod());
        assertEquals("Unexpected create user URL", "https://go-demo.urbanairship.com/api/user/",
                testRequest.getURL().toString());

        assertEquals("User request should be the JSON payload", testRequest.getRequestBody(),
                newUserPayload.toString());
        assertEquals("User ID should match with response", "someUserId", response.getUserId());
        assertEquals("User token should match with response", "someUserToken", response.getUserToken());
    }

    /**
     * Test create user request fails if the status is 200
     */
    @Test
    public void testCreateUserFailsRequestWithOk() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ failed }")
                .create();

        UserResponse response = client.createUser(newUserPayload);

        assertNull("User response should not be null", response);
        assertEquals("User create should be a POST", "POST", testRequest.getRequestMethod());
        assertEquals("Unexpected create user URL", "https://go-demo.urbanairship.com/api/user/",
                testRequest.getURL().toString());
    }

    /**
     * Test update user request succeeds if the status is 200
     */
    @Test
    public void testUpdateUserSucceedsRequest() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        boolean success = client.updateUser(updateUserPayload, "someUserId", "someUserToken");

        assertTrue("Update user should succeed", success);
        assertEquals("User create should be a POST", "POST", testRequest.getRequestMethod());
        assertEquals("Unexpected update user URL", "https://go-demo.urbanairship.com/api/user/someUserId/",
                testRequest.getURL().toString());
    }

    /**
     * Test update user request fails if the status is 201
     */
    @Test
    public void testUpdateUserFailsRequestWithCreated() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_CREATED)
                .setResponseMessage("Created")
                .setResponseBody("{ failed }")
                .create();

        boolean success = client.updateUser(updateUserPayload, "someUserId", "someUserToken");

        assertFalse("Update user should fail", success);
        assertEquals("User create should be a POST", "POST", testRequest.getRequestMethod());
        assertEquals("Unexpected update user URL", "https://go-demo.urbanairship.com/api/user/someUserId/",
                testRequest.getURL().toString());
    }

    /**
     * Test update user request fails with a null userId
     */
    @Test
    public void testUpdateUserFailsRequestNullUserId() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        boolean success = client.updateUser(updateUserPayload, null, "someUserToken");

        assertFalse("Update user should fail", success);
    }

    /**
     * Test update user request fails with a null user token
     */
    @Test
    public void testUpdateUserFailsRequestNullUserToken() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();
        boolean success = client.updateUser(updateUserPayload, "someUserId", null);

        assertFalse("Update user should fail", success);
    }

    /**
     * Test delete messages on server succeeded
     */
    @Test
    public void testDeleteMessagesWithPayload() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        boolean success = client.deleteMessages(deleteMessagePayload, "someUserId", "someUserToken");

        assertTrue("Delete messages on server succeeded", success);
        assertEquals("User create should be a POST", "POST", testRequest.getRequestMethod());
        assertEquals("Unexpected delete messages URL", "https://go-demo.urbanairship.com/api/user/someUserId/messages/delete/",
                testRequest.getURL().toString());
    }

    /**
     * Test delete messages on server fails with 501
     */
    @Test
    public void testDeleteMessagesFails() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_NOT_IMPLEMENTED)
                .setResponseMessage("Not Implemented")
                .setResponseBody("{ failed }")
                .create();

        boolean success = client.deleteMessages(deleteMessagePayload, "someUserId", "someUserToken");

        assertFalse("Delete messages on server failed", success);
    }

    /**
     * Test delete messages on server fails with a null userId
     */
    @Test
    public void testDeleteMessagesFailWithNullUserId() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        boolean success = client.deleteMessages(deleteMessagePayload, null, "someUserToken");

        assertFalse("Delete messages on server failed", success);
    }

    /**
     * Test delete messages on server fails with a null user token
     */
    @Test
    public void testDeleteMessagesFailWithNullUserToken() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        boolean success = client.deleteMessages(deleteMessagePayload, "someUserId", null);

        assertFalse("Delete messages on server failed", success);
    }

    /**
     * Test mark messages read on server succeeded
     */
    @Test
    public void testMarkMessagesReadWithPayload() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();


        boolean success = client.markMessagesRead(markMessagesReadPayload, "someUserId", "someUserToken");

        assertTrue("Mark messages read on server succeeded", success);
        assertEquals("User create should be a POST", "POST", testRequest.getRequestMethod());
        assertEquals("Unexpected mark messages read URL", "https://go-demo.urbanairship.com/api/user/someUserId/messages/unread/",
                testRequest.getURL().toString());
    }

    /**
     * Test mark messages read on server fails with 501
     */
    @Test
    public void testMarkMessagesReadFails() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_NOT_IMPLEMENTED)
                .setResponseMessage("Not Implemented")
                .setResponseBody("{ failed }")
                .create();

        boolean success = client.markMessagesRead(markMessagesReadPayload, "someUserId", "someUserToken");

        assertFalse("Mark messages read on server failed", success);
    }

    /**
     * Test mark messages read on server fails with a null userId
     */
    @Test
    public void testMarkMessagesReadFailWithNullUserId() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        boolean success = client.markMessagesRead(markMessagesReadPayload, null, "someUserToken");

        assertFalse("Mark messages read on server failed", success);
    }

    /**
     * Test mark messages read on server fails with a null user token
     */
    @Test
    public void testMarkMessagesReadFailWithNullUserToken() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\" }")
                .create();

        boolean success = client.markMessagesRead(markMessagesReadPayload, "someUserId", null);

        assertFalse("Mark messages read on server failed", success);
    }

    /**
     * Test get messages from the server succeeds.
     */
    @Test
    public void testGetMessages() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"messages\": [ {\"message_id\": \"some_mesg_id\"," +
                        "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," +
                        "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," +
                        "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," +
                        "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
                        "\"title\": \"Message title\", \"extra\": { \"some_key\": \"some_value\"}," +
                        "\"content_type\": \"text/html\", \"content_size\": \"128\"}]}")
                .create();

        MessageListResponse messages = client.getMessages("someUserId", "someUserToken", 1000);


        assertNotNull("User response should not be null", messages);
        assertEquals("User create should be a GET", "GET", testRequest.getRequestMethod());
        assertEquals("Unexpected get messages URL", "https://go-demo.urbanairship.com/api/user/someUserId/messages/",
                testRequest.getURL().toString());
    }

    /**
     * Test get messages from the server fails with 501
     */
    @Test
    public void testGetMessagesFail() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_NOT_IMPLEMENTED)
                .setResponseBody("{ \"ok\" }")
                .create();

        MessageListResponse response = client.getMessages("someUserId", "someUserToken", 10000);

        assertNull("User response should be null", response.getServerMessages());
    }

    /**
     * Test get messages from the server fails with null userId
     */
    @Test
    public void testGetMessagesFailWithNullUserId() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseBody("{ \"ok\" }")
                .create();

        MessageListResponse messages = client.getMessages(null, "someUserToken", 1000);

        assertNull("User response should be null", messages);
    }

    /**
     * Test get messages from the server fails with null user token
     */
    @Test
    public void testGetMessagesFailWithNullUserToken() throws Exception {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseBody("{ \"ok\" }")
                .create();

        MessageListResponse messages = client.getMessages("someUserId", null, 1000);

        assertNull("User response should be null", messages);
    }

    // Helper methods

    private String formatUrl(String urlFormat, String[] urlParams) {
        StringBuilder builder = new StringBuilder(UAirship.shared().getAirshipConfigOptions().hostURL).append(String.format(urlFormat, (Object[]) urlParams));
        return builder.toString();
    }
}
