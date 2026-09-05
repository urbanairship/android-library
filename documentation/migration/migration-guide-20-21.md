# Airship Android SDK 20.x to 21.x Migration Guide

> **Note**
> This guide is a work in progress and will be updated as SDK 21.0 development continues.

This guide outlines the changes required when migrating your app from SDK 20.x to SDK 21.x.

## Breaking Changes

### Minimum SDK is now 26 (Android 8.0)

`minSdkVersion` has been raised from 23 to 26. The SDK now uses `java.time` (`java.time.Instant`), which requires API 26; core library desugaring is not enabled.

Update your app's `minSdk` accordingly:

```groovy
android {
    defaultConfig {
        minSdk 26
    }
}
```

### Timestamps are now `Instant`, and durations are now `Duration`

Timestamps previously typed as `Long` (epoch milliseconds) or `java.util.Date` are now `java.time.Instant`, and time intervals previously typed as `Long` are now `kotlin.time.Duration`.

**JSON and wire formats are unchanged**, as are persisted values and database columns — this is a type-level change only.

Because `Duration` is a Kotlin value class and cannot be expressed from Java, every `Duration`-typed member of the public API is accompanied by a Java-friendly counterpart.

#### Timestamps

| Before                                                                                 | After                                               |
|----------------------------------------------------------------------------------------|-----------------------------------------------------|
| `Message.sentDate: Date`                                                               | `Message.sentDate: Instant`                         |
| `Message.expirationDate: Date?`                                                        | `Message.expirationDate: Instant?`                  |
| `ApplicationListener.onForeground(Long)` / `onBackground(Long)`                        | `onForeground(Instant)` / `onBackground(Instant)`   |
| `EmailRegistrationOptions.transactionalOptedIn: Long`                                  | `transactionalOptedIn: Instant?`                    |
| `EmailRegistrationOptions.commercialOptedIn: Long`                                     | `commercialOptedIn: Instant?`                       |
| `EmailRegistrationOptions.commercialOptions(Date?, Date?, JsonMap?)`                   | `commercialOptions(Instant?, Instant?, JsonMap?)`   |
| `EmailRegistrationOptions.options(Date?, JsonMap?, Boolean)`                           | `options(Instant?, JsonMap?, Boolean)`              |
| `LegacyInAppMessage.expiryMs: Long?`                                                   | `expiry: Instant?`                                  |
| `AirshipEventData.timeMs: Long`                                                        | `AirshipEventData.timestamp: Instant`               |
| `AutomationSchedule.startDate` / `endDate: ULong?`                                     | `startDate` / `endDate: Instant?`                   |
| `AutomationSchedule.Builder.setStartDate(Long?)` / `setEndDate(Long?)`                 | `setStartDate(Instant?)` / `setEndDate(Instant?)`   |
| `LiveUpdate.lastContentUpdateTime` / `lastStateChangeTime: Long`                       | `: Instant`                                         |
| `LiveUpdate.dismissalTime: Long?`                                                      | `: Instant?`                                        |
| `LiveUpdateManager.start` / `update` / `end(timestamp: Long, dismissTimestamp: Long?)` | `(timestamp: Instant, dismissTimestamp: Instant?)`  |
| `ScopedSubscriptionListMutation.newSubscribeMutation(…, timestamp: Long)`              | `(…, timestamp: Instant)`                           |
| `ScopedSubscriptionListMutation.newUnsubscribeMutation(…, timestamp: Long)`            | `(…, timestamp: Instant)`                           |
| `AttributeEditor.setAttribute(String, String, Date?, JsonMap)`                         | `setAttribute(String, String, Instant?, JsonMap)`   |

`EmailRegistrationOptions` previously used `-1` to mean "not set"; this is now `null`. Its factory methods, `commercialOptions` and `options`, now take `Instant?` instead of `Date?`. Callers holding a `Date` should pass `date.toInstant()`.

#### Durations

