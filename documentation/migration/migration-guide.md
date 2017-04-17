# Urban Airship Android SDK Migration Guide
# UrbanAirship Library 7.2.x to 8.0.0

Removed deprecated APIs from 7.x.

## Minimum SDK Version

Urban Airship now requires the minimum SDK version 16 (Jelly Bean).

Please update the `build.gradle` file:

```groovy
android {
   defaultConfig {
      minSdkVersion 16
   }
}
```

## Analytics

The methods to report activity started and stopped have been removed.

```java
// Removed
public static void activityStarted(Activity activity);
public static void activityStopped(Activity activity);
```

## PushManager

The GCM Instance ID and ADM ID retrieval methods have been consolidated into
one generalized method.

```java
// Old
UAirship.shared().getPushManager().getAdmId();
UAirship.shared().getPushManager().getGcmId();

// New
UAirship.shared().getPushManager().getRegistrationToken();
```

## Notifications

The [LocalizableRemoteInput.Builder](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/notifications/LocalizableRemoteInput.Builder.html) method `setChoices(int[] choices)`
has been removed and replaced with `setChoices(int choices)`.

## Notification Factories

The notification factories have been refactored.

The `SystemNotificationFactory` has been removed and replaced with the
[NotificationFactory](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/notifications/NotificationFactory.html). The `NotificationFactory` class now contains all
the setters from the default notification factory. The public notification, styles,
actions, and wearable notification setting have been moved into extender classes.

The [CustomLayoutNotificationFactory](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/notifications/CustomLayoutNotificationFactory.html) constructor takes in a custom content
view to display the notification. Custom binding can be applied by overriding
[CustomLayoutNotificationFactory.onBindContentView](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/notifications/CustomLayoutNotificationFactory.html#onBindContentView(android.widget.RemoteViews, com/urbanairship/push/PushMessage, int)).
To customize the builder, override [CustomLayoutNotificationFactory.extendBuilder](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/notifications/CustomLayoutNotificationFactory.html#extendBuilder(android.support.v4.app.NotificationCompat.Builder, com/urbanairship/push/PushMessage, int)).

The `createNotification` method in [DefaultNotificationFactory](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/notifications/DefaultNotificationFactory.html) is now
final, thus please use the [DefaultNotificationFactory.extendBuilder](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/notifications/DefaultNotificationFactory.html#extendBuilder(android.support.v4.app.NotificationCompat.Builder, com/urbanairship/push/PushMessage, int)) method instead.

# UrbanAirship Library 7.1.x to 7.2.x

## AirshipReceiver

`onChannelRegistrationSucceeded()` has been replaced by [AirshipReceiver.onChannelCreated](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/AirshipReceiver.html#onChannelCreated(android.content.Context, java.lang.String))
and [AirshipReceiver.onChannelUpdated](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/AirshipReceiver.html#onChannelUpdated(android.content.Context, java.lang.String)).

## NamedUser

[NamedUser](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/NamedUser.html) access has been moved to [UAirship](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/UAirship.html).

```java
// Old
UAirship.shared().getPushManager().getNamedUser()

// New
UAirship.shared().getNamedUser()
```

## GCM Integrations

The internal GCM integration has been updated to not conflict with other integrations and no longer triggers the
GcmListenerService when receiving GCM messages. Any application that contains additional GCM clients outside of Urban
Airship needs to register the GcmReceiver from Google Play Services in the AndroidManifest.xml. The instructions provided in
[Set up a GCM Client App on Android](https://developers.google.com/cloud-messaging/android/client) can now be followed
without adjustment.

# UrbanAirship Library 7.0.x to 7.1.x

## AirshipReceiver

`BaseIntentReceiver` has been deprecated in favor of [AirshipReceiver](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/AirshipReceiver.html).

```java
// Old inheritable methods
protected abstract void onChannelRegistrationSucceeded(@NonNull Context context, @NonNull String channelId);
protected abstract void onChannelRegistrationFailed(@NonNull Context context);
protected abstract void onPushReceived(@NonNull Context context, @NonNull PushMessage message, int notificationId);
protected abstract void onBackgroundPushReceived(@NonNull Context context, @NonNull PushMessage message);
protected abstract boolean onNotificationOpened(@NonNull Context context, @NonNull PushMessage message, int notificationId);
protected abstract boolean onNotificationActionOpened(@NonNull Context context, @NonNull PushMessage message, int notificationId, @NonNull String buttonId, boolean isForeground);
protected void onNotificationDismissed(@NonNull Context context, @NonNull PushMessage message, int notificationId) {}

// New inheritable methods
protected void onChannelRegistrationSucceeded(@NonNull Context context, @NonNull String channelId) {}
protected void onChannelRegistrationFailed(@NonNull Context context) {}
protected void onPushReceived(@NonNull Context context, @NonNull PushMessage message, boolean notificationPosted) {}
protected void onNotificationPosted(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {}
protected boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {}
protected boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo, @NonNull notificationButtonInfo actionButtonInfo) {}
protected void onNotificationDismissed(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {}
```

The method signatures have been simplified and now accept [AirshipReceiver.NotificationInfo](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/AirshipReceiver.NotificationInfo.html) and [AirshipReceiver.ActionButtonInfo](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/AirshipReceiver.ActionButtonInfo.html) objects instead of disparate fields.
[AirshipReceiver.NotificationInfo](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/AirshipReceiver.NotificationInfo.html) contains the relevant [PushMessage](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/push/PushMessage.html) and notification ID while [AirshipReceiver.ActionButtonInfo](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/AirshipReceiver.ActionButtonInfo.html) contains fields pertinent to the
notification action button.

`onPushReceived()` and `onBackgroundPushReceived` have been replaced by `onPushReceived()` and `onNotificationPosted()`.
`onPushReceived()` now accepts a boolean flag indicating whether or not the notification will be in the foreground,
and `onNotificationPosted()` will be called immediately after in the event that a notification is posted.

## Tag Editor API

An Editor API is now provided for tag mutation operations.

```java
// Old
Set<String> tags = new HashSet<String>();
tags = UAirship.shared().getPushManager().getTags();
tags.add("Some-Tag");
UAirship.shared().getPushManager().setTags(tags);

// New
UAirship.shared().getPushManager().editTags()
    .addTag("some_tag")
    .removeTag("some_other_tag")
    .apply();
```

## AssociatedIdentifiers Editor API

An Editor API is now provided for associated identifier mutation operations replacing the
deprecated `associateIdentifiers` method. Use [Analytics.editAssociatedIdentifiers](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/analytics/Analytics.html#editAssociatedIdentifiers%28%29).

```java
// Old
AssociatedIdentifiers.Builder identifiers = new AssociatedIdentifiers.Builder()
        .setIdentifier("customIdentifierKey", "customIdentifierValue")
        .create();

UAirship.shared().getAnalytics().associateIdentifiers(identifiers);

// New
UAirship.shared().getAnalytics()
        .editAssociatedIdentifiers()
        .addIdentifier("customIdentifierKey", "customIdentifierValue")
        .apply();
```

***See [legacy migration guide](migration-guide-legacy.md) for older migrations***
