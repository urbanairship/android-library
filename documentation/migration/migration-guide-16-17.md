# Airship Android SDK 16.x to 17.0 Migration Guide

## Compile and Target SDK Versions

Urban Airship now requires `compileSdk` version 33 (Android 13) or higher.

Please update the `build.gradle` file:

###### Groovy
```groovy
android {
    compileSdkVersion 33

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 33

        // ...
    }
}
```

## Removed modules

The following modules are no longer supported and have been removed from the SDK:

### `urbanairship-accengage`

Users of Accengage should remove the `urbanairship-accengage` module from their project after completing the migration process.
For further information about migration and removal, see the [Accengage Migration guide](https://docs.airship.com/platform/mobile/accengage-migration/migration/android/index.html#remove-airship-accengage-module).

### `urbanairship-chat`

The Airship Chat module is no longer supported and has been removed from the SDK.

### `urbanairship-location`

The Airship Location module is no longer supported and has been removed from the SDK. If you want to continue prompting users for location permissions, you must update your integration to set a location permission delegate on the `PermissionsManager`:

###### Java
```java
UAirship.shared().getPermissionsManager().setPermissionDelegate(
    Permission.LOCATION,
    new SinglePermissionDelegate(Manifest.permission.ACCESS_COARSE_LOCATION)
);
```

###### Kotlin
```kotlin
UAirship.shared().permissionsManager.setPermissionDelegate(
    Permission.LOCATION,
    SinglePermissionDelegate(Manifest.permission.ACCESS_COARSE_LOCATION)
)
```

## Deprecated modules

### Android Preferences

The `urbanairship-preference` module has been deprecated and will be removed in a future SDK release.
Apps should either migrate to the `urbanairship-preference-center` module or maintain a copy of the current preference UI controls from the preference module.

### Ads Identifier

The `AdvertisingIdTracker` class in the `urbanairship-ads-identifier` module has been deprecated and will be removed in a future SDK release.
Apps making use of this module should migrate to using the `AssociatedIdentifiers.Editor` directly.

###### Java
```java
// Call from Autopilot onAirshipReady, or in Application onCreate.
public void updateAdvertisingId(@NonNull Context context){
    try {
        AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
        if (adInfo == null) {
            return;
        }
        
        advertisingId = adInfo.getId();
        limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled();
        if (advertisingId != null && limitedAdTrackingEnabled != null) {
            UAirship.shared().getAnalytics().editAssociatedIdentifiers()
                .setAdvertisingId(advertisingId, limitedAdTrackingEnabled)
                .apply();
        }
    } catch (IOException | GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
        Log.e(TAG, "Failed to retrieve and update advertising ID!", e);
    }
}
```

###### Kotlin
```kotlin
// Call from Autopilot onAirshipReady, or in Application onCreate.
fun updateAdvertisingId(context: Context) {
    try {
        val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context) ?: return
        val advertisingId = adInfo.id ?: return
        val limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled ?: return

        UAirship.shared().analytics.editAssociatedIdentifiers()
            .setAdvertisingId(advertisingId, limitedAdTrackingEnabled)
            .apply()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to retrieve and update advertising ID!", e)
    }
}
```

## Allowed URLs

The URL allow list configuration has been changed to an opt-out process, rather than an opt-in process like previous SDK versions.
By default, all URLs are allowed by SDK 17, unless explicitly disallowed by the app via the `urlAllowList` or `urlAllowListScopeOpen` config options.

Allow list behavior changes:
- If neither `urlAllowList` or `urlAllowListScopeOpen` are set in your Airship config, the SDK will default to allowing all URLs and an error message will be logged.
- To suppress the error message, set `urlAllowList` or `urlAllowListScopeOpen` to `[*]` to your config to adopt the new allow-all behavior, or customize the allowed URLs as needed.
- URLs for media displayed within in-app automation and in-app experiences will no longer be checked against the URL allow lists.
- YouTube has been removed from the default allow list. If your application makes use of opening links to YouTube from Airship messaging, you will need to update your `urlAllowListScopeOpen` list to explicitly allow `youtube.com`, or allow all URLs with `[*]`.

## In-App Automation

### Updated the default display interval for In-App Messages

The new default display interval for in-app messages is now set to 0 seconds. Apps that wish to maintain the previous default display interval of 30 seconds should set the display interval manually, in Autopilot or elsewhere after takeOff:

###### Java
```java
InAppAutomation.shared().getInAppMessageManager().setDisplayInterval(30, TimeUnit.SECONDS);
```

###### Kotlin
```kotlin
InAppAutomation.shared().inAppMessageManager.setDisplayInterval(30, TimeUnit.SECONDS)
```

## AirshipChannelListener.onChannelUpdated callback replaced by new PushNotificationStatusListener

The `AirshipChannel` class has changed, and the listener callback that receives channel update events has been removed:

###### Java
```java
UAirship.shared().getChannel().addChannelListener(new AirshipChannelListener() {
    @Override
    public void onChannelCreated(@NonNull String channelId) {
    }

    // Deprecated
    @Override
    public void onChannelUpdated(@NonNull String channelId) {
    }
});

// Replacement
UAirship.shared().getChannel().addChannelListener(new AirshipChannelListener() {
    @Override
    public void onChannelCreated(@NonNull String channelId) {
    }
}
```

The `onChannelUpdated` callback has been replaced with a new `PushNotificationStatusListener` that can be used to listen for push opt-in changes, and provides more detailed information about the various factors that determine the push notification status:

###### Java
```java
// Replacement
UAirship.shared().getPushManager().addNotificationStatusListener(status -> {
    Log.d(TAG, "Notification status: %s", status);
});
```

###### Kotlin
```kotlin
// Replacement
UAirship.shared().pushManager.addNotificationStatusListener { status ->
    Log.d(TAG, "Notification status: $status")
}
```

## Contact conflict listener interface updated

The `ContactConflictListener` interface has been simplified to emit a `ConflictEvent` object:

###### Java
```java
// Deprecated
contact.setContactConflictListener((anonymousContactData, namedUserId) -> {
    // Handle conflict...
});

// Replacement
contact.setContactConflictListener(conflictEvent -> {
    // Handle conflict...
});
```

## Subscription lists

Methods for getting subscription lists belonging to a `Contact` or `AirshipChannel` have been updated:

Java callers should migrate to the replacement methods that return a `PendingResult`:

```java
// Deprecated
contact.getSubscriptionLists();
channel.getSubscriptionLists();

// Replacement
contact.fetchSubscriptionListsPendingResult();
channel.fetchSubscriptionListsPendingResult();
```

In Kotlin, you may use the `PendingResult` methods above, or migrate to the new *suspend* function that returns a Kotlin `Result`:

```kotlin
// Deprecated
contact.getSubscriptionLists()
channel.getSubscriptionLists()

// Replacement
scope.launch {
    contact.fetchSubscriptionLists().getOrNull()?.let { lists ->
        // Handle lists
    }
}

scope.launch {
    channel.fetchSubscriptionLists().getOrNull()?.let { lists ->
        // Handle lists
    }
}
```

## Log listener

The internal logging system has been updated and the `Logger` class has been removed, along with the `LoggerListener` interface.
Apps that collect logs from the Airship SDK at runtime should migrate to the new `UALog` class and `AirshipLogHandler` interface:

###### Java
```java
// Deprecated
com.urbanairship.Logger.addListener((priority, throwable, message) -> {
    // Handle logs...
});

// Replacement
UALog.setLogHandler((tag, level, throwable, message) -> {
    // Handle logs...
});
```

###### Kotlin
```kotlin
// Deprecated
com.urbanairship.Logger.addListener { priority, throwable, message ->
    // Handle logs...
}

// Replacement
UALog.logHandler = AirshipLogHandler { tag, logLevel, throwable, message ->
    // Handle logs...
}
```