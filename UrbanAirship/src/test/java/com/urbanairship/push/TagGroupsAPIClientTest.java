package com.urbanairship.push;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestRequest;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class TagGroupsAPIClientTest extends BaseTestCase {

    private final String fakeNamedUserId = "fake-named-user-id";
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String tagGroup = "fake_tag_group";

    private Set<String> tagsToAdd;
    private Set<String> tagsToRemove;
    private TestRequest testRequest;

    private TagGroupsAPIClient client;
    
    @Before
    public void setUp() {
        AirshipConfigOptions configOptions = new AirshipConfigOptions();
        configOptions.developmentAppKey = "appKey";
        configOptions.developmentAppSecret = "appSecret";
        configOptions.inProduction = false;
        configOptions.hostURL = "https://go-demo.urbanairship.com/";

        testRequest = new TestRequest();
        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        client = new TagGroupsAPIClient(configOptions, mockRequestFactory);

        tagsToAdd = new HashSet<>();
        tagsToAdd.add("tag1");

        tagsToRemove = new HashSet<>();
        tagsToRemove.add("tag2");
    }

    /**
     * Test updateNamedUserTags succeeds if status is 200.
     */
    @Test
    public void testUpdateNamedUserTagsSucceeds() throws JsonException {
        // testRequest is returned from the mockRequestFactory
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(tagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(tagGroup, tagsToRemove);

        Response response = client.updateNamedUserTags(fakeNamedUserId, addTags, removeTags);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());

        testRequest.getRequestBody();

        Map<String, String> audience = new HashMap<>();
        audience.put("named_user_id", fakeNamedUserId);

        JsonValue request = JsonValue.parseString(testRequest.getRequestBody());

        // verify payload
        assertEquals(request.getMap().get("audience"), JsonValue.wrap(audience));
        assertEquals(request.getMap().get("add"), JsonValue.wrap(addTags));
        assertEquals(request.getMap().get("remove"), JsonValue.wrap(removeTags));
    }

    /**
     * Test updateNamedUserTags will null named user returns null.
     */
    @Test
    public void testUpdateNamedUserTagsNullNamedUser() {

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(tagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(tagGroup, tagsToRemove);

        Response response = client.updateNamedUserTags(null, addTags, removeTags);

        assertNull("Response should be null", response);
    }

    /**
     * Test updateChannelTags succeeds if status is 200.
     */
    @Test
    public void testUpdateChannelTagsSucceeds() throws JsonException {
        // testRequest is returned from the mockRequestFactory
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(tagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(tagGroup, tagsToRemove);

        Response response = client.updateChannelTags(fakeChannelId, addTags, removeTags);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());

        testRequest.getRequestBody();

        Map<String, String> audience = new HashMap<>();
        audience.put("android_channel", fakeChannelId);

        JsonValue request = JsonValue.parseString(testRequest.getRequestBody());

        // verify payload
        assertEquals(request.getMap().get("audience"), JsonValue.wrap(audience));
        assertEquals(request.getMap().get("add"), JsonValue.wrap(addTags));
        assertEquals(request.getMap().get("remove"), JsonValue.wrap(removeTags));
    }

    /**
     * Test updateChannelTags with null channel ID returns null.
     */
    @Test
    public void testUpdateChannelTagsNullChannel() {

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(tagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(tagGroup, tagsToRemove);

        Response response = client.updateChannelTags(null, addTags, removeTags);

        assertNull("Response should be null", response);
    }
}
