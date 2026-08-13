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

Because `kotlin.time.Duration` is a Kotlin value class and cannot be expressed from Java, `Duration`-based members are accompanied by a Java-friendly counterpart wherever the type appears in public API. Where both exist, the `Duration` member is the source of truth and the other is derived from it.

#### Timestamps

| Before | After |
|---|---|
| `Message.sentDate: Date` | `Message.sentDate: Instant` |
| `Message.expirationDate: Date?` | `Message.expirationDate: Instant?` |
| `ApplicationListener.onForeground(Long)` / `onBackground(Long)` | `onForeground(Instant)` / `onBackground(Instant)` |
| `EmailRegistrationOptions.transactionalOptedIn: Long` | `transactionalOptedIn: Instant?` |
| `EmailRegistrationOptions.commercialOptedIn: Long` | `commercialOptedIn: Instant?` |
| `LegacyInAppMessage.expiryMs: Long?` | `expiry: Instant?` |

`EmailRegistrationOptions` previously used `-1` to mean "not set"; this is now represented as `null`.

`AttributeEditor` keeps its `Date` overloads and gains `Instant` ones alongside them, so `setAttribute(String, Date)` continues to work. The one exception is the JSON-payload overload: `setAttribute(String, String, Date?, JsonMap)` has been **removed**, because it and the `Instant?` overload both erase to `setAttribute(String, String, <reference>, JsonMap)`, making a `null` expiration ambiguous to resolve. Pass `date.toInstant()`, or `null`.

#### Durations

| Before | After | Java |
|---|---|---|
| `InAppMessagingInterface.displayInterval: Long` (seconds) | `displayInterval: Duration` | `displayIntervalSeconds: Long` |
| `Banner.durationMs: Long` | `Banner.duration: Duration` | `durationMs: Long` (read-only) |
| `Banner.DEFAULT_DURATION_MS: Long` | `Banner.DEFAULT_DURATION: Duration` | `DEFAULT_DURATION_MS: Long` |
| `LegacyInAppMessage.displayDurationMs: Long?` | `displayDuration: Duration?` | `displayDurationMs: Long?` (read-only) |
| `BannerPresentation.durationMs: Long?` | `BannerPresentation.duration: Duration?` | — |
| `AirshipConfigOptions.backgroundReportingIntervalMS: Long` | `backgroundReportingInterval: Duration` | `backgroundReportingIntervalMS: Long` |
| `Builder.setBackgroundReportingIntervalMS(Long)` | `setBackgroundReportingInterval(Duration)` | `setBackgroundReportingIntervalMS(Long)` |
| `PushProviderBridge...setMaxCallbackWaitTime(Long)` | `setMaxCallbackWaitTime(Duration)` | `setMaxCallbackWaitTime(Long)` |
| `Timer(duration: Long)` | `Timer(duration: Duration)` | — |

`displayInterval` deserves particular attention: the old `Long` was in **seconds**, which was not reflected in its name or documentation. Java callers should move from `setDisplayInterval(30)` to `setDisplayIntervalSeconds(30)`; Kotlin callers should use `displayInterval = 30.seconds`. The persisted value is still stored in seconds, so no migration occurs.

`Banner.copy()` takes a `Duration` in Kotlin. Java sees a `copy(...)` overload taking `long durationMs` in that position instead.

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

## Deprecated APIs

_To be documented as development proceeds._

## Troubleshooting

_To be documented as development proceeds._
