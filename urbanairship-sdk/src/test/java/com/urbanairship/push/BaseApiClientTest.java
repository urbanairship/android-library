/* Copyright 2016 Urban Airship and Contributors */

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

public class BaseApiClientTest extends BaseTestCase {

    private final String identifier = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String tagGroup = "fake_tag_group";

    private Set<String> tagsToAdd;
    private Set<String> tagsToRemove;
    private Set<String> tagsToSet;
    private TestRequest testRequest;

    private BaseApiClient client;

    @Before
    public void setUp() {
        AirshipConfigOptions configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setInProduction(false)
                .setHostURL("https://go-demo.urbanairship.com/")
                .build();

        testRequest = new TestRequest();
        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        client = new BaseApiClient(configOptions, mockRequestFactory) {
            @Override
            protected String getTagGroupAudienceSelector() {
                return "test";
            }

            @Override
            protected String getTagGroupPath() {
                return "api/test";
            }
        };

        tagsToAdd = new HashSet<>();
        tagsToAdd.add("tag1");

        tagsToRemove = new HashSet<>();
        tagsToRemove.add("tag2");

        tagsToSet = new HashSet<>();
        tagsToSet.add("tag3");
    }

    /**
     * Test updateTagGroups succeeds if status is 200.
     */
    @Test
    public void testUpdateTagGroupsSucceeds() throws JsonException {
        // testRequest is returned from the mockRequestFactory
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(tagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(tagGroup, tagsToRemove);

        Response response = client.updateTagGroups(identifier, addTags, removeTags, new HashMap<String, Set<String>>());

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());

        testRequest.getRequestBody();

        Map<String, String> audience = new HashMap<>();
        audience.put("test", identifier);

        JsonValue request = JsonValue.parseString(testRequest.getRequestBody());

        // verify payload
        assertEquals("Payload should contain audience", request.getMap().get("audience"), JsonValue.wrap(audience));
        assertEquals("Payload should contain addTags", request.getMap().get("add"), JsonValue.wrap(addTags));
        assertEquals("Payload should contain removeTags", request.getMap().get("remove"), JsonValue.wrap(removeTags));
    }

    /**
     * Test updateTagGroups succeeds if status is 200.
     */
    @Test
    public void testUpdateSetTagGroupsSucceeds() throws JsonException {
        // testRequest is returned from the mockRequestFactory
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Map<String, Set<String>> setTags = new HashMap<>();
        setTags.put(tagGroup, tagsToSet);

        Response response = client.updateTagGroups(identifier, null, null, setTags);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());

        testRequest.getRequestBody();

        Map<String, String> audience = new HashMap<>();
        audience.put("test", identifier);

        JsonValue request = JsonValue.parseString(testRequest.getRequestBody());

        // verify payload
        assertEquals("Payload should contain audience", request.getMap().get("audience"), JsonValue.wrap(audience));
        assertEquals("Payload should contain setTags", request.getMap().get("set"), JsonValue.wrap(setTags));
    }


    /**
     * Test payload does not contain empty addTags.
     */
    @Test
    public void testUpdateTagGroupsEmptyAddTags() throws JsonException {
        // testRequest is returned from the mockRequestFactory
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Map<String, Set<String>> emptyAddTags = new HashMap<>();

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(tagGroup, tagsToRemove);

        Response response = client.updateTagGroups(identifier, emptyAddTags, removeTags, new HashMap<String, Set<String>>());

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());

        testRequest.getRequestBody();

        Map<String, String> audience = new HashMap<>();
        audience.put("test", identifier);

        JsonValue request = JsonValue.parseString(testRequest.getRequestBody());

        // verify payload
        assertEquals("Payload should contain audience", request.getMap().get("audience"), JsonValue.wrap(audience));
        assertNull("EmptyAddTags should not be present in payload", request.getMap().get("add"));
        assertEquals("Payload should contain removeTags", request.getMap().get("remove"), JsonValue.wrap(removeTags));
    }

    /**
     * Test payload does not contain empty removeTags.
     */
    @Test
    public void testEmptyRemoveTags() throws JsonException {
        // testRequest is returned from the mockRequestFactory
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(tagGroup, tagsToAdd);

        Map<String, Set<String>> emptyRemoveTags = new HashMap<>();

        Response response = client.updateTagGroups(identifier, addTags, emptyRemoveTags, new HashMap<String, Set<String>>());

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());

        testRequest.getRequestBody();

        Map<String, String> audience = new HashMap<>();
        audience.put("test", identifier);

        JsonValue request = JsonValue.parseString(testRequest.getRequestBody());

        // verify payload
        assertEquals("Payload should contain audience", request.getMap().get("audience"), JsonValue.wrap(audience));
        assertEquals("Payload should contain addTags", request.getMap().get("add"), JsonValue.wrap(addTags));
        assertNull("EmptyRemoveTags should not be present in payload", request.getMap().get("remove"));
    }

    /**
     * Test updateChannelTags with empty addTags and removeTags returns null.
     */
    @Test
    public void testUpdateTagGroupsEmptyTags() {
        Map<String, Set<String>> emptyAddTags = new HashMap<>();
        Map<String, Set<String>> emptyRemoveTags = new HashMap<>();

        Response response = client.updateTagGroups(identifier, emptyAddTags, emptyRemoveTags, new HashMap<String, Set<String>>());
        assertNull("Response should be null", response);
    }
}
