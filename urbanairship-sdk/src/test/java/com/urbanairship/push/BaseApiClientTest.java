/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.google.common.collect.Lists;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class BaseApiClientTest extends BaseTestCase {


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

        TagGroupsMutation mutation = TagGroupsMutation.newAddTagsMutation("test", new HashSet<>(Lists.newArrayList("tag1", "tag2")));
        Response response = client.updateTagGroups("identifier", mutation);

        assertNotNull("Response should not be null", response);
        assertEquals("Response status should be 200", HttpURLConnection.HTTP_OK, response.getStatus());

        testRequest.getRequestBody();

        Map<String, String> audience = new HashMap<>();
        audience.put("test", "identifier");

        JsonValue request = JsonValue.parseString(testRequest.getRequestBody());

        // verify payload
        JsonValue expected = JsonValue.parseString("{\"audience\": { \"test\": \"identifier\" }, \"add\": { \"test\": [\"tag1\", \"tag2\"] } }");
        assertEquals(request.getMap(), expected);
    }
}
