/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.sample;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.assertion.ViewAssertions;
import android.support.test.espresso.contrib.NavigationViewActions;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.espresso.web.webdriver.Locator;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.app.NotificationManagerCompat;
import android.view.Gravity;

import com.urbanairship.AirshipReceiver;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.sample.utils.ActionsPayload;
import com.urbanairship.sample.utils.InAppMessagePayload;
import com.urbanairship.sample.utils.PushPayload;
import com.urbanairship.sample.utils.PushSender;
import com.urbanairship.sample.utils.RichPushPayload;
import com.urbanairship.util.UAStringUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.swipeDown;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerActions.open;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static android.support.test.espresso.matcher.ViewMatchers.hasChildCount;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.web.assertion.WebViewAssertions.webMatches;
import static android.support.test.espresso.web.sugar.Web.onWebView;
import static android.support.test.espresso.web.webdriver.DriverAtoms.findElement;
import static android.support.test.espresso.web.webdriver.DriverAtoms.getText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test basic pushes, message center and in-app messages using UIAutomator.
 * <p/>
 * To run the test suite on emulator or device with API 21+:
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.appKey="APP_KEY" -Pandroid.testInstrumentationRunnerArguments.masterSecret="MASTER_SECRET"
 */
@RunWith(AndroidJUnit4.class)
public class SampleTest {

    static final String TEST_NAMED_USER_STRING = UUID.randomUUID().toString();
    static final String TEST_TAG_STRING = UUID.randomUUID().toString();

