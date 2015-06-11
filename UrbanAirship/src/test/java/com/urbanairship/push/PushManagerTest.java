package com.urbanairship.push;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.push.notifications.NotificationFactory;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PushManagerTest extends BaseTestCase {

    Analytics mockAnalytics;
    private final String fakeChannelId = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeChannelLocation = "https://go.urbanairship.com/api/channels/AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final String fakeNamedUserId = "fakeNamedUserId";

    PushPreferences mockPushPreferences;
    PushManager pushManager;
    NamedUser mockNamedUser;

    private NotificationManagerCompat mockNotificationManager;
    private Notification notification;
    private Context context = UAirship.getApplicationContext();
    private PushMessage pushMessage;
    private PushMessage backgroundMessage;
    private NotificationFactory notificationFactory;

    public int constantNotificationId = 123;
    public int iconDrawableId = UAirship.getAppIcon();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() {

        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        extras.putString(PushMessage.EXTRA_PUSH_ID, "testPushID");

        pushMessage = new PushMessage(extras);

        Bundle backgroundMessageExtras = new Bundle();
        backgroundMessageExtras.putString(PushMessage.EXTRA_PUSH_ID, "anotherPushID");

        backgroundMessage = new PushMessage(backgroundMessageExtras);

        notification = new NotificationCompat.Builder(RuntimeEnvironment.application)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .setSmallIcon(iconDrawableId)
                .build();

        mockAnalytics = mock(Analytics.class);
        Mockito.doNothing().when(mockAnalytics).addEvent(any(Event.class));
        TestApplication.getApplication().setAnalytics(mockAnalytics);

        mockPushPreferences = mock(PushPreferences.class);
        mockNotificationManager = mock(NotificationManagerCompat.class);

        mockNamedUser = mock(NamedUser.class);

        notificationFactory = new NotificationFactory(TestApplication.getApplication()) {
            @Override
            public Notification createNotification(PushMessage pushMessage, int notificationId) {
                return notification;
            }

            @Override
            public int getNextId(PushMessage pushMessage) {
                return constantNotificationId;
            }
        };

        pushManager = new PushManager(TestApplication.getApplication(), mockPushPreferences, mockNamedUser, mockNotificationManager);
        pushManager.setNotificationFactory(notificationFactory);
    }

    /**
     * Test deliver push notification.
     */
    @Test
    public void testDeliverPush() {
        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);

        pushManager.deliverPush(pushMessage);

        verify(mockNotificationManager).notify(constantNotificationId, notification);

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notification.contentIntent);
        assertTrue("The pending intent is broadcast intent.", shadowPendingIntent.isBroadcastIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_OPENED_PROXY);
        assertEquals("The push message should match.", pushMessage, intent.getExtras().get(PushManager.EXTRA_PUSH_MESSAGE));
        assertEquals("One category should exist.", 1, intent.getCategories().size());
    }

    /**
     * Test deliver background notification.
     */
    @Test
    public void testDeliverPushUserPushDisabled() {

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(false);

        pushManager.deliverPush(pushMessage);

        List<Intent> intents = shadowApplication.getBroadcastIntents();
        Intent i = intents.get(intents.size() - 1);
        Bundle extras = i.getExtras();
        PushMessage push = extras.getParcelable(PushManager.EXTRA_PUSH_MESSAGE);
        assertEquals("Intent action should be push received", i.getAction(), PushManager.ACTION_PUSH_RECEIVED);
        assertEquals("Push ID should equal pushMessage ID", pushMessage.getCanonicalPushId(), push.getCanonicalPushId());
        assertEquals("No notification ID should be present", extras.getInt(PushManager.EXTRA_NOTIFICATION_ID, -1), -1);
    }


    /**
     * Test deliver background notification.
     */
    @Test
    public void testDeliverBackgroundPush() {

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(false);

        pushManager.deliverPush(backgroundMessage);

        List<Intent> intents = shadowApplication.getBroadcastIntents();
        Intent i = intents.get(intents.size() - 1);
        Bundle extras = i.getExtras();
        PushMessage push = extras.getParcelable(PushManager.EXTRA_PUSH_MESSAGE);
        assertEquals("Intent action should be push received", i.getAction(), PushManager.ACTION_PUSH_RECEIVED);
        assertEquals("Push ID should equal pushMessage ID", backgroundMessage.getCanonicalPushId(), push.getCanonicalPushId());
        assertEquals("No notification ID should be present", extras.getInt(PushManager.EXTRA_NOTIFICATION_ID, -1), -1);
    }

    /**
     * Test handling an exception
     */
    @Test
    public void testDeliverPushException() {
        Bundle extras = new Bundle();
        PushMessage pushMessage = new PushMessage(extras);

        notificationFactory = new NotificationFactory(TestApplication.getApplication()) {
            @Override
            public Notification createNotification(PushMessage pushMessage, int notificationId) {
                throw new RuntimeException("Unable to create and display notification.");
            }

            @Override
            public int getNextId(PushMessage pushMessage) {
                return 0;
            }
        };

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);

        pushManager.setNotificationFactory(notificationFactory);
        pushManager.deliverPush(pushMessage);

        verify(mockNotificationManager, Mockito.never()).notify(Mockito.anyInt(), any(Notification.class));
    }

    /**
     * Test notification content intent
     */
    @Test
    public void testNotificationContentIntent() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, new Intent(), 0);
        notification = new NotificationCompat.Builder(context)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .setSmallIcon(iconDrawableId)
                .setContentIntent(pendingIntent)
                .build();

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);

        pushManager.deliverPush(pushMessage);

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notification.contentIntent);
        assertTrue("The pending intent is broadcast intent.", shadowPendingIntent.isBroadcastIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_OPENED_PROXY);
        assertEquals("One category should exist.", 1, intent.getCategories().size());
        assertNotNull("The notification content intent is not null.", pendingIntent);
        assertSame("The notification content intent matches.", pendingIntent, intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT));
    }


    /**
     * Test notification delete intent
     */
    @Test
    public void testNotificationDeleteIntent() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 1, new Intent(), 0);
        notification = new NotificationCompat.Builder(context)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .setSmallIcon(iconDrawableId)
                .setDeleteIntent(pendingIntent)
                .build();

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);

        pushManager.deliverPush(pushMessage);

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notification.deleteIntent);
        assertTrue("The pending intent is broadcast intent.", shadowPendingIntent.isBroadcastIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_DISMISSED_PROXY);
        assertEquals("One category should exist.", 1, intent.getCategories().size());
        assertNotNull("The notification delete intent is not null.", pendingIntent);
        assertSame("The notification delete intent matches.", pendingIntent, intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT));
    }

    /**
     * Test when sound is disabled the flag for DEFAULT_SOUND is removed and the notification sound
     * is set to null.
     */
    @Test
    public void testDeliverPushSoundDisabled() {
        // Enable push
        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);

        // Disable sound
        when(mockPushPreferences.isSoundEnabled()).thenReturn(false);

        notification.sound = Uri.parse("some://sound");
        notification.defaults = NotificationCompat.DEFAULT_ALL;
        pushManager.deliverPush(pushMessage);

        pushManager.deliverPush(pushMessage);
        assertNull("The notification sound should be null.", notification.sound);
        assertEquals("The notification defaults should not include DEFAULT_SOUND.",
                notification.defaults & NotificationCompat.DEFAULT_SOUND, 0);
    }

    /**
     * Test when sound is disabled the flag for DEFAULT_VIBRATE is removed and the notification vibrate
     * is set to null.
     */
    @Test
    public void testDeliverPushVibrateDisabled() {
        // Enable push
        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);

        // Disable vibrate
        when(mockPushPreferences.isVibrateEnabled()).thenReturn(false);

        notification.defaults = NotificationCompat.DEFAULT_ALL;
        notification.vibrate = new long[] { 0L, 1L, 200L };

        pushManager.deliverPush(pushMessage);
        assertNull("The notification sound should be null.", notification.vibrate);
        assertEquals("The notification defaults should not include DEFAULT_VIBRATE.",
                notification.defaults & NotificationCompat.DEFAULT_VIBRATE, 0);
    }

    /**
     * Test delivering a push with an in-app message sets the pending notification.
     */
    @Test
    public void testDeliverPushInAppMessage() {
        // Enable push
        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);

        InAppMessage inAppMessage = new InAppMessage.Builder()
                .setAlert("oh hi")
                .setExpiry(1000l)
                .setId("what")
                .create();

        PushMessage message = mock(PushMessage.class);
        when(message.getInAppMessage()).thenReturn(inAppMessage);

        pushManager.deliverPush(message);

        assertEquals(inAppMessage, UAirship.shared().getInAppMessageManager().getPendingMessage());
    }

    /**
     * Test the notification defaults: in quiet time.
     */
    @Test
    public void testInQuietTime() {
        when(mockPushPreferences.isVibrateEnabled()).thenReturn(true);
        when(mockPushPreferences.isSoundEnabled()).thenReturn(true);
        when(mockPushPreferences.isInQuietTime()).thenReturn(true);


        pushManager.deliverPush(pushMessage);
        assertNull("The notification sound should be null.", notification.sound);
        assertEquals("The notification defaults should not include vibrate or sound.", 0, notification.defaults);
    }

    /**
     * Test enabling push.
     */
    @Test
    public void testPushEnabled() {
        pushManager.setPushEnabled(true);
        verify(mockPushPreferences).setPushEnabled(true);
    }

    /**
     * Test disabling push
     */
    @Test
    public void testPushDisabled() {
        when(mockPushPreferences.isPushEnabled()).thenReturn(true);

        pushManager.setPushEnabled(false);
        verify(mockPushPreferences).setPushEnabled(false);
    }

    /**
     * Test enable QuietTime
     */
    @Test
    public void testQuietTimeEnabled() {
        pushManager.setQuietTimeEnabled(true);
        verify(mockPushPreferences).setQuietTimeEnabled(true);
    }

    /**
     * Test disable QuietTime
     */
    @Test
    public void testQuietTimeDisabled() {
        pushManager.setQuietTimeEnabled(false);
        verify(mockPushPreferences).setQuietTimeEnabled(false);
    }

    /**
     * Test enable sound
     */
    @Test
    public void testSoundEnabled() {
        pushManager.setSoundEnabled(true);
        verify(mockPushPreferences).setSoundEnabled(true);
    }

    /**
     * Test disable sound
     */
    @Test
    public void testSoundDisabled() {
        pushManager.setSoundEnabled(false);
        verify(mockPushPreferences).setSoundEnabled(false);
    }

    /**
     * Test enable vibrate
     */
    @Test
    public void testVibrateEnabled() {
        pushManager.setVibrateEnabled(true);
        verify(mockPushPreferences).setVibrateEnabled(true);
    }

    /**
     * Test disable vibrate
     */
    @Test
    public void testVibrateDisabled() {
        pushManager.setVibrateEnabled(false);
        verify(mockPushPreferences).setVibrateEnabled(false);
    }

    /**
     * Test set tags
     */
    @Test
    public void testTags() {
        HashSet<String> tags = new HashSet<>();
        tags.add("$xf*\"\"kkfj");
        tags.add("'''''7that'sit\"");
        tags.add("here's,some,comma,separated,stuff");

        pushManager.setTags(tags);
        verify(mockPushPreferences).setTags(tags);
    }

    /**
     * Tests adding a null tag via setTags results in IllegalArgumentException.
     */
    @Test
    public void testSetNullTags() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tags must be non-null.");

        pushManager.setTags(null);
    }

    /**
     * Tests adding a null tag via setAliasAndTags results in IllegalArgumentException.
     */
    @Test
    public void testSetNullTagsAliasAndTags() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tags must be non-null.");

        pushManager.setTags(null);
    }

    /**
     * Tests trimming of tag's white space when tag is only white space.
     */
    @Test
    public void testSetTagsWhiteSpaceTrimmedToEmpty() {
        HashSet<String> mockTags = new HashSet<>();
        mockTags.add("tag");
        when(mockPushPreferences.getTags()).thenReturn(mockTags);

        HashSet<String> tags = new HashSet<>();
        tags.add(" ");
        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests trimming of tag's white space.
     */
    @Test
    public void testNormalizeTagsWhiteSpaceTrimmedToValid() {
        HashSet<String> tags = new HashSet<>();

        tags.add("    whitespace_test_tag    ");

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("whitespace_test_tag");
            }
        }));
    }

    /**
     * Tests that tag length of 128 chars cannot be set.
     */
    @Test
    public void testNormalizeTagsOverMaxLength() {
        HashSet<String> mockTags = new HashSet<>();
        mockTags.add("tag");
        when(mockPushPreferences.getTags()).thenReturn(mockTags);

        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests that max tag length of 127 chars can be set.
     */
    @Test
    public void testNormalizeTagsMaxLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("127_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[");

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("127_chars_lkashdflsfghekjashdflkjhsdfkjhsadk" +
                        "fjhskdnvpeortoivnk84389349843982ij32" +
                        "1masdflkjahsdgkfjandsgkljhasdg'k./l'" +
                        ".][;l].k,/[");
            }
        }));
    }

    /**
     * Tests that zero length tag cannot be set.
     */
    @Test
    public void testNormalizeTagsZeroLength() {
        HashSet<String> mockTags = new HashSet<>();
        mockTags.add("tag");
        when(mockPushPreferences.getTags()).thenReturn(mockTags);

        HashSet<String> tags = new HashSet<>();
        tags.add("");
        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests that a null tag cannot be set.
     */
    @Test
    public void testNormalizeTagsNullTag() {
        HashSet<String> tags = new HashSet<>();
        tags.add(null);

        HashSet<String> mockTags = new HashSet<>();
        mockTags.add("tag");
        when(mockPushPreferences.getTags()).thenReturn(mockTags);

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests passing an empty set clears the tags.
     */
    @Test
    public void testNormalizeTagsEmptySet() {
        HashSet<String> tags = new HashSet<>();
        tags.add("testTag");
        when(mockPushPreferences.getTags()).thenReturn(tags);

        pushManager.setTags(new HashSet<String>());
        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));
    }

    /**
     * Tests the removal of a bad tag from a 2 tag set.
     */
    @Test
    public void testNormalizeTagsMixedTagSet() {
        HashSet<String> tags = new HashSet<>();
        tags.add("testTag");
        tags.add("");
        when(mockPushPreferences.getTags()).thenReturn(tags);

        pushManager.setTags(tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("testTag");
            }
        }));
    }

    /**
     * Tests setting alias and tags.
     */
    @Test
    public void testAliasAndTags() {
        HashSet<String> tags = new HashSet<>();

        tags.add("a_tag");

        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("a_tag");
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests trimming of tag's white space when tag is only white space.
     */
    @Test
    public void testNormalizeAliasAndTagsWhiteSpaceTrimmedToEmpty() {
        HashSet<String> tags = new HashSet<>();

        tags.add(" ");
        //add another test tag to allow updateApid call
        tags.add("test_tag");


        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests trimming of tag's white space.
     */
    @Test
    public void testNormalizeAliasAndTagsWhiteSpaceTrimmedToValid() {
        HashSet<String> tags = new HashSet<>();

        tags.add("    whitespace_test_tag    ");

        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("whitespace_test_tag");
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests that tag length of 128 chars cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsOverMaxLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");
        //add another test tag to pass tag changes check
        tags.add("test_tag");


        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests that max tag length of 127 chars can be set.
     */
    @Test
    public void testNormalizeAliasAndTagsMaxLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("127_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[");

        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1 && set.contains("127_chars_lkashdflsfghekjashdflkjhsdfkjhsadk" +
                        "fjhskdnvpeortoivnk84389349843982ij32" +
                        "1masdflkjahsdgkfjandsgkljhasdg'k./l'" +
                        ".][;l].k,/[");
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests that zero length tag cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsZeroLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("");
        //add another test tag to allow updateApid call
        tags.add("test_tag");


        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests that a null tag cannot be set.
     */
    @Test
    public void testNormalizeAliasAndTagsNullTag() {
        HashSet<String> tags = new HashSet<>();

        tags.add(null);
        //add another test tag to allow updateApid call
        tags.add("test_tag");


        pushManager.setAliasAndTags("sandwich", tags);

        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 1;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));
    }

    /**
     * Tests passing an empty set clears the tags.
     */
    @Test
    public void testNormalizeAliasAndTagsEmptySet() {
        HashSet<String> tags = new HashSet<>();

        tags.add("testTag");

        when(mockPushPreferences.getTags()).thenReturn(tags);

        pushManager.setAliasAndTags("sandwich", new HashSet<String>());
        verify(mockPushPreferences).setTags(argThat(new ArgumentMatcher<Set<String>>() {
            @Override
            public boolean matches(Object o) {
                Set<String> set = (Set<String>) o;
                return set.size() == 0;
            }
        }));

        verify(mockPushPreferences).setAlias(argThat(new ArgumentMatcher<String>() {
            @Override
            public boolean matches(Object o) {
                String string = (String) o;
                return string.equals("sandwich");
            }
        }));

    }

    /**
    * Test set trimmed alias
    */
    @Test
    public void testTrimmedAlias() {
        pushManager.setAlias("    whitespace_test_alias    ");
        verify(mockPushPreferences).setAlias("whitespace_test_alias");
    }

    /**
     * Tests that we can set and get tags.
     */
    @Test
    public void testNormalizeGetTags() {
        HashSet<String> tags = new HashSet<>();

        tags.add("test_tag");
        when(mockPushPreferences.getTags()).thenReturn(tags);
        pushManager.setTags(tags);

        assertEquals("Tags should be equal", tags, pushManager.getTags());
    }

    /**
     * Tests that get tags will normalize the invalid tags.
     */
    @Test
    public void testNormalizeGetTagsWhiteSpace() {
        HashSet<String> tags = new HashSet<>();

        tags.add(" test_tag ");

        HashSet<String> normalizedTags = new HashSet<>();

        normalizedTags.add("test_tag");

        when(mockPushPreferences.getTags()).thenReturn(tags);
        assertEquals("Tags should be equal", normalizedTags, pushManager.getTags());
    }

    /**
     * Tests getTags for tags greater than MAX_TAG_LENGTH
     */
    @Test
    public void testNormalizeGetTagsLength() {
        HashSet<String> tags = new HashSet<>();

        tags.add("128_chars_lkashdflsfghekjashdflkjhsdfkjhsadkfjhskdnvpeortoivnk84389349843982ij321" +
                "masdflkjahsdgkfjandsgkljhasdg'k./l'.][;l].k,/[1");
        when(mockPushPreferences.getTags()).thenReturn(tags);

        assertEquals("Tags should be equal", new HashSet<String>(), pushManager.getTags());
    }

    /*
     * Test set alias
     */
    @Test
    public void testAlias() {
        pushManager.setAlias("someAlias");
        verify(mockPushPreferences).setAlias("someAlias");
    }

    /**
     * Test set GCM ID
     */
    @Test
    public void testSetGcmId() {
        pushManager.setGcmId("fakeGcmId");
        verify(mockPushPreferences).setGcmId("fakeGcmId");
    }

    /**
     * Test set ADM ID
     */
    @Test
    public void testSetAdmId() {
        pushManager.setAdmId("fakeAdmId");
        verify(mockPushPreferences).setAdmId("fakeAdmId");
    }

    /**
     * Test OptIn is false when push is disabled
     */
    @Test
    public void testOptInPushDisabled() {
        when(mockPushPreferences.isPushEnabled()).thenReturn(false);
        when(mockPushPreferences.getGcmId()).thenReturn("fakeGcmId");

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test OptIn is true for Amazon
     */
    @Test
    public void testOptInAmazon() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);
        when(mockPushPreferences.getAdmId()).thenReturn("fakeAdmId");

        assertEquals("OptIn should be true", true, pushManager.isOptIn());
    }

    /**
     * Test OptIn is true for Android
     */
    @Test
    public void testOptInAndroid() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getUserNotificationsEnabled()).thenReturn(true);

        when(mockPushPreferences.getGcmId()).thenReturn("fakeGcmId");

        assertEquals("OptIn should be true", true, pushManager.isOptIn());
    }

    /**
     * Test OptIn is false when GCM Id is null
     */
    @Test
    public void testOptInGCMIdNull() {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getGcmId()).thenReturn(null);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test OptIn is false when ADM Id is null
     */
    @Test
    public void testOptInADMIdNull() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        when(mockPushPreferences.isPushEnabled()).thenReturn(true);
        when(mockPushPreferences.getAdmId()).thenReturn(null);

        assertEquals("OptIn should be false", false, pushManager.isOptIn());
    }

    /**
     * Test getNextChannelRegistrationPayload returns a payload with android device and GCM ID
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAndroid() throws JSONException {
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);

        when(mockPushPreferences.getChannelId()).thenReturn(fakeChannelId);
        when(mockPushPreferences.getChannelLocation()).thenReturn(fakeChannelLocation);
        when(mockPushPreferences.getGcmId()).thenReturn("GCM_ID");

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertEquals(payload.asJSON().getJSONObject("channel").get("device_type"), "android");
        assertEquals(payload.asJSON().getJSONObject("channel").get("push_address"), "GCM_ID");
    }

    /**
     * Test getNextChannelRegistrationPayload returns a payload amazon device and ADM ID
     */
    @Test
    public void testGetNextChannelRegistrationPayloadAmazon() throws JSONException {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        when(mockPushPreferences.getChannelId()).thenReturn(fakeChannelId);
        when(mockPushPreferences.getChannelLocation()).thenReturn(fakeChannelLocation);
        when(mockPushPreferences.getAdmId()).thenReturn("ADM_ID");


        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        assertNotNull("The payload should not be null.", payload);
        assertEquals(payload.asJSON().getJSONObject("channel").get("device_type"), "amazon");
        assertEquals(payload.asJSON().getJSONObject("channel").get("push_address"), "ADM_ID");
    }

    /**
     * Test Urban Airship notification action button groups are available
     */
    @Test
    public void testUrbanAirshipNotificationActionButtonGroups() {
        Set<String> keys = NotificationActionButtonGroupFactory.createUrbanAirshipGroups().keySet();
        assertTrue(keys.size() > 0);

        for (String key : keys) {
            assertNotNull("Missing notification button group with ID: " + key, pushManager.getNotificationActionGroup(key));
        }
    }

    /**
     * Test trying to add a notification action button group with the reserved prefix
     */
    @Test
    public void testAddingNotificationActionButtonGroupWithReservedPrefix() {
        pushManager.addNotificationActionButtonGroup("ua_my_test_id", new NotificationActionButtonGroup.Builder().build());
        assertNull("Should not be able to add groups with prefix ua_", pushManager.getNotificationActionGroup("ua_my_test_id"));
    }

    /**
     * Test trying to remove a notification action button group with the reserved prefix
     */
    @Test
    public void testRemovingNotificationActionButtonGroupWithReservedPrefix() {
        Set<String> keys = NotificationActionButtonGroupFactory.createUrbanAirshipGroups().keySet();

        for (String key : keys) {
            pushManager.removeNotificationActionButtonGroup(key);
            assertNotNull("Should not be able to remove notification button group with ID: " + key, pushManager.getNotificationActionGroup(key));
        }
    }

    /**
     * Test init starts named user update service.
     */
    @Test
    public void testInitStartNamedUserUpdateService() {
        pushManager.init();
        verify(mockNamedUser).startUpdateService();
    }
}
