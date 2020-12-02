/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import android.os.Bundle;

import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.automation.Schedule;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.push.InternalNotificationListener;
import com.urbanairship.push.NotificationInfo;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LegacyInAppMessageManager}
 */
@RunWith(AndroidJUnit4.class)
public class LegacyInAppMessageManagerTest {

    LegacyInAppMessageManager legacyInAppMessageManager;
    PreferenceDataStore preferenceDataStore;
    InAppAutomation inAppAutomation;
    Analytics analytics;
    PushMessage pushMessage;
    PushManager pushManager;
    PushListener pushListener;
    InternalNotificationListener notificationListener;

    @Before
    public void setup() {
        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;
        inAppAutomation = mock(InAppAutomation.class);
        analytics = mock(Analytics.class);
        pushManager = mock(PushManager.class);
        legacyInAppMessageManager = new LegacyInAppMessageManager(TestApplication.getApplication(), preferenceDataStore, inAppAutomation, analytics, pushManager);

        String inAppJson = "{\"display\": {\"primary_color\": \"#FF0000\"," +
                "\"duration\": 10, \"secondary_color\": \"#00FF00\", \"type\": \"banner\"," +
                "\"alert\": \"Oh hi!\"}, \"actions\": {\"button_group\": \"ua_yes_no\"," +
                "\"button_actions\": {\"yes\": {\"^+t\": \"yes_tag\"}, \"no\": {\"^+t\": \"no_tag\"}}," +
                "\"on_click\": {\"^d\": \"someurl\"}}, \"expiry\": \"2015-12-12T12:00:00\", \"extra\":" +
                "{\"wat\": 123, \"Tom\": \"Selleck\"}}";

        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_IN_APP_MESSAGE, inAppJson);
        extras.putString(PushMessage.EXTRA_SEND_ID, "send id");

        pushMessage = new PushMessage(extras);

        ArgumentCaptor<PushListener> pushListenerArgumentCaptor = ArgumentCaptor.forClass(PushListener.class);
        ArgumentCaptor<InternalNotificationListener> notificationListenerArgumentCaptor = ArgumentCaptor.forClass(InternalNotificationListener.class);
        legacyInAppMessageManager.init();
        verify(pushManager).addInternalPushListener(pushListenerArgumentCaptor.capture());
        verify(pushManager).addInternalNotificationListener(notificationListenerArgumentCaptor.capture());

        pushListener = pushListenerArgumentCaptor.getValue();
        notificationListener = notificationListenerArgumentCaptor.getValue();
    }

    @Test
    public void testPushReceived() {
        pushListener.onPushReceived(pushMessage, true);

        verify(inAppAutomation).schedule(argThat(new ArgumentMatcher<Schedule<InAppMessage>>() {
            @Override
            public boolean matches(Schedule<InAppMessage> schedule) {
                if (!schedule.getId().equals("send id")) {
                    return false;
                }

                BannerDisplayContent displayContent = schedule.getData().getDisplayContent();
                if (!displayContent.getBody().getText().equals("Oh hi!")) {
                    return false;
                }

                if (displayContent.getDuration() != 10000) {
                    return false;
                }

                return true;
            }
        }));
    }

    @Test
    public void testPushReceivedCancelsPreviousIam() {
        // Receive the first push
        pushListener.onPushReceived(pushMessage, true);

        // Create a new push
        Bundle extras = pushMessage.getPushBundle();
        extras.putString(PushMessage.EXTRA_SEND_ID, "some other id");
        PushMessage otherPush = new PushMessage(extras);

        // Set up a pending result for a cancelled message
        PendingResult<Boolean> pendingResult = new PendingResult<>();
        pendingResult.setResult(true);
        when(inAppAutomation.cancelSchedule("send id")).thenReturn(pendingResult);

        // Receive the other push
        pushListener.onPushReceived(otherPush, true);

        // Verify it added a resolution event for the previous message
        verify(analytics).addEvent(any(ResolutionEvent.class));
    }


    @Test
    public void testPushReceivedPreviousAlreadyDisplayed() {
        // Receive the first push
        pushListener.onPushReceived(pushMessage, true);

        // Create a new push
        Bundle extras = pushMessage.getPushBundle();
        extras.putString(PushMessage.EXTRA_SEND_ID, "some other id");
        PushMessage otherPush = new PushMessage(extras);

        // Set up a pending result for a cancelled message
        PendingResult<Boolean> pendingResult = new PendingResult<>();
        pendingResult.setResult(false);
        when(inAppAutomation.cancelSchedule("send id")).thenReturn(pendingResult);

        // Receive the other push
        pushListener.onPushReceived(otherPush, true);

        // Verify it did not add a resolution event for the previous message
        verify(analytics, never()).addEvent(any(ResolutionEvent.class));
    }

    @Test
    public void testPushResponse() {
        // Receive the push
        pushListener.onPushReceived(pushMessage, true);

        // Set up a pending result for a cancelled message
        PendingResult<Boolean> pendingResult = new PendingResult<>();
        pendingResult.setResult(true);
        when(inAppAutomation.cancelSchedule("send id")).thenReturn(pendingResult);

        // Receive the response
        NotificationInfo info = new NotificationInfo(pushMessage, 1, "cool");
        notificationListener.onNotificationResponse(info, null);

        // Verify it added a resolution event for the message
        verify(analytics).addEvent(any(ResolutionEvent.class));
    }

    @Test
    public void testPushResponseAlreadyDisplayed() {
        // Receive the push
        pushListener.onPushReceived(pushMessage, true);

        // Set up a pending result for a cancelled message
        PendingResult<Boolean> pendingResult = new PendingResult<>();
        pendingResult.setResult(false);
        when(inAppAutomation.cancelSchedule("send id")).thenReturn(pendingResult);

        // Receive the response
        NotificationInfo info = new NotificationInfo(pushMessage, 1, "cool");
        notificationListener.onNotificationResponse(info, null);

        // Verify it did not add a resolution event for the message
        verify(analytics, never()).addEvent(any(ResolutionEvent.class));
    }

    @Test
    public void testMessageExtenderException() {
        legacyInAppMessageManager.setMessageBuilderExtender(new LegacyInAppMessageManager.MessageBuilderExtender() {
            @NonNull
            @Override
            public InAppMessage.Builder extend(@NonNull Context context, @NonNull InAppMessage.Builder builder, @NonNull LegacyInAppMessage legacyMessage) {
                throw new RuntimeException("exception!");
            }
        });

        // Receive the push
        pushListener.onPushReceived(pushMessage, true);

        // Verify we did not try to schedule an in-app message
        verifyZeroInteractions(inAppAutomation);
    }

    @Test
    public void testScheduleExtenderException() {
        legacyInAppMessageManager.setScheduleBuilderExtender(new LegacyInAppMessageManager.ScheduleBuilderExtender() {
            @NonNull
            @Override
            public Schedule.Builder extend(@NonNull Context context, @NonNull Schedule.Builder builder, @NonNull LegacyInAppMessage legacyMessage) {
                throw new RuntimeException("exception!");
            }
        });

        // Receive the push
        pushListener.onPushReceived(pushMessage, true);

        // Verify we did not try to schedule an in-app message
        verifyZeroInteractions(inAppAutomation);
    }
}