| Before                                                     | After                                      | Java                                                                                           |
|------------------------------------------------------------|--------------------------------------------|------------------------------------------------------------------------------------------------|
| `InAppMessagingInterface.displayInterval: Long` (seconds)  | `displayInterval: Duration`                | `displayIntervalSeconds: Long`                                                                 |
| `Banner.durationMs: Long`                                  | `Banner.duration: Duration`                | `durationMs: Long` (read-only)                                                                 |
| `Banner.DEFAULT_DURATION_MS: Long`                         | `Banner.DEFAULT_DURATION: Duration`        | `DEFAULT_DURATION_MS: Long`                                                                    |
| `LegacyInAppMessage.displayDurationMs: Long?`              | `displayDuration: Duration?`               | `displayDurationMs: Long?` (read-only)                                                         |
| `AirshipConfigOptions.backgroundReportingIntervalMS: Long` | `backgroundReportingInterval: Duration`    | `backgroundReportingIntervalMs: Long` (renamed casing; old `MS` name kept as `@Deprecated`)    |
| `Builder.setBackgroundReportingIntervalMS(Long)`           | `setBackgroundReportingInterval(Duration)` | `setBackgroundReportingIntervalMs(Long)` (renamed casing; old `MS` name kept as `@Deprecated`) |
| `PushProviderBridge...setMaxCallbackWaitTime(Long)`        | `setMaxCallbackWaitTime(Duration)`         | `setMaxCallbackWaitTimeMs(Long)`                                                               |
| `AutomationSchedule.interval: ULong?` (seconds)            | `interval: Duration?`                      | `intervalSeconds: Long?` (read-only)                                                           |
| `AutomationSchedule.Builder.setInterval(Long?)` (seconds)  | `setInterval(Duration?)`                   | `setIntervalSeconds(Long?)`                                                                    |

#### No longer public

These were public but are SDK plumbing, and are now `@RestrictTo(LIBRARY_GROUP)` rather than gaining `Duration` counterparts nothing would call:

* `com.urbanairship.android.layout.BannerPresentation`
* `com.urbanairship.android.layout.util.Timer`
* `com.urbanairship.util.CachedList`

### Removed: `PushManager` extension functions

The previously deprecated `PushManagerExtensions` file has been removed. Callers that explicitly referenced the extensions by name will need to update to use the replacement methods on directly on `PushManager`.

| Removed extension                                                 | Replacement                                                    |
|-------------------------------------------------------------------|----------------------------------------------------------------|
| `PushManager.pushNotificationStatusFlow` (extension)              | `PushManager.pushNotificationStatusFlow` (property)            |
| `PushManager.enableUserNotifications(promptFallback)` (extension) | `PushManager.enableUserNotifications(promptFallback)` (member) |

### Consistent data class copy visibility

The SDK now uses the `-Xconsistent-data-class-copy-visibility` compiler flag, which makes the generated `copy()` methods on data classes inherit the same visibility as their primary constructors.

As a result of this change, the `copy()` methods on the following data classes are no longer `public`:

* `com.urbanairship.automation.AutomationSchedule.ScheduleData.Deferred`
* `com.urbanairship.automation.compose.EmbeddedViewItem`

### Internal APIs now marked `@RestrictTo`

`AirshipLayout.layoutInfo` is now `@RestrictTo(LIBRARY_GROUP)` to better align with `LayoutInfo`, which was already restricted and not intended to be used by consumers of the SDK.

## Other Changes

### The custom view API is no longer flagged as restricted

`com.urbanairship.android.layout` carried an overly restrictive package-level `@RestrictTo(LIBRARY_GROUP)` that applied to several APIs intended to be public:

* `AirshipCustomViewManager.register` and `unregister`
* `AirshipCustomViewHandler.onCreateView`
* `AirshipCustomViewArguments`, along with its `properties`, `sizeInfo` and `sceneController` accessors

Any `@Suppress("RestrictedApi")` or lint baseline entries your app added to work around this can be removed.

`SceneController.Companion.empty()` has also been made public. It builds a no-op controller, which makes it possible to construct `AirshipCustomViewArguments` in a Compose `@Preview` or a unit test:

```kotlin
AirshipCustomViewArguments(
    name = "preview",
    properties = jsonMapOf(),
    sizeInfo = AirshipCustomViewArguments.SizeInfo(true, true),
    sceneController = SceneController.empty()
)
```

## Deprecated APIs

* `AirshipConfigOptions.backgroundReportingIntervalMS` — use `backgroundReportingIntervalMs`.
* `AirshipConfigOptions.Builder.setBackgroundReportingIntervalMS(Long)` — use `setBackgroundReportingIntervalMs(Long)`.

## Troubleshooting

_To be documented as development proceeds._
