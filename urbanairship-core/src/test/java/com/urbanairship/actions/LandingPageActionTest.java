/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.iam.InAppMessageScheduleInfo;
import com.urbanairship.iam.html.HtmlDisplayContent;
import com.urbanairship.js.Whitelist;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LandingPageActionTest extends BaseTestCase {

    private LandingPageAction action;
    private Whitelist whitelist;
    private InAppMessageManager inAppMessageManager;

    @Before
    public void setup() {
        action = new LandingPageAction();
        inAppMessageManager = mock(InAppMessageManager.class);
        getApplication().setInAppMessageManager(inAppMessageManager);

        whitelist = UAirship.shared().getWhitelist();
        whitelist.setOpenUrlWhitelistingEnabled(true);
    }

    /**
     * Test accepts arguments
     */
    @Test
    public void testAcceptsArguments() {
        // Basic URIs
        verifyAcceptsArgumentValue("https://www.urbanairship.com", true);

        // Payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "https://www.urbanairship.com");
        payload.put("cache_on_receive", true);
        verifyAcceptsArgumentValue(payload, true);
    }

    /**
     * Test accepts arguments rejects payloads that do not
     * define a url
     */
    @Test
    public void testRejectsArguments() {
        whitelist.addEntry("*");
        verifyAcceptsArgumentValue(null, false);
        verifyAcceptsArgumentValue("", false);
        // Empty payload
        Map<String, Object> payload = new HashMap<>();
        verifyAcceptsArgumentValue(payload, false);
    }

    /**
     * Test accepts arguments for URLs that are whitelisted.
     */
    @Test
    public void testWhiteList() {
        whitelist.addEntry("https://yep.example.com");

        // Basic URIs
        verifyAcceptsArgumentValue("https://yep.example.com", true);
        verifyAcceptsArgumentValue("https://nope.example.com", false);

        // Payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "https://yep.example.com");
        payload.put("cache_on_receive", true);
        verifyAcceptsArgumentValue(payload, true);

        payload.put("url", "https://nope.example.com");
        verifyAcceptsArgumentValue(payload, false);
    }

    /**
     * Test perform for every situation the action accepts
     */
    @Test
    public void testPerform() {
        whitelist.setOpenUrlWhitelistingEnabled(false);

        // Verify scheme less URIs turn into https
        verifyPerform("www.urbanairship.com", "https://www.urbanairship.com");

        // Verify common file URIs
        verifyPerform("file://urbanairship.com", "file://urbanairship.com");
        verifyPerform("https://www.urbanairship.com", "https://www.urbanairship.com");
        verifyPerform("http://www.urbanairship.com", "http://www.urbanairship.com");

        // Verify message URIs
        verifyPerform("message://message_id", "message://message_id");

        // Verify basic payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://example.com");
        verifyPerform(payload, "http://example.com");

        // Verify payload without a scheme
        payload.put("url", "www.example.com");
        verifyPerform(payload, "https://www.example.com");
    }

    private void verifyPerform(Object value, final String expectedUrl) {
        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_AUTOMATION
        };

        for (@Action.Situation final int situation : situations) {
            final ActionArguments args = ActionTestUtils.createArgs(situation, value);

            ActionResult result = action.perform(args);
            assertTrue("Should return 'null' result for situation " + situation, result.getValue().isNull());

            verify(inAppMessageManager).scheduleMessage(Mockito.argThat(new ArgumentMatcher<InAppMessageScheduleInfo>() {
                @Override
                public boolean matches(InAppMessageScheduleInfo argument) {
                    InAppMessage message = argument.getInAppMessage();
                    if (!message.getType().equals(InAppMessage.TYPE_HTML)) {
                        return false;
                    }

                    if (!message.getDisplayBehavior().equals(InAppMessage.DISPLAY_BEHAVIOR_IMMEDIATE)) {
                        return false;
                    }

                    if (message.isReportingEnabled()) {
                        return false;
                    }

                    HtmlDisplayContent displayContent = message.getDisplayContent();
                    if (displayContent.getRequireConnectivity()) {
                        return false;
                    }

                    if (!displayContent.getUrl().equals(expectedUrl)) {
                        return false;
                    }



                    return true;
                }
            }));

            clearInvocations(inAppMessageManager);
        }

    }

    private void verifyAcceptsArgumentValue(Object value, boolean shouldAccept) {
        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        for (@Action.Situation int situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, value);
            if (shouldAccept) {
                assertTrue("Should accept arguments in situation " + situation,
                        action.acceptsArguments(args));
            } else {
                assertFalse("Should reject arguments in situation " + situation,
                        action.acceptsArguments(args));
            }

        }
    }

}
