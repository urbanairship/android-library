/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.EventTestUtils;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class InAppMessageEventTest extends BaseTestCase {

    InAppMessage appDefinedInAppMessage;
    InAppMessage legacyInAppMessage;
    InAppMessage remoteDataInAppMessage;
    Map<String, JsonValue> renderedLocale;

    @Before
    public void setup() {

        renderedLocale = new HashMap<>();
        renderedLocale.put("language", JsonValue.wrap("en"));
        renderedLocale.put("country", JsonValue.wrap("US"));

        legacyInAppMessage = InAppMessage.newBuilder()
                                         .setId("message id")
                                         .setDisplayContent(new CustomDisplayContent(JsonValue.wrapOpt("COOL")))
                                         .setSource(InAppMessage.SOURCE_LEGACY_PUSH)
                                         .setCampaigns(JsonValue.wrap("campaigns info"))
                                         .build();

        remoteDataInAppMessage = InAppMessage.newBuilder()
                                             .setId("message id")
                                             .setDisplayContent(new CustomDisplayContent(JsonValue.wrapOpt("COOL")))
                                             .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                             .setCampaigns(JsonValue.wrap("campaigns info"))
                                             .setRenderedLocale(renderedLocale)
                                             .build();

        appDefinedInAppMessage = InAppMessage.newBuilder()
                                             .setId("message id")
                                             .setDisplayContent(new CustomDisplayContent(JsonValue.wrapOpt("COOL")))
                                             .setSource(InAppMessage.SOURCE_APP_DEFINED)
                                             .setCampaigns(JsonValue.wrap("campaigns info"))
                                             .build();
    }

    /**
     * Test display event from a legacy in-app message.
     */
    @Test
    public void testLegacyMessage() throws JsonException {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        UAirship.shared().getAnalytics().setConversionMetadata("metadata");

        JsonMap expectedEventData = JsonMap.newBuilder()
                                           .put("id", "message id")
                                           .put("source", "urban-airship")
                                           .put("conversion_send_id", "send id")
                                           .put("conversion_metadata", "metadata")
                                           .build();

        TestEvent event = new TestEvent(legacyInAppMessage);
        assertEquals(expectedEventData, EventTestUtils.getEventData(event));
        assertTrue(event.isValid());
    }

    /**
     * Test display event from an app-defined in-app message.
     */
    @Test
    public void testAppDefinedMessage() throws JsonException {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        UAirship.shared().getAnalytics().setConversionMetadata("metadata");

        JsonMap expectedEventData = JsonMap.newBuilder()
                                           .put("id", JsonMap.newBuilder()
                                                             .put("message_id", "message id")
                                                             .build())
                                           .put("source", "app-defined")
                                           .put("conversion_send_id", "send id")
                                           .put("conversion_metadata", "metadata")
                                           .build();

        TestEvent event = new TestEvent(appDefinedInAppMessage);
        assertEquals(expectedEventData, EventTestUtils.getEventData(event));
        assertTrue(event.isValid());
    }

    /**
     * Test display event from a remote-data in-app message.
     */
    @Test
    public void testRemoteDataMessage() throws JsonException {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        UAirship.shared().getAnalytics().setConversionMetadata("metadata");

        JsonMap expectedEventData = JsonMap.newBuilder()
                                           .put("id", JsonMap.newBuilder()
                                                             .put("message_id", "message id")
                                                             .put("campaigns", "campaigns info")
                                                             .build())
                                           .put("source", "urban-airship")
                                           .put("conversion_send_id", "send id")
                                           .put("conversion_metadata", "metadata")
                                           .put("locale", JsonValue.wrap(renderedLocale))
                                           .build();

        TestEvent event = new TestEvent(remoteDataInAppMessage);
        assertEquals(expectedEventData, EventTestUtils.getEventData(event));
        assertTrue(event.isValid());
    }

    /**
     * Test display event when the conversion send id is null.
     */
    @Test
    public void testDisplayEventNoConversionSendId() throws JsonException {
        UAirship.shared().getAnalytics().setConversionSendId(null);
        UAirship.shared().getAnalytics().setConversionMetadata(null);

        JsonMap expectedEventData = JsonMap.newBuilder()
                                           .put("id", "message id")
                                           .put("source", "urban-airship")
                                           .build();

        TestEvent event = new TestEvent(legacyInAppMessage);
        assertEquals(expectedEventData, EventTestUtils.getEventData(event));
        assertTrue(event.isValid());
    }

    private static class TestEvent extends InAppMessageEvent {

        TestEvent(InAppMessage message) {
            super(message);
        }

        @NonNull
        @Override
        public String getType() {
            return "test";
        }

    }

}
