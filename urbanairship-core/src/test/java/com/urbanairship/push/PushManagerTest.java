/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.R;
import com.urbanairship.TestApplication;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;

import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PushManager}.
 */
public class PushManagerTest extends BaseTestCase {

    private PushManager pushManager;
    private PreferenceDataStore preferenceDataStore;
    private AirshipConfigOptions options;

    private JobDispatcher mockDispatcher;
    private AirshipChannel mockAirshipChannel;
    private PushProvider mockPushProvider;

    @Before
    public void setup() {
        mockDispatcher = mock(JobDispatcher.class);
        mockAirshipChannel = mock(AirshipChannel.class);
        mockPushProvider = mock(PushProvider.class);

        preferenceDataStore = TestApplication.getApplication().preferenceDataStore;

        options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, options,
                mockPushProvider, mockAirshipChannel, mockDispatcher);
    }

    /**
     * Test init starts push registration if the registration token is not available.
     */
    @Test
    public void testInit() {
        pushManager.init();

        verify(mockDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManager.ACTION_UPDATE_PUSH_REGISTRATION);
            }
        }));

        verifyNoMoreInteractions(mockDispatcher);
    }

    /**
     * Test enabling push.
     */
    @Test
    public void testPushEnabled() {
        pushManager.setPushEnabled(true);
        assertTrue(preferenceDataStore.getBoolean(PushManager.PUSH_ENABLED_KEY, false));
        verify(mockAirshipChannel).updateRegistration();
    }

    /**
     * Test disabling push
     */
    @Test
    public void testPushDisabled() {
        pushManager.setPushEnabled(false);
        assertFalse(preferenceDataStore.getBoolean(PushManager.PUSH_ENABLED_KEY, true));
        verify(mockAirshipChannel).updateRegistration();
    }

    /**
     * Test on registering for a push token.
     */
    @Test
    public void testPushRegistration() throws PushProvider.RegistrationException {
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());

        verify(mockAirshipChannel).updateRegistration();
    }

    /**
     * Test OptIn is only true if push and notifications are enabled and we have a push token.
     */
    @Test
    public void testOptIn() throws PushProvider.RegistrationException {
        assertFalse(pushManager.isOptIn());

        pushManager.setPushEnabled(true);
        assertFalse(pushManager.isOptIn());

        pushManager.setUserNotificationsEnabled(true);
        assertFalse(pushManager.isOptIn());

        // Register for a token
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);

        assertTrue(pushManager.isOptIn());

        pushManager.setPushEnabled(false);
        assertFalse(pushManager.isOptIn());

        pushManager.setPushEnabled(true);
        pushManager.setUserNotificationsEnabled(false);
        assertFalse(pushManager.isOptIn());
    }

    /**
     * Test Airship notification action button groups are available
     */
    @Test
    public void testUrbanAirshipNotificationActionButtonGroups() {
        Set<String> keys = ActionButtonGroupsParser.fromXml(RuntimeEnvironment.application, R.xml.ua_notification_buttons).keySet();
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
        pushManager.addNotificationActionButtonGroup("ua_my_test_id", NotificationActionButtonGroup.newBuilder().build());
        assertNull("Should not be able to add groups with prefix ua_", pushManager.getNotificationActionGroup("ua_my_test_id"));
    }

    /**
     * Test trying to remove a notification action button group with the reserved prefix
     */
    @Test
    public void testRemovingNotificationActionButtonGroupWithReservedPrefix() {
        Set<String> keys = ActionButtonGroupsParser.fromXml(RuntimeEnvironment.application, R.xml.ua_notification_buttons).keySet();

        for (String key : keys) {
            pushManager.removeNotificationActionButtonGroup(key);
            assertNotNull("Should not be able to remove notification button group with ID: " + key, pushManager.getNotificationActionGroup(key));
        }
    }

    /**
     * Test channel registration extender when push is opted in.
     */
    @Test
    public void testChannelRegistrationExtenderOptedIn() throws PushProvider.RegistrationException {
        ArgumentCaptor<AirshipChannel.ChannelRegistrationPayloadExtender> argument = ArgumentCaptor.forClass(AirshipChannel.ChannelRegistrationPayloadExtender.class);
        pushManager.init();
        verify(mockAirshipChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.ChannelRegistrationPayloadExtender extender = argument.getValue();
        assertNotNull(extender);

        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        pushManager.setUserNotificationsEnabled(true);
        pushManager.setPushEnabled(true);

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();

        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setBackgroundEnabled(true)
                .setOptIn(true)
                .setPushAddress("token")
                .build();

        assertEquals(expected, payload);
    }

    /**
     * Test channel registration extender when push is opted out.
     */
    @Test
    public void testChannelRegistrationExtenderOptedOut() {
        ArgumentCaptor<AirshipChannel.ChannelRegistrationPayloadExtender> argument = ArgumentCaptor.forClass(AirshipChannel.ChannelRegistrationPayloadExtender.class);
        pushManager.init();
        verify(mockAirshipChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.ChannelRegistrationPayloadExtender extender = argument.getValue();
        assertNotNull(extender);

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();

        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setBackgroundEnabled(false)
                .setOptIn(false)
                .build();

        assertEquals(expected, payload);
    }

    /**
     * Test channel registration extender when token registration is disabled.
     */
    @Test
    public void testChannelRegistrationDisabledTokenRegistration() throws PushProvider.RegistrationException {
        ArgumentCaptor<AirshipChannel.ChannelRegistrationPayloadExtender> argument = ArgumentCaptor.forClass(AirshipChannel.ChannelRegistrationPayloadExtender.class);
        pushManager.init();
        verify(mockAirshipChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.ChannelRegistrationPayloadExtender extender = argument.getValue();
        assertNotNull(extender);

        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        pushManager.setUserNotificationsEnabled(true);
        pushManager.setPushEnabled(true);
        pushManager.setPushTokenRegistrationEnabled(false);

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();

        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setBackgroundEnabled(false)
                .setOptIn(false)
                .build();

        assertEquals(expected, payload);
    }

    /**
     * Test enabling token registration updates channel registration.
     */
    @Test
    public void testEnablingTokenRegistrationUpdatesChannel() {
        pushManager.setPushTokenRegistrationEnabled(true);
        verify(mockAirshipChannel).updateRegistration();
    }

    /**
     * Test enabling the component updates token registration.
     */
    @Test
    public void testComponentEnabled() {
        pushManager.onComponentEnableChange(true);

        verify(mockDispatcher, times(1)).dispatch(Mockito.argThat(new ArgumentMatcher<JobInfo>() {
            @Override
            public boolean matches(JobInfo jobInfo) {
                return jobInfo.getAction().equals(PushManager.ACTION_UPDATE_PUSH_REGISTRATION);
            }
        }));
    }
}
