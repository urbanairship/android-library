package com.urbanairship.push;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestRequest;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;

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
    private final String namedUserTagGroup = "named_user_tags";
    private Set<String> tagsToAdd;
    private Set<String> tagsToRemove;
    private AirshipConfigOptions mockAirshipConfigOptions;
    private TagGroupsAPIClient client;
    private TestRequest testRequest;

    @Before
    public void setUp() {
        mockAirshipConfigOptions = Mockito.mock(AirshipConfigOptions.class);
        testRequest = new TestRequest();

        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        when(mockRequestFactory.createRequest(anyString(), any(URL.class))).thenReturn(testRequest);

        when(mockAirshipConfigOptions.getAppKey()).thenReturn("appKey");
        when(mockAirshipConfigOptions.getAppSecret()).thenReturn("appSecret");

        TestApplication.getApplication().setOptions(mockAirshipConfigOptions);

        // Set hostURL
        UAirship.shared().getAirshipConfigOptions().hostURL = "https://go-demo.urbanairship.com/";

        client = new TagGroupsAPIClient(mockRequestFactory);

        tagsToAdd = new HashSet<>();
        tagsToAdd.add("tag1");

        tagsToRemove = new HashSet<>();
        tagsToRemove.add("tag2");
    }

    /**
     * Test updateNamedUserTags succeeds if status is 200.
     */
    @Test
    public void testUpdateNamedUserTagsSucceeds() {

        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(namedUserTagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(namedUserTagGroup, tagsToRemove);

        Response response = client.updateNamedUserTags(fakeNamedUserId, addTags, removeTags);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());
    }

    /**
     * Test updateNamedUserTags will null named user returns null.
     */
    @Test
    public void testUpdateNamedUserTagsNullNamedUser() {

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(namedUserTagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(namedUserTagGroup, tagsToRemove);

        Response response = client.updateNamedUserTags(null, addTags, removeTags);

        assertNull("Response should be null", response);
    }

    /**
     * Test updateChannelTags succeeds if status is 200.
     */
    @Test
    public void testUpdateChannelTagsSucceeds() {
        testRequest.response = new Response.Builder(HttpURLConnection.HTTP_OK)
                .setResponseMessage("OK")
                .setResponseBody("{ \"ok\": true}")
                .create();

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(namedUserTagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(namedUserTagGroup, tagsToRemove);

        Response response = client.updateChannelTags(fakeChannelId, addTags, removeTags);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());

    }

    /**
     * Test updateChannelTags with null channel ID returns null.
     */
    @Test
    public void testUpdateChannelTagsNullChannel() {

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(namedUserTagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(namedUserTagGroup, tagsToRemove);

        Response response = client.updateChannelTags(null, addTags, removeTags);

        assertNull("Response should be null", response);
    }

    /**
     * Test updateNamedUserTags and updateChannelTags with malformed host URL returns null.
     */
    @Test
    public void testMalformedUrl() {
        RequestFactory mockRequestFactory = Mockito.mock(RequestFactory.class);
        // Set hostURL
        UAirship.shared().getAirshipConfigOptions().hostURL = "files://thisIsMalformed";

        TagGroupsAPIClient client2 = new TagGroupsAPIClient(mockRequestFactory);

        Map<String, Set<String>> addTags = new HashMap<>();
        addTags.put(namedUserTagGroup, tagsToAdd);

        Map<String, Set<String>> removeTags = new HashMap<>();
        removeTags.put(namedUserTagGroup, tagsToRemove);

        Response response = client2.updateNamedUserTags(fakeNamedUserId, addTags, removeTags);
        assertNull("Response should be null", response);

        response = client2.updateChannelTags(fakeChannelId, addTags, removeTags);
        assertNull("Response should be null", response);
    }
}
