# Airship Android SDK Migration Guide

## UrbanAirship Library 9.x to 10.0.0

### Breaking Changes

#### Removed urbanairship-gcm and urbanairship-sdk Modules

Both `urbanairship-gcm` and `urbanairship-sdk` have been removed. Please
migrate to `urbanairship-fcm` and or `urbanairship-adm` instead. For GCM to FCM
migration, please follow the [FCM Migration Guide](https://github.com/urbanairship/android-library/tree/master/documentation/migration/migration-guide-fcm.md).

#### Auto Tracking Advertising ID

Advertising ID auto tracking has been removed from the core library. A new package
`urbanairship-ad-identifier` is available to enable this behavior:

```java
AdvertisingIdTracker.shared(context).setEnabled(enabled);
```

Alternatively you can gather the Advertising ID manually and notify Urban Airship
by editing the associated identifiers:

```java
UAirship.shared().getAnalytics()
                 .editAssociatedIdentifiers()
                 .setAdvertisingId(advertisingId, isLimitedTrackingEnabled)
                 .apply();
```

#### Preferences

The provided Airship preferences have been moved into its own package `urbanairship-preference`
and each preference has been updated to extend the support preferences instead of the
deprecated system preferences. Applications that are using the old Airship preferences need to update
to `PreferenceFragmentCompat` to continue using Airship preferences. For more details, read the
[Settings developer guide](https://developer.android.com/guide/topics/ui/settings.html).

Quiet time, sound, and vibration have been removed due to the features being deprecated.

#### AirshipReceiver Removed

With the new restrictions introduced in Android Q, we can no longer support the `AirshipReceiver`
implementation. The receiver has been replaced with 3 listeners - `NotificationListener`, `PushListener`,
and `RegistrationListener`. Each listener can be set on the Push Manager class and should be done in either
the `Autopilot#onAirshipReady(UAirship)` or the `OnReadyCallback` when calling takeOff manually.

*NotificationListener*

```java
// AirshipReceiver - 9.x
@MainThread
void onNotificationPosted(@NonNull Context context, @NonNull NotificationInfo notificationInfo);

// NotificationListener - 10.x
@WorkerThread
void onNotificationPosted(@NonNull NotificationInfo notificationInfo);


// AirshipReceiver - 9.x
@MainThread
boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo);

// NotificationListener - 10.x
@MainThread
boolean onNotificationOpened(@NonNull NotificationInfo notificationInfo);


// AirshipReceiver - 9.x
@MainThread
boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo, @NonNull ActionButtonInfo actionButtonInfo);

// NotificationListener - 10.x
@MainThread
boolean onNotificationForegroundAction(@NonNull NotificationInfo notificationInfo, @NonNull NotificationActionButtonInfo actionButtonInfo);
void onNotificationBackgroundAction(@NonNull NotificationInfo notificationInfo, @NonNull NotificationActionButtonInfo actionButtonInfo);


// AirshipReceiver - 9.x
@MainThread
void onNotificationDismissed(@NonNull Context context, @NonNull NotificationInfo notificationInfo);

// NotificationListener - 10.x
@MainThread
void onNotificationDismissed(@NonNull NotificationInfo notificationInfo);
```

Setting the listener:

```java
void onAirshipReady(@NonNull UAirship airship) {
    NotificationListener listener = new SampleNotificationListener();
    airship.getPushManager().setNotificationListener(listener);
}
```

*PushListener*

```java
// AirshipReceiver - 9.x
@MainThread
void onPushReceived(@NonNull Context context, @NonNull PushMessage message, boolean notificationPosted);

// PushListener - 10.x
@WorkerThread
void onPushReceived(@NonNull PushMessage message, boolean notificationPosted);
```


Adding a push listener:

```java
void onAirshipReady(@NonNull UAirship airship) {
    PushListener listener = new SamplePushListener();
    airship.getPushManager().addPushListener(listener);
}
```

*RegistrationListener*

The registration listener contains only the useful methods from `AirshipReceiver`. The
listener will only be called when a channel is created and the push token is updated. Apps
no longer have the ability to get a call whenever the channel is updated or failed to update.

```java
// AirshipReceiver - 9.x
@MainThread
void onChannelUpdated(@NonNull Context context, @NonNull String channelId);
@MainThread
void onChannelCreated(@NonNull Context context, @NonNull String channelId);
@MainThread
void onChannelRegistrationFailed(@NonNull Context context);

// PushListener - 10.x
 @WorkerThread
void onChannelCreated(@NonNull String channelId);
@WorkerThread
void onPushTokenUpdated(@NonNull String token);
```

Adding a registration listener:

```java
void onAirshipReady(@NonNull UAirship airship) {
    RegistrationListener listener = new SampleRegistrationListener();
    airship.getPushManager().addRegistrationListener(listener);
}
```

#### PushManager: Removed Notification Factory Getter

The notification factory has been deprecated. Applications can still set a factory on the manager but it will
be wrapped in a new NotificationProvider instance. See [Deprecations](#Deprecations) for more info.

#### Kotlin Interop

To better support Kotlin interop, all public APIs now have nullability annotations and a few
methods had to be changed to move the lambda to the last parameter.

##### UAirship

```java
// 9.x
public static Cancelable shared(final OnReadyCallback callback, Looper looper)

// 10.x
public static Cancelable shared(@Nullable Looper looper, @NonNull final OnReadyCallback)
```

##### ActionRegistry#Entry

```java
// 9.x
public void addSituationOverride(@NonNull Action action, @Action.Situation int situation)

// 10.x
public void setSituationOverride(@Action.Situation int situation, @Nullable Action action)
```

##### ActionRunner#ActionRunRequest

```java
// 9.x
public void run(final ActionCompletionCallback callback, Looper looper)

// 10.x
public void run(@Nullable Looper looper, @Nullable final ActionCompletionCallback callback)
```

##### RichPushInbox

```java
// 9.x
public Cancelable fetchMessages(final FetchMessagesCallback callback, Looper looper)

// 10.x
public Cancelable fetchMessages(@Nullable Looper looper, @NonNull FetchMessagesCallback callback)
```

##### ActionRegistry

Removed:
```java
public Entry registerAction(@NonNull Class<? extends Action> c, Predicate predicate, @NonNull String... names)
```

Instead, you can set the predicate after registering the action:

```java
UAirship.shared().getActionRegistry()
        .registerAction(SomeAction.class, "some-action-name")
        .setPredicate(predicate);
```

#### ActivityMonitor Changes

ActivityMonitor is now an interface and is located in com.urbanairship.app package. GlobalActivityMonitor
is a replacement for the old ActivityMonitor singleton. The old ActivityMonitor.Listener has been broken
out into two listener classes - ApplicationListener and ActivityListener. ApplicationListener contains calls
for app foreground/background events while the ActivityListener is used only for activity events.

#### In App Automation/Message Changes

Several updates have been made to the In App Automation frameworks. Most apps will
not be affected by these changes, only those that have implemented custom display
logic.

##### DisplayHandler Changes

The `requestDisplayLock` method has been renamed to `isDisplayAllowed`:

```java
// 9.x
public boolean requestDisplayLock(@NonNull Activity activity)

// 10.x
public boolean isDisplayAllowed(@NonNull Context context)
```

The method `continueOnNextActivity` has been removed. It is expected that the InAppMessageAdapter
will handle displaying the message across activities.

##### InAppMessageAdapter Changes

`InAppMessageAdapter` has been updated to handle the new asset caching functionality and
to no longer be dependent on a resumed activity:

```java
// 9.x
int onPrepare(@NonNull Context context);
boolean isReady(@NonNull Activity activity);
boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler);
void onFinish();

// 10.x
int onPrepare(@NonNull Context context, @NonNull Assets assets);
boolean isReady(@NonNull Context context);
void onDisplay(@NonNull Context context, @NonNull DisplayHandler displayHandler);
void onFinish(@NonNull Context context);
```

If the message adapter requires a resumed activity to display,
check for a resumed activity in the `isReady` method:

```java
public boolean isReady(@NonNull Context context) {
    return !InAppActivityMonitor.shared(context).getResumedActivities().isEmpty();
}
```

The adapter's `onDisplay` no longer returns a boolean. Any checks that might prevent
the in-app message from displaying should be moved into `isReady`.

#### InAppMessageCache Replaced with Assets

Message assets such as media can now be prefetched when the message is scheduled instead
of just before display. To support this new behavior `InAppMessageCache` has been replaced
with `Assets`. An instance of `Assets` will be provided to the display adapter during
`onPrepare`. The instance will be managed by the SDK.

*Creating a file*
```java
// 9.x
messageCache.file("file_name");

// 10.x
assets.file("some-key");
```

*Storing metadata*
```
// 9.x
messageCache.getBundle().putString("metadata");

// 10.x
assets.setMetadata("some-key", JsonValue.wrap("some value"));
```

#### BannerFragment Replaced with BannerView

Due to the system `Fragment` being deprecated, the Banner messages now use a BannerView
and are attached directly to the activity.

### Deprecations

#### NotificationFactory

The notification factory has been deprecated in favor of the new `NotificationProvider`. The
new provider allows the app more control over the tag and notification channel than supported
by the factory. Apps can still set a notification factory on the push manager and it will
automatically be wrapped as a provider.

Applications that want to take advantage of all Urban Airship features should extend `AirshipNotificationProvider` and override `onExtendBuilder`.

*Configuring the notification*

```java
// Notification Factory - 9.x
boolean requiresLongRunningTask(@NonNull PushMessage message);
int getNextId(@NonNull PushMessage pushMessage);

// Notification Provider - 10.x
NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message);
```

The notification arguments created during `onCreateNotificationArguments` can be used to
specify the channel id, notification id, tag, and whether or not the notification requires
a long running task. Those arguments are then passed into `onCreateNotification` and will
be used when posting the notification. Example:

```java
public NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message) {
    String channelId = message.getNotificationChannel(NotificationProvider.DEFAULT_NOTIFICATION_CHANNEL);
    int notificationId = NotificationIdGenerator.nextID();

    return NotificationArguments.newBuilder(message)
                                .setNotificationChannelId(channelId)
                                .setNotificationId("sample-tag", notificationId)
                                .setRequiresLongRunningTask(false)
                                .build();
}
```

*Building the notification*

```java
// Notification Factory - 9.x
Notification createNotification(@NonNull final PushMessage message, final int notificationId);
Result createNotificationResult(@NonNull final PushMessage message, final int notificationId, boolean isLongRunningTask);

// Notification Provider - 10.x
NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments);

```

The `onCreateNotification` will be called to create the notification. The app should not post
the notification directly to the notification manager, instead return the notification and
the SDK will post it on behave of the app. Example:

```java
public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
    if (UAStringUtil.isEmpty(arguments.getMessage().getAlert())) {
        return NotificationResult.cancel();
    }

    ...

    if (failure) {
        return NotificationResult.retry();
    }

    return NotificationResult.notification(notification);
}
```

#### Sound, Quiet Time, and Vibration Overrides

Sound, quiet time, and vibration features have been deprecated because they are unsupported on
Android O and above. Applications targeting Android O+ are required to use notification channels
instead. To support channels on older Android versions apps can use `NotificationChannelCompat`
and the Urban Airship SDK will apply notification channel settings on older devices. The channel
can be specified in the composer and API to control each notification behavior separately.

#### FCM Provider Changes

`AirshipFirebaseInstanceIdService` was deprecated due to `FirebaseInstanceIdService` being deprecated and removed. Use `AirshipFirebaseMessagingService` or `AirshipFirebaseIntegration.processNewToken(Context)` instead.

### From JSON Method Normalization

All from/parse JSON methods have been updated to have a consistent signature:

```
@NonNull
static <CLASS_NAME> fromJson(@NonNull JsonValue value) throws JsonException;
```

### Builder Normalization

All classes that expose a builder now follow the same pattern, with a factory method
named `newBuilder` and a `build` method instead of `create`.

