# Airship Android SDK 19.x to 20.x Migration Guide

The Airship SDK 20.0 introduces major architectural changes including the renaming of the core `UAirship` class to `Airship`, comprehensive Java-to-Kotlin migration, module restructuring for Message Center and Preference Center, and new Jetpack Compose UI components. The minimum compile SDK is raised to Android 15 (API 36). This guide outlines the necessary changes for migrating your app from SDK 19.x to SDK 20.x.

**Required Migration Tasks:**
- Update `compileSdk` to 36 and `minSdk` to 23.
- Update breaking API changes

**Optional Migration Tasks:**
- Rename all `UAirship` references to `Airship` throughout your codebase
- Update `Autopilot.onAirshipReady()` signature to include `Context` parameter
- Update module dependencies for Message Center and Preference Center if using custom UIs
- Migrate to new suspend function APIs for better Kotlin intergation
- Adopt Kotlin Flow-based APIs instead of listener-based patterns
- Use new Compose UI modules for Message Center and Preference Center

## Table of Contents

- [Breaking Changes](#breaking-changes)
  - [UAirship API Changes](#uairship-api-changes)
  - [Platform Type Update](#platform-type-update)
  - [Log Level](#log-level)
  - [Final Classes and Methods](#final-classes-and-methods)
  - [Removed Utility Classes](#removed-utility-classes)
  - [NotificationIdGenerator API Changes](#notificationidgenerator-api-changes)
  - [Minor Push Notification Changes](#minor-push-notification-changes)
  - [Permissions Manager API Changes](#permissions-manager-api-changes)
- [Deprecated APIs](#deprecated-apis)
  - [UAirship Deprecated](#uairship-deprecated)
  - [Autopilot Signature Change](#autopilot-signature-change)
  - [Flow and PendingResult Renames](#flow-and-pendingresult-renames)
- [Kotlin Migration](#kotlin-migration)
  - [Kotlin DSL Convenience APIs](#kotlin-dsl-convenience-apis)
  - [Suspend Function APIs](#suspend-function-apis)
  - [Flow-Based APIs](#flow-based-apis)
- [New Compose UI Modules](#new-compose-ui-modules)
  - [Message Center Compose](#message-center-compose)
  - [Preference Center Compose](#preference-center-compose)
- [Troubleshooting](#troubleshooting)

## Breaking Changes

This section details changes that will cause build failures or change runtime behavior if not addressed. Some APIs listed here have deprecated fallbacks for backward compatibility, but we strongly recommend migrating to the new APIs.

### UAirship API Changes

The `UAirship` compatibility class has undergone several changes. In addition to `UAirship` being deprecated, many helper methods have been removed.

#### Removed Static Methods
The following static utility methods have been removed from `UAirship`:
- `getAppName()`
- `getPackageName()`
- `getPackageManager()`
- `getPackageInfo()`
- `getAppInfo()`
- `getAppIcon()`
- `getAppVersion()`
- `isMainProcess()`

These utilities were primarily helpers around the application `Context`. You can now achieve the same functionality by accessing the `PackageManager` directly from a context.

**Example:**
```java
// 19.x
String appName = UAirship.getAppName();

// Replacement 
String appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo()).toString();
```

#### Removed Instance Getters
Several component getters on the `UAirship` instance have been removed to simplify the API surface of the compatibility class. The following getters are no longer available on `UAirship`:
- `getApplicationMetrics()`
- `getInputValidator()`

These getters have been removed as they were intended for internal use only.

### Platform Type Update

The platform type constants have been changed from integer constants to an enum.

| SDK 19.x                    | SDK 20.0           |
|-----------------------------|--------------------|
| `UAirship.AMAZON_PLATFORM`  | `Platform.AMAZON`  |
| `UAirship.ANDROID_PLATFORM` | `Platform.ANDROID` |
| `UAirship.UNKNOWN_PLATFORM` | `Platform.UNKNOWN` |

The accessor has updated to just `platform` instead of `platformType`.

###### Java
```java
// Removed
int platform = UAirship.ANDROID_PLATFORM;

// On new Airship singleton
Platform platform = Airship.getPlatform(); 

// Deprecated UAirship singleton
Platform platform = UAirship.shared().getPlatform(); 
```

###### Kotlin
```kotlin
// Removed
val platform = UAirship.ANDROID_PLATFORM

// On new Airship singleton
val platform: Platform = Airship.platform

// Deprecated UAirship singleton
val platform: Platform = UAirship.shared().platform
```

### Log Level
The `logLevel` in `AirshipConfigOptions` is now an enum `AirshipConfigOptions.LogLevel` instead of an integer. 

### Final Classes and Methods
As part of the migration to Kotlin, many classes and methods that were previously open for extension are now `final`. This is a convention in Kotlin to prefer composition over inheritance. If you were subclassing any of the following classes, you will need to refactor your usage. If you have a use case that requires subclassing, please open a GitHub issue to discuss it with us.
- `PushMessage`
- `ActionsNotificationExtender`
- `LocalizableRemoteInput` and its `Builder`
- `NotificationActionButton` and its `Builder`
- `NotificationActionButtonGroup` and its `Builder`
- `NotificationArguments` and its `Builder`
- `NotificationChannelCompat`
- `PublicNotificationExtender`
- `StyleNotificationExtender`
- `WearableNotificationExtender`
- `Field` (from `wallet`)
- `Pass` (from `wallet`)
- `PassRequest` (from `wallet`)

### NotificationIdGenerator API Changes
The `NotificationIdGenerator` class has been converted to a Kotlin `object`. All public methods that modify the generator's state now require a `Context` parameter.

**Example:**
```java
// 19.x
int id = NotificationIdGenerator.nextID();

// 20.x
int id = NotificationIdGenerator.nextId(context);
```
```kotlin
// 19.x
val id = NotificationIdGenerator.nextID()

// 20.x
val id = NotificationIdGenerator.nextId(context)
```

### Minor Push Notification Changes

Several classes and methods in the `push` package have been updated.

- **`PushProvider.getPlatform()`**: This method now returns a `Platform` enum instead of an `int`.
- **`PushProvider.DeliveryType`**: This is now a Kotlin enum instead of a String annotation.
- **`NotificationResult.Status`**: This is now a Kotlin enum instead of an `int`.

### Permissions Manager API Changes
The `PermissionsManager` has been updated to adopt more idiomatic Kotlin `suspend` functions.

- The primary `suspend` functions have been renamed:
  - `suspendingRequestPermission` is now `requestPermission`.
  - `suspendingCheckPermissionStatus` is now `checkPermissionStatus`.
- The original callback-based `requestPermission` and `checkPermissionStatus` methods still exist for Java compatibility, but Kotlin callers will now resolve to the new `suspend` functions, which is a breaking change.

**Migration:**
Update any calls to `suspendingRequestPermission` and `suspendingCheckPermissionStatus` to the new names. If you were using the callback-based methods from Kotlin, you must now use the new `suspend` functions or the `PendingResult` alternative.

**Example (Suspending):**
```kotlin
// 19.x
coroutineScope.launch {
    val result = permissionsManager.suspendingRequestPermission(permission)
    // Handle result
}

// 20.x
coroutineScope.launch {
    val result = permissionsManager.requestPermission(permission)
    // Handle result
}
```

**Example (Non-Suspending):**
```java
// Using PendingResult for a non-suspending alternative
permissionsManager.requestPermissionPendingResult(permission)
    .addResultCallback(result -> {
        // Handle result
    });
```


## Deprecated APIs

### UAirship Deprecated

The `UAirship` class has been deprecated and replaced by the new `Airship` Kotlin object. A `UAirship` compatibility class is still available but will be removed in a future release. The new `Airship` object provides a more modern, Kotlin-friendly API.

The primary change is the move from an instance-based singleton (`UAirship.shared()`) to a static object (`Airship`).

**Migration Steps:**
1.  Find and replace all instances of `UAirship` with `Airship`.
2.  Update all imports from `com.urbanairship.UAirship` to `com.urbanairship.Airship`.
3.  Replace all calls to `UAirship.shared()` with direct access to the `Airship` object, as detailed below.

#### Java API Changes

| SDK 19.x API                                  | SDK 20.x API                             |
| :-------------------------------------------- | :--------------------------------------- |
| `UAirship.shared()`                           | `Airship`                                |
| `UAirship.takeOff(...)`                       | `Airship.takeOff(...)`                   |
| `UAirship.shared().getPushManager()`          | `Airship.getPush()`                      |
| `UAirship.shared().getChannel()`              | `Airship.getChannel()`                   |
| `UAirship.shared().getAnalytics()`            | `Airship.getAnalytics()`                 |
| `UAirship.shared().getContact()`              | `Airship.getContact()`                   |
| `UAirship.shared().getPrivacyManager()`       | `Airship.getPrivacyManager()`            |
| `UAirship.shared().getPermissionsManager()`   | `Airship.getPermissionsManager()`        |
| `UAirship.shared().getUrlAllowList()`         | `Airship.getUrlAllowList()`              |
| `UAirship.shared().getActionRegistry()`       | `Airship.getActionRegistry()`            |
| `UAirship.shared().getAirshipConfigOptions()` | `Airship.getAirshipConfigOptions()`      |
| `UAirship.shared().deepLink(String)`          | `Airship.deepLink(String)`               |
| `UAirship.shared(callback)`                   | `Airship.onReady(callback)`              |
| `UAirship.waitForTakeOff(long)`               | `Airship.waitForReadyBlocking(Duration)` |

#### Kotlin API Changes

| SDK 19.x API                            | SDK 20.x API                   |
| :-------------------------------------- | :----------------------------- |
| `UAirship.shared()`                     | `Airship`                      |
| `UAirship.takeOff(...)`                 | `Airship.takeOff(...)`         |
| `UAirship.shared().pushManager`         | `Airship.push`                 |
| `UAirship.shared().channel`             | `Airship.channel`              |
| `UAirship.shared().analytics`           | `Airship.analytics`            |
| `UAirship.shared().contact`             | `Airship.contact`              |
| `UAirship.shared().privacyManager`      | `Airship.privacyManager`       |
| `UAirship.shared().permissionsManager`  | `Airship.permissionsManager`   |
| `UAirship.shared().urlAllowList`        | `Airship.urlAllowList`         |
| `UAirship.shared().actionRegistry`      | `Airship.actionRegistry`       |
| `UAirship.shared().airshipConfigOptions`| `Airship.airshipConfigOptions` |
| `UAirship.shared().deepLink(String)`    | `Airship.deepLink(String)`     |
| `UAirship.shared { ... }`               | `Airship.onReady { ... }`      |
| `UAirship.waitForTakeOff(long)`         | `Airship.waitForReady()`       |

### Flow and PendingResult Renames
Several `Flow` and `PendingResult` APIs have been renamed for clarity. The old names are deprecated and will be removed in a future release.

- **`Contact`**:
  - `subscriptions` is now `subscriptionListsFlow`.
  - `channelContacts` is now `contactChannelsFlow`.
- **`FeatureFlagManager`**:
  - `flagAsPendingResult` is now `flagPendingResult`.

### Autopilot Signature Change
The `Autopilot.onAirshipReady()` method signature has been updated to include a `Context` parameter.

###### Java
```java
// SDK 19.x
public class MyAutopilot extends Autopilot {
    @Override
    public void onAirshipReady(@NonNull UAirship airship) {
        airship.getPushManager().setUserNotificationsEnabled(true);
    }
}
// SDK 20.0
public class MyAutopilot extends Autopilot {
    @Override
    public void onAirshipReady(@NonNull Context context) {
        Airship.push.setUserNotificationsEnabled(true);
    }
}
```

###### Kotlin
```kotlin
// SDK 19.x
class MyAutopilot : Autopilot() {
    override fun onAirshipReady(airship: UAirship) {
        airship.pushManager.userNotificationsEnabled = true
    }
}
// SDK 20.0
class MyAutopilot : Autopilot() {
    override fun onAirshipReady(context: Context) {
        Airship.push.userNotificationsEnabled = true
    }
}
```

The old `onAirshipReady(airship: UAirship)` method is deprecated but still available for backward compatibility. We recommend updating to the new `onAirshipReady(context: Context)` method.


## Kotlin Migration

SDK 20.0 has migrated core SDK classes from Java to Kotlin and introduced several Kotlin-friendly API improvements. These changes are largely **backward compatible**, but new APIs are available for improved Kotlin usage.

### Kotlin DSL Convenience APIs

SDK 20.0 introduces Kotlin DSL convenience methods that automatically call `apply()` for you, reducing boilerplate code.

###### Kotlin
```kotlin
// SDK 19.x and 20.0 (Explicit apply - still supported)
Airship.channel.editTags()
    .addTag("vip")
    .apply()

// SDK 20.0 (Kotlin DSL - optional, apply is automatic)
Airship.channel.editTags {
    addTag("vip")
}
```

The following APIs now have Kotlin DSL convenience methods:

- `AirshipChannel.editTags()`
- `AirshipChannel.editTagGroups()`
- `AirshipChannel.editAttributes()`
- `Contact.editSubscriptionLists()`
- `Contact.editAttributes()`
- `Contact.editTagGroups()`

### Suspend Function APIs

Several APIs now have suspend function alternatives for Kotlin coroutines and async/await patterns. The callback-based APIs remain available.

###### Kotlin
```kotlin
// SDK 19.x (Callback-based - still supported)
Airship.push.enableUserNotifications { enabled ->
    if (enabled) {
        // Notifications enabled
    }
}

// SDK 20.0 (Suspend function - optional)
CoroutineScope(Dispatchers.Main).launch {
    val enabled = Airship.push.enableUserNotifications()
    if (enabled) {
        // Notifications enabled
    }
}
```

The following APIs now support suspend functions:

- `PushManager.enableUserNotifications()`
- `NotificationChannelRegistry.getNotificationChannel(id: String)`
- `ActionRunRequest.runSuspending()`
- `Contact.fetchSubscriptionLists()`
- `Contact.validateSms()`
- `AirshipChannel.fetchSubscriptionLists()`

### Flow-Based APIs

Several listener-based APIs now have Kotlin Flow alternatives for reactive programming.

###### Kotlin
```kotlin
// SDK 19.x (Listener-based - still supported)
Airship.shared().privacyManager.addListener { enabledFeatures ->
    updateUI(enabledFeatures)
}

// SDK 20.0 (Flow-based - optional)
CoroutineScope(Dispatchers.Main).launch {
    Airship.shared().privacyManager.featureUpdates.collect { enabledFeatures ->
        updateUI(enabledFeatures)
    }
}
```

The following components now provide Flow-based APIs:

- `PrivacyManager.featureUpdates` - Flow of enabled features
- `LocaleManager.localeUpdates` - Flow of locale changes
- `PermissionsManager.permissionStatusUpdates` - Flow of permission status changes



## New Compose UI Modules

SDK 20.0 introduces new Jetpack Compose UI modules for Message Center and Preference Center.

The Message Center module has been split into three modules:

| SDK 19.x                         | SDK 20.0                                 | Description                                      |
|----------------------------------|------------------------------------------|--------------------------------------------------|
| `urbanairship-message-center`    | `urbanairship-message-center-core`       | Core functionality, data models, and API (NEW)   |
| `urbanairship-message-center`    | `urbanairship-message-center`            | Traditional View-based UI (now depends on -core) |
| n/a                              | `urbanairship-message-center-compose`    | Jetpack Compose UI (includes -core)              |
| `urbanairship-preference-center` | `urbanairship-preference-center-core`    | Core functionality, config, and API (NEW)        |
| `urbanairship-preference-center` | `urbanairship-preference-center`         | Traditional View-based UI (now depends on -core) |
| n/a                              | `urbanairship-preference-center-compose` | Jetpack Compose UI (includes -core)              |


### Message Center Compose

###### Kotlin
```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import com.urbanairship.messagecenter.compose.ui.*

@Composable
fun MyApp() {
    // Full Message Center, with toolbars and navigation between the list and message screens
    MessageCenterScreen()

    // Separate list and message components, with toolbars
    MessageCenterListScreen(
        onMessageSelected = { message ->
            // Navigate to the message screen
        }
    )

    @OptIn(ExperimentalMaterial3Api::class)
    MessageCenterMessageScreen(
        state = rememberMessageCenterMessageState(messageId = "message-id"),
        onClose = {
            // Navigate up or otherwise close the message screen
        }
    )

    // Separate list and message components, without toolbars
    MessageCenterList(
        onMessageClick = { message ->
            // Navigate to the message view
        }
    )

    MessageCenterMessage(
        state = rememberMessageCenterMessageState(messageId = "message-id"),
        onClose = {
            // Navigate up or otherwise close the message view
        }
    )
}
```

Features include customizable theming and two-pane layout for tablets.

### Preference Center Compose


###### Kotlin
```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.urbanairship.preferencecenter.compose.ui.*

@Composable
fun MySettingsScreen() {
    PreferenceCenterScreen("my_preference_center")
}

// With custom theming
@Composable
fun MyThemedPreferenceCenter() {
    val options = PreferenceCenterOptions(
        showTitleItem = false,
        showSwitchIcons = true
    )

    val lightColors = PreferenceCenterColors.lightDefaults(
        accent = Color.Red
    )

    val darkColors = PreferenceCenterColors.darkDefaults(
        accent = Color.Blue
    )

    PreferenceCenterTheme(
        options = options,
        colors = if (isSystemInDarkTheme()) darkColors else lightColors
    ) {
        PreferenceCenterScreen("my_preference_center")
    }
}
```

---

## Troubleshooting

### Common Issues

**Build Errors After Migration**
- Ensure you're using Kotlin 2.2.0 or later.
- Update `compileSdk` to 36 and `minSdk` to 23 in your `build.gradle`.
- Clean your build folder (`./gradlew clean`) and rebuild.
- Check that all Airship dependencies use the same version (20.0.0).
- Review the "Breaking Changes" section for removed classes and methods.

**Compose Compiler Errors**
- Ensure you're using Kotlin 2.2.0 or later (the Compose compiler is bundled).
- Remove any explicit `kotlinCompilerExtensionVersion` settings from your `build.gradle`.

### Getting Help

If you encounter issues not covered in this guide:
- Check the [Airship Documentation](https://docs.airship.com/)
- Review the [SDK API Reference](https://docs.airship.com/reference/libraries/android/)
- Contact [Airship Support](https://support.airship.com/)
- File an issue on [GitHub](https://github.com/urbanairship/android-library/issues)
