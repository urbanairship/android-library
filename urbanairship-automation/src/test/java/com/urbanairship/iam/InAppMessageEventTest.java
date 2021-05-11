/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class InAppMessageEventTest {

    /**
     * Test display event from a legacy in-app message.
     */
    @Test
    public void testLegacyMessage() {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        UAirship.shared().getAnalytics().setConversionMetadata("metadata");

        JsonMap expectedEventData = JsonMap.newBuilder()
                                           .put("id", "message id")
                                           .put("source", "urban-airship")
                                           .put("conversion_send_id", "send id")
                                           .put("conversion_metadata", "metadata")
                                           .build();

        InAppMessageEvent event = new InAppMessageEvent("message id", InAppMessage.SOURCE_LEGACY_PUSH, JsonValue.wrap("campaign info")) {
            @NonNull
            @Override
            public String getType() {
                return "test";
            }

            @NonNull
            @Override
            protected JsonMap.Builder extendEventDataBuilder(@NonNull JsonMap.Builder builder) {
                return builder;
            }
        };

        assertEquals(expectedEventData, event.getEventData());
        assertTrue(event.isValid());
    }

    /**
     * Test display event from an app-defined in-app message.
     */
    @Test
    public void testAppDefinedMessage() {
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

        InAppMessageEvent event = new InAppMessageEvent("message id", InAppMessage.SOURCE_APP_DEFINED, JsonValue.wrap("campaign info")) {
            @NonNull
            @Override
            public String getType() {
                return "test";
            }

            @NonNull
            @Override
            protected JsonMap.Builder extendEventDataBuilder(@NonNull JsonMap.Builder builder) {
                return builder;
            }
        };

        assertEquals(expectedEventData, event.getEventData());
        assertTrue(event.isValid());
    }

    /**
     * Test display event from a remote-data in-app message.
     */
    @Test
    public void testRemoteDataMessage() {
        UAirship.shared().getAnalytics().setConversionSendId("send id");
        UAirship.shared().getAnalytics().setConversionMetadata("metadata");

        JsonMap expectedEventData = JsonMap.newBuilder()
                                           .put("id", JsonMap.newBuilder()
                                                             .put("message_id", "message id")
                                                             .put("campaigns", "campaign info")
                                                             .build())
                                           .put("source", "urban-airship")
                                           .put("conversion_send_id", "send id")
                                           .put("conversion_metadata", "metadata")
                                           .build();


        InAppMessageEvent event = new InAppMessageEvent("message id", InAppMessage.SOURCE_REMOTE_DATA, JsonValue.wrap("campaign info")) {
            @NonNull
            @Override
            public String getType() {
                return "test";
            }

            @NonNull
            @Override
            protected JsonMap.Builder extendEventDataBuilder(@NonNull JsonMap.Builder builder) {
                return builder;
            }
        };

        assertEquals(expectedEventData, event.getEventData());
        assertTrue(event.isValid());
    }

    /**
     * Test display event when the conversion send id is null.
     */
    @Test
    public void testNoConversionSendId() {
        UAirship.shared().getAnalytics().setConversionSendId(null);
        UAirship.shared().getAnalytics().setConversionMetadata(null);

        JsonMap expectedEventData = JsonMap.newBuilder()
                                           .put("id", "message id")
                                           .put("source", "urban-airship")
                                           .build();

        InAppMessageEvent event = new InAppMessageEvent("message id", InAppMessage.SOURCE_APP_DEFINED, JsonValue.wrap("campaign info")) {
            @NonNull
            @Override
            public String getType() {
                return "test";
            }

            @NonNull
            @Override
            protected JsonMap.Builder extendEventDataBuilder(@NonNull JsonMap.Builder builder) {
                return builder;
            }
        };
    }

}
