/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.sample;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.AirshipReceiver;
import com.urbanairship.UAirship;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test basic pushes, message center and in-app messages using UIAutomator.
 * <p/>
 * To run the test suite on emulator or device with API 21+:
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.appKey="APP_KEY" -Pandroid.testInstrumentationRunnerArguments.masterSecret="MASTER_SECRET"
 */
@RunWith(AndroidJUnit4.class)
public class SampleTest {

    static final String TEST_ALIAS_STRING = UUID.randomUUID().toString();
    static final String TEST_NAMED_USER_STRING = UUID.randomUUID().toString();
    static final String TEST_TAG_STRING = UUID.randomUUID().toString();

    private String channelId;
    private UiDevice device;
    private PushSender pushSender;
    private TestAirshipReceiver airshipReceiver;
    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class);

    @Before
    public void setup() throws InterruptedException {
        Bundle arguments = InstrumentationRegistry.getArguments();
        // Get the app key and master secret
        String appKey = arguments.getString("appKey");
        String masterSecret = arguments.getString("masterSecret");

        pushSender = new PushSender(masterSecret, appKey);

        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

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

        UAirship.shared().getInAppMessageManager().setAutoDisplayDelay(0);
        UAirship.shared().getInAppMessageManager().setDisplayAsapEnabled(true);

        channelId = UAirship.shared().getPushManager().getChannelId();

        // Make sure we have a channel
        assertFalse(UAStringUtil.isEmpty(channelId));
    }

    @After
    public void cleanup() {
        device.pressHome();
        airshipReceiver.unregister();
    }

    /**
     * Test setting alias, named user, tag and pushes to them, including broadcast and channel pushes.
     */
    @Test
    public void testPush() throws Exception {
        UAirship.shared().getNamedUser().setId(TEST_NAMED_USER_STRING);
        UAirship.shared().getPushManager().editTags().addTag(TEST_TAG_STRING).apply();
        UAirship.shared().getPushManager().setAlias(TEST_ALIAS_STRING);

        // Wait for registration to finish
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        PushPayload channelPayload = PushPayload.newChannelPushBuilder(channelId)
                                                .setAlert(UUID.randomUUID().toString())
                                                .build();
        pushSender.send(channelPayload);

        PushPayload broadcastPayload = PushPayload.newBroadcastPushBuilder()
                                                  .setAlert(UUID.randomUUID().toString())
                                                  .build();
        pushSender.send(broadcastPayload);

        PushPayload aliasPayload = PushPayload.newAliasPushBuilder(TEST_ALIAS_STRING)
                                              .setAlert(UUID.randomUUID().toString())
                                              .build();
        pushSender.send(aliasPayload);

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
        expectedAlerts.put(broadcastPayload.getAlert(), "broadcast");
        expectedAlerts.put(aliasPayload.getAlert(), "alias");
        expectedAlerts.put(tagPayload.getAlert(), "tag");
        expectedAlerts.put(namedUserPayload.getAlert(), "named user");

        for (int i = 0; i < 5; i++) {
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
                                                             .setTitle(UUID.randomUUID().toString())
                                                             .setHtmlContent("Hello")
                                                             .build();

            PushPayload payload = PushPayload.newChannelPushBuilder(channelId)
                                             .setAlert(UUID.randomUUID().toString())
                                             .setRichPushMessage(richPushPayload)
                                             .build();
            pushSender.send(payload);
            UiObject userAlert = device.findObject(new UiSelector().textContains(payload.getAlert()));
            userAlert.waitForExists(5000);
        }

        UAirship.shared().getInbox().fetchMessages();

        // Navigate to MC
        UiObject openNavigationDrawer = device.findObject(new UiSelector().description("Open navigation drawer"));
        openNavigationDrawer.click();

        UiObject messageCenter = device.findObject(new UiSelector().text("Message Center"));
        messageCenter.click();

        // Verify messages exist in message center by swiping down to refresh the inbox
        UiObject inbox = device.findObject(new UiSelector().className("android.widget.ListView"));
        inbox.waitForExists(5000);
        inbox.swipeDown(50);

        int originalMessageCount = inbox.getChildCount();
        assertEquals("Expect 2 messages.", 2, originalMessageCount);

        UiObject topMessage = inbox.getChild(new UiSelector().className("android.widget.FrameLayout").index(0));
        topMessage.waitForExists(5000);
        assertTrue(topMessage.exists());

        // open message to make sure it is read
        topMessage.click();

        // navigate back to inbox
        UiObject navigateUp = device.findObject(new UiSelector().description("Navigate up"));
        navigateUp.click();

        UiObject messageCheckBox = device.findObject(new UiSelector().resourceId("com.urbanairship.sample:id/checkbox"));
        messageCheckBox.waitForExists(2000);
        messageCheckBox.click();

        UiObject moreOptions = device.findObject(new UiSelector().description("More options"));
        moreOptions.waitForExists(2000);
        moreOptions.click();

        // select all to mark read
        UiObject selectAll = device.findObject(new UiSelector().text("Select All"));
        selectAll.waitForExists(2000);
        selectAll.click();
        UiObject markReadAction = device.findObject(new UiSelector().description("Mark Read"));
        markReadAction.waitForExists(2000);
        markReadAction.click();

        // verify all messages read
        assertEquals(UAirship.shared().getInbox().getUnreadCount(), 0);

        messageCheckBox.click();
        moreOptions = device.findObject(new UiSelector().description("More options"));
        moreOptions.waitForExists(2000);
        moreOptions.click();

        // select all to delete the remaining messages
        selectAll = device.findObject(new UiSelector().text("Select All"));
        selectAll.waitForExists(2000);
        selectAll.click();
        UiObject deleteAction = device.findObject(new UiSelector().description("Delete"));
        deleteAction.click();

        inbox = device.findObject(new UiSelector().className("android.widget.ListView"));

        // verify all messages are deleted
        assertFalse(inbox.exists());
    }

    /**
     * Test for an in-app message with landing page and interactive buttons.
     *
     * @throws Exception
     */
    @Test
    public void testInAppMessage() throws Exception {

        // Open external url action for yes interactive button
        ActionsPayload yesActions = ActionsPayload.newBuilder()
                                                  .setOpenUrl("https://aprojectforkindness.files.wordpress.com/2014/01/pay-it-forward-heart.jpeg")
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
                                                                     .setAlert("in-app landing page test")
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

        // click on in-app message
        UiObject inAppMesg = device.findObject(new UiSelector().text(inAppMessagePayload.getAlert()));
        inAppMesg.waitForExists(10000);
        inAppMesg.click();

        // verify landing page opens
        UiObject landingPage = device.findObject(new UiSelector().className("android.webkit.WebView"));
        landingPage.waitForExists(10000);
        landingPage.swipeUp(100);
        assertTrue(landingPage.exists());

        // close landing page.
        UiObject closeButton = device.findObject(new UiSelector().resourceId("com.urbanairship.sample:id/close_button"));
        closeButton.click();

        // test in-app message with interactive buttons
        pushSender.send(pushPayload);

        // click YES button
        UiObject yesButton = device.findObject(new UiSelector().text("Yes"));
        yesButton.waitForExists(10000);
        yesButton.click();

        // verify web page opens
        UiObject page = device.findObject(new UiSelector().className("android.webkit.WebView"));
        page.waitForExists(5000);
        assertTrue(page.exists());

        // Back to the app
        device.pressBack();

        // send in-app message
        pushSender.send(pushPayload);

        // click NO button
        UiObject noButton = device.findObject(new UiSelector().text("No"));
        noButton.waitForExists(5000);
        noButton.click();

        assertTrue(UAirship.shared().getPushManager().getTags().contains("NO_INTERACTIVE_BUTTON_TAG"));
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
