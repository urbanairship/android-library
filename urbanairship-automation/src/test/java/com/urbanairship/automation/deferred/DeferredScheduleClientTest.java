/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred;

import android.net.Uri;

import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestRequestSession;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.automation.TriggerContext;
import com.urbanairship.automation.Triggers;
import com.urbanairship.base.Supplier;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.automation.tags.TestUtils.tagSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class DeferredScheduleClientTest {

    private static List<TagGroupsMutation> EMPTY_TAGS = Collections.emptyList();
    private static List<AttributeMutation> EMPTY_ATTRIBUTES = Collections.emptyList();

    private DeferredScheduleClient client;
    private Supplier<StateOverrides> mockSupplier;
    private TestRequestSession requestSession = new TestRequestSession();
    private TestAirshipRuntimeConfig runtimeConfig;

    @Before
    public void setup() {
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();

        mockSupplier = mock(Supplier.class);
        client = new DeferredScheduleClient(
                runtimeConfig,
                requestSession,
                mockSupplier
        );

        when(mockSupplier.get()).thenReturn(null);
    }


    @Test
    public void testAndroidRequest() throws RequestException {
        requestSession.addResponse(400);
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM);

        Uri url = Uri.parse("https://airship.com");
        client.performRequest(url, "channel", null, EMPTY_TAGS, EMPTY_ATTRIBUTES);
        assertEquals(url, requestSession.getLastRequest().getUrl());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals(new RequestAuth.ChannelTokenAuth("channel"), requestSession.getLastRequest().getAuth());

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "android")
                                  .put("channel_id", "channel")
                                  .build();

        assertEquals(new RequestBody.Json(expected), requestSession.getLastRequest().getBody());
    }

    @Test
    public void testAmazonRequest() throws RequestException {
        requestSession.addResponse(400);
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);

        Uri url = Uri.parse("https://airship.com");
        client.performRequest(url, "channel", null, EMPTY_TAGS, EMPTY_ATTRIBUTES);
        assertEquals(url, requestSession.getLastRequest().getUrl());
        assertEquals("POST", requestSession.getLastRequest().getMethod());
        assertEquals(new RequestAuth.ChannelTokenAuth("channel"), requestSession.getLastRequest().getAuth());

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "amazon")
                                  .put("channel_id", "channel")
                                  .build();

        assertEquals(new RequestBody.Json(expected), requestSession.getLastRequest().getBody());
    }

    @Test
    public void testTriggeringContext() throws RequestException {
        requestSession.addResponse(400);

        CustomEvent event = CustomEvent.newBuilder("some event").build();
        TriggerContext triggerContext = new TriggerContext(Triggers.newCustomEventTriggerBuilder().build(), event.toJsonValue());
        client.performRequest(Uri.parse("https://airship.com"), "channel", triggerContext, EMPTY_TAGS, EMPTY_ATTRIBUTES);

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "android")
                                  .put("channel_id", "channel")
                                  .put("trigger", JsonMap.newBuilder()
                                                         .put("goal", 1)
                                                         .put("type", "custom_event_count")
                                                         .put("event", event.toJsonValue())
                                                         .build())
                                  .build();

        assertEquals(new RequestBody.Json(expected), requestSession.getLastRequest().getBody());
    }

    @Test
    public void testTagOverrides() throws RequestException {
        requestSession.addResponse(400);

        List<TagGroupsMutation> tagOverrides = new ArrayList<>();
        tagOverrides.add(TagGroupsMutation.newRemoveTagsMutation("foo", tagSet("one", "two")));
        tagOverrides.add(TagGroupsMutation.newSetTagsMutation("bar", tagSet("a")));

        client.performRequest(Uri.parse("https://airship.com"), "channel", null, tagOverrides, EMPTY_ATTRIBUTES);

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "android")
                                  .put("channel_id", "channel")
                                  .put("tag_overrides", JsonValue.wrapOpt(tagOverrides))
                                  .build();

        assertEquals(new RequestBody.Json(expected), requestSession.getLastRequest().getBody());
    }

    @Test
    public void testAttributeOverrides() throws RequestException {
        requestSession.addResponse(400);

        List<AttributeMutation> attributeOverrides = new ArrayList<>();
        attributeOverrides.add(AttributeMutation.newRemoveAttributeMutation("foo", 100));
        attributeOverrides.add(AttributeMutation.newRemoveAttributeMutation("bar", 100));

        client.performRequest(Uri.parse("https://airship.com"), "channel", null, EMPTY_TAGS, attributeOverrides);

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "android")
                                  .put("channel_id", "channel")
                                  .put("attribute_overrides", JsonValue.wrapOpt(attributeOverrides))
                                  .build();

        assertEquals(new RequestBody.Json(expected), requestSession.getLastRequest().getBody());
    }

    @Test
    public void testStateOverrides() throws RequestException {
        requestSession.addResponse(400);

        when(mockSupplier.get()).thenReturn(new StateOverrides("1", "1.0.0", true, new Locale("en", "US")));

        client.performRequest(Uri.parse("https://airship.com"), "channel", null, EMPTY_TAGS, EMPTY_ATTRIBUTES);

        JsonValue jsonStateOverrides = JsonMap.newBuilder()
                                              .put("app_version", "1")
                                              .put("sdk_version", "1.0.0")
                                              .put("notification_opt_in", true)
                                              .put("locale_language", "en")
                                              .put("locale_country", "US")
                                              .build()
                                              .toJsonValue();

        JsonMap expected = JsonMap.newBuilder()
                                  .put("platform", "android")
                                  .put("channel_id", "channel")
                                  .put("state_overrides", jsonStateOverrides)
                                  .build();

        assertEquals(new RequestBody.Json(expected), requestSession.getLastRequest().getBody());
    }

    @Test
    public void testMissedResponse() throws RequestException {
        requestSession.addResponse(200,
                JsonMap.newBuilder()
                       .put("type", "in_app_message")
                       .put("audience_match", false)
                       .build()
                       .toString()
        );

        Response<DeferredScheduleClient.Result> response = client.performRequest(Uri.parse("https://airship.com"),
                "channel", null, EMPTY_TAGS, EMPTY_ATTRIBUTES);

        assertFalse(response.getResult().isAudienceMatch());
        assertNull(response.getResult().getMessage());
    }

    @Test
    public void testNoMessage() throws RequestException {
        requestSession.addResponse(200,
                JsonMap.newBuilder()
                       .put("audience_match", true)
                       .build()
                       .toString()
        );

        Response<DeferredScheduleClient.Result> response = client.performRequest(Uri.parse("https://airship.com"),
                "channel", null, EMPTY_TAGS, EMPTY_ATTRIBUTES);

        assertTrue(response.getResult().isAudienceMatch());
        assertNull(response.getResult().getMessage());
    }

    @Test
    public void testMessage() throws RequestException {
        requestSession.addResponse(200,
                JsonMap.newBuilder()
                       .put("audience_match", true)
                       .put("type", "in_app_message")
                       .put("message", JsonMap.newBuilder()
                                              .put("display_type", "custom")
                                              .put("display", JsonMap.EMPTY_MAP)
                                              .build())
                       .build()
                       .toString()
        );

        InAppMessage expected = InAppMessage.newBuilder()
                                            .setDisplayContent(new CustomDisplayContent(JsonValue.NULL))
                                            .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                            .build();

        Response<DeferredScheduleClient.Result> response = client.performRequest(Uri.parse("https://airship.com"),
                "channel", null, EMPTY_TAGS, EMPTY_ATTRIBUTES);

        assertTrue(response.getResult().isAudienceMatch());
        assertEquals(expected, response.getResult().getMessage());
    }
}