    private String channelId;
    private PushSender pushSender;
    private TestAirshipReceiver airshipReceiver;

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class);

    @Before
    public void setup() throws InterruptedException {
        Bundle arguments = InstrumentationRegistry.getArguments();

        // Get the app key and master secret
        String appKey = arguments.getString("appKey");
        String masterSecret = arguments.getString("masterSecret");

        pushSender = new PushSender(masterSecret, appKey);

        airshipReceiver = new TestAirshipReceiver();
        airshipReceiver.register();

        // Make sure user notifications are enabled
        UAirship.shared().getPushManager().setUserNotificationsEnabled(true);

        // Clear any notifications
        NotificationManagerCompat.from(UAirship.getApplicationContext()).cancelAll();

        // Clear inbox
        Set<String> messageIds = UAirship.shared().getInbox().getMessageIds();
        UAirship.shared().getInbox().deleteMessages(messageIds);

        // Clear tags
        if (!UAirship.shared().getPushManager().getTags().isEmpty() || UAirship.shared().getPushManager().getChannelId() == null) {
            UAirship.shared().getPushManager().editTags().clear().apply();
            airshipReceiver.waitForChannelUpdate();
        }

        channelId = UAirship.shared().getPushManager().getChannelId();

        // Make sure we have a channel
        assertFalse(UAStringUtil.isEmpty(channelId));
    }

    @After
    public void cleanup() {
        airshipReceiver.unregister();
    }

    /**
     * Test setting named user, tag and pushes to them, including broadcast and channel pushes.
     */
    @Test
    public void testPush() throws Exception {
        UAirship.shared().getNamedUser().setId(TEST_NAMED_USER_STRING);
        UAirship.shared().getPushManager().editTags().addTag(TEST_TAG_STRING).apply();

        // Wait for registration updates
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        PushPayload channelPayload = PushPayload.newChannelPushBuilder(channelId)
                                                .setAlert(UUID.randomUUID().toString())
                                                .build();
        pushSender.send(channelPayload);

        PushPayload tagPayload = PushPayload.newTagPushBuilder(TEST_TAG_STRING)
                                            .setAlert(UUID.randomUUID().toString())
                                            .build();
        pushSender.send(tagPayload);

        PushPayload namedUserPayload = PushPayload.newNamedUserPushBuilder(TEST_NAMED_USER_STRING)
                                                  .setAlert(UUID.randomUUID().toString())
                                                  .build();
        pushSender.send(namedUserPayload);

        Map<String, String> expectedAlerts = new HashMap<>();
        expectedAlerts.put(channelPayload.getAlert(), "channel");
        expectedAlerts.put(tagPayload.getAlert(), "tag");
        expectedAlerts.put(namedUserPayload.getAlert(), "named user");

        for (int i = 0; i < 4; i++) {
            String postedAlert = airshipReceiver.postedAlerts.poll(15, TimeUnit.SECONDS);
            if (postedAlert == null) {
                break;
            }
            expectedAlerts.remove(postedAlert);
        }

        assertTrue("Failed to receive alert types: " + expectedAlerts.values(), expectedAlerts.isEmpty());
    }

    /**
     * Test messages in message center, including marking them as read and deleting the message.
     *
     * @throws Exception
     */
    @Test
    public void testMessageCenter() throws Exception {
        for (int i = 0; i < 2; i++) {
            RichPushPayload richPushPayload = RichPushPayload.newBuilder()
                                                             .setTitle("message " + i)
                                                             .setHtmlContent("<body id=\"title\">message " + i + "</body>")
                                                             .build();

            PushPayload payload = PushPayload.newChannelPushBuilder(channelId)
                                             .setAlert("message " + i)
                                             .setRichPushMessage(richPushPayload)
                                             .build();
            pushSender.send(payload);
        }

        for (int i = 0; i < 2; i++) {
            String postedAlert = airshipReceiver.postedAlerts.poll(15, TimeUnit.SECONDS);
            if (postedAlert == null && !postedAlert.startsWith("message")) {
                fail("Unable to receive rich push message.");
                return;
            }
        }

        // Open Navigation drawer
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.LEFT)))
                .perform(open());

        // Click Message Center
        onView(withId(R.id.nav_view))
                .perform(NavigationViewActions.navigateTo(R.id.nav_message_center));

        // Refresh the list
        onView(withId(R.id.message_list_container))
                .perform(swipeDown());

        // Check that we have 2 messages
        onView(withId(android.R.id.list))
                .check(ViewAssertions.matches(hasChildCount(2)));

        // Open the first message
        onData(instanceOf(RichPushMessage.class))
                .atPosition(0)
                .perform(click());

        // Verify the content
        onWebView()
                .withElement(findElement(Locator.ID, "title"))
                .check(webMatches(getText(), containsString(UAirship.shared().getInbox().getMessages().get(0).getTitle())));

        // Navigate back
        Espresso.pressBack();

        // Verify 1 message is marked read
        assertEquals(1, UAirship.shared().getInbox().getReadCount());
        assertEquals(1, UAirship.shared().getInbox().getUnreadCount());

        // Select the second message
        onData(instanceOf(RichPushMessage.class))
                .atPosition(1)
                .onChildView(withId(R.id.checkbox))
                .perform(click());

        // Mark all as read
        onView(withId(R.id.mark_read))
                .perform(click());

        // Verify all messages read
        assertEquals(UAirship.shared().getInbox().getUnreadCount(), 0);

        // Select each message
        onData(instanceOf(RichPushMessage.class))
                .atPosition(0)
                .onChildView(withId(R.id.checkbox))
                .perform(click());
        onData(instanceOf(RichPushMessage.class))
                .atPosition(1)
                .onChildView(withId(R.id.checkbox))
                .perform(click());

        // Delete all messages
        onView(withId(R.id.delete))
                .perform(click());

        // Verify all the messages are deleted
        assertEquals(0, UAirship.shared().getInbox().getCount());

        // Check that the list is empty
        onView(withId(android.R.id.list))
                .check(ViewAssertions.matches(hasChildCount(0)));
    }

    /**
     * Test for an in-app message with landing page and interactive buttons.
     *
     * @throws Exception
     */
    @Test
    public void testInAppMessage() throws Exception {

        // Add tag action for yes interactive button
        ActionsPayload yesActions = ActionsPayload.newBuilder()
                                                  .addTag("YES_INTERACTIVE_BUTTON_TAG")
                                                  .build();

        // Add tag action for no interactive button
        ActionsPayload noActions = ActionsPayload.newBuilder()
                                                 .addTag("NO_INTERACTIVE_BUTTON_TAG")
                                                 .build();

        // Landing page action for opening in-app message
        ActionsPayload openActions = ActionsPayload.newBuilder()
                                                   .setLandingPage("Hi!")
                                                   .build();

        InAppMessagePayload inAppMessagePayload = InAppMessagePayload.newBuilder()
                                                                     .setAlert("in-app test")
                                                                     .setInteractiveType("ua_yes_no_foreground")
                                                                     .addButtonActions("yes", yesActions)
                                                                     .addButtonActions("no", noActions)
                                                                     .setOpenActions(openActions)
                                                                     .build();

        PushPayload pushPayload = PushPayload.newChannelPushBuilder(channelId)
                                             .setAlert(UUID.randomUUID().toString())
                                             .setInAppMessage(inAppMessagePayload)
                                             .build();


        pushSender.send(pushPayload);
        if (airshipReceiver.postedAlerts.poll(15, TimeUnit.SECONDS) == null) {
            fail("Unable to send in-app message.");
        }


        // Click the in-app message
        onView(withText(inAppMessagePayload.getAlert()))
                .perform(click());

        // Verify the landing page opens
        intended(hasAction("com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION"));

        // Close the landing page
        Espresso.pressBack();

        // Test in-app message with interactive buttons
        pushSender.send(pushPayload);
        if (airshipReceiver.postedAlerts.poll(15, TimeUnit.SECONDS) == null) {
            fail("Unable to send in-app message.");
        }

        // Click YES button
        onView(withText("Yes"))
                .perform(click());

        assertTrue(UAirship.shared().getPushManager().getTags().contains("YES_INTERACTIVE_BUTTON_TAG"));
    }

    public static class TestAirshipReceiver extends AirshipReceiver {

        public final Object channelUpdateLock = new Object();
        public final BlockingQueue<String> postedAlerts = new LinkedBlockingQueue<>();

        @Override
        protected void onChannelUpdated(@NonNull Context context, @NonNull String channelId) {
            synchronized (channelUpdateLock) {
                channelUpdateLock.notifyAll();
            }
        }

        @Override
        protected void onChannelCreated(@NonNull Context context, @NonNull String channelId) {
            synchronized (channelUpdateLock) {
                channelUpdateLock.notifyAll();
            }
        }

        @Override
        protected void onNotificationPosted(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {
            postedAlerts.add(notificationInfo.getMessage().getAlert());
        }

        public void register() {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.urbanairship.push.CHANNEL_UPDATED");
            filter.addAction("com.urbanairship.push.OPENED");
            filter.addAction("com.urbanairship.push.DISMISSED");
            filter.addAction("com.urbanairship.push.RECEIVED");
            filter.addCategory(UAirship.getPackageName());

            UAirship.getApplicationContext().registerReceiver(this, filter, UAirship.getUrbanAirshipPermission(), new Handler(Looper.getMainLooper()));
        }

        public void unregister() {
            UAirship.getApplicationContext().unregisterReceiver(this);
        }

        public void waitForChannelUpdate() throws InterruptedException {
            synchronized (channelUpdateLock) {
                channelUpdateLock.wait(10000);
            }
        }
    }
}
