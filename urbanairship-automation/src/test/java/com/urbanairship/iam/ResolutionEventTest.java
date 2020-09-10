/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.analytics.Event;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class ResolutionEventTest {

    InAppMessage message;

    @Before
    public void before() {
        message = InAppMessage.newBuilder()
                              .setDisplayContent(new CustomDisplayContent(JsonValue.wrapOpt("COOL")))
                              .build();
    }

    /**
     * Test button click resolution event.
     */
    @Test
    public void testButtonClickResolutionEvent() throws JSONException {
        ButtonInfo buttonInfo = ButtonInfo.newBuilder()
                                          .setId("button id")
                                          .setLabel(TextInfo.newBuilder()
                                                            .setText("hi")
                                                            .build())
                                          .build();

        ResolutionEvent event = ResolutionEvent.messageResolution("schedule ID", message, ResolutionInfo.buttonPressed(buttonInfo), 3500);

        JsonMap expectedResolutionInfo = JsonMap.newBuilder()
                                                .put("type", "button_click")
                                                .put("button_id", "button id")
                                                .put("button_description", "hi")
                                                .put("display_time", Event.millisecondsToSecondsString(3500))
                                                .build();

        verifyEvent(expectedResolutionInfo, event);
    }

    /**
     * Test button click resolution event with a large label description only takes the first 30
     * characters.
     */
    @Test
    public void testButtonClickResolutionEventLargeLabel() throws JSONException {
        String largeLabel = UAStringUtil.repeat("a", 100, "");
        ButtonInfo buttonInfo = ButtonInfo.newBuilder()
                                          .setId("button id")
                                          .setLabel(TextInfo.newBuilder()
                                                            .setText(largeLabel)
                                                            .build())
                                          .build();

        ResolutionEvent event = ResolutionEvent.messageResolution("schedule ID", message, ResolutionInfo.buttonPressed(buttonInfo), 3500);

        JsonMap expectedResolutionInfo = JsonMap.newBuilder()
                                                .put("type", "button_click")
                                                .put("button_id", "button id")
                                                .put("button_description", largeLabel.substring(0, 30))
                                                .put("display_time", Event.millisecondsToSecondsString(3500))
                                                .build();

        verifyEvent(expectedResolutionInfo, event);
    }

    /**
     * Test on click resolution event.
     */
    @Test
    public void testClickedResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.messageResolution("schedule ID", message, ResolutionInfo.messageClicked(), 5500);

        JsonMap expectedResolutionInfo = JsonMap.newBuilder()
                                                .put("type", "message_click")
                                                .put("display_time", Event.millisecondsToSecondsString(5500))
                                                .build();

        verifyEvent(expectedResolutionInfo, event);
    }

    /**
     * Test user dismissed resolution event.
     */
    @Test
    public void testUserDismissedResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.messageResolution("schedule ID", message, ResolutionInfo.dismissed(), 3500);

        JsonMap expectedResolutionInfo = JsonMap.newBuilder()
                                                .put("type", "user_dismissed")
                                                .put("display_time", Event.millisecondsToSecondsString(3500))
                                                .build();

        verifyEvent(expectedResolutionInfo, event);
    }

    /**
     * Test timed out resolution event.
     */
    @Test
    public void testTimedOutResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.messageResolution("schedule ID", message, ResolutionInfo.timedOut(), 15000);

        JsonMap expectedResolutionInfo = JsonMap.newBuilder()
                                                .put("type", "timed_out")
                                                .put("display_time", Event.millisecondsToSecondsString(15000))
                                                .build();

        verifyEvent(expectedResolutionInfo, event);
    }

    /**
     * Test replaced resolution event.
     */
    @Test
    public void testReplacedResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.legacyMessageReplaced("iam id", "replacement id");

        JsonMap expectedResolutionInfo = JsonMap.newBuilder()
                                                .put("type", "replaced")
                                                .put("replacement_id", "replacement id")
                                                .build();

        verifyEvent(expectedResolutionInfo, event);
    }

    /**
     * Test direct open resolution event.
     */
    @Test
    public void testDirectOpenResolutionEvent() throws JSONException {
        ResolutionEvent event = ResolutionEvent.legacyMessagePushOpened("iam id");

        JsonMap expectedResolutionInfo = JsonMap.newBuilder()
                                                .put("type", "direct_open")
                                                .build();

        verifyEvent(expectedResolutionInfo, event);
    }

    private void verifyEvent(JsonMap expectedResolutionInfo, ResolutionEvent event) {
        assertEquals(expectedResolutionInfo, event.getEventData().get("resolution"));
        assertEquals("in_app_resolution", event.getType());
    }

}
