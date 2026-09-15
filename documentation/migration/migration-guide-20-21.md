# Airship Android SDK 20.x to 21.x Migration Guide

This guide outlines the changes required when migrating your app from SDK 20.x to SDK 21.x.

## Breaking changes

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
| `ContactChannel.Email.RegistrationInfo.Registered.commercialOptedIn: Long?`            | `: Instant?`                                        |
| `ContactChannel.Email.RegistrationInfo.Registered.commercialOptedOut: Long?`           | `: Instant?`                                        |
| `ContactChannel.Email.RegistrationInfo.Registered.transactionalOptedIn: Long?`         | `: Instant?`                                        |
| `ContactChannel.Email.RegistrationInfo.Registered.transactionalOptedOut: Long?`        | `: Instant?`                                        |
| `JsonMap.isoDateAsMilliseconds(String, Long?): Long?`                                  | `JsonMap.isoDateAsInstant(String, Instant?): Instant?` |

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

### Layout event types are renamed from `IN_APP_*` to `LAYOUT_*`

The event types that Scenes and other layout-backed experiences emit were named `IN_APP_*`, which read as though they were specific to in-app messages. They now use a `LAYOUT_*` prefix on both `EventType` (analytics) and `EventAutomationTriggerType` (automation triggers). The rename is identical on both, and the enclosing enum is otherwise unchanged:

| Before                     | After                      |
|----------------------------|----------------------------|
| `IN_APP_BUTTON_TAP`        | `LAYOUT_BUTTON_TAP`        |
| `IN_APP_FORM_DISPLAY`      | `LAYOUT_FORM_DISPLAY`      |
| `IN_APP_FORM_RESULT`       | `LAYOUT_FORM_RESULT`       |
| `IN_APP_GESTURE`           | `LAYOUT_GESTURE`           |
| `IN_APP_PAGE_ACTION`       | `LAYOUT_PAGE_ACTION`       |
| `IN_APP_PAGE_SWIPE`        | `LAYOUT_PAGE_SWIPE`        |
| `IN_APP_PAGE_VIEW`         | `LAYOUT_PAGE_VIEW`         |
| `IN_APP_PAGER_COMPLETED`   | `LAYOUT_PAGER_COMPLETED`   |
| `IN_APP_PAGER_SUMMARY`     | `LAYOUT_PAGER_SUMMARY`     |
| `IN_APP_PERMISSION_RESULT` | `LAYOUT_PERMISSION_RESULT` |

`IN_APP_DISPLAY` and `IN_APP_RESOLUTION` keep their names on both enums — those really are in-app message events.

This affects apps that read `AirshipEventData.type` from an analytics event listener, or that build an `EventAutomationTrigger` for one of these types. The reporting values sent to Airship are unchanged.

### `AutomationSchedule.editGracePeriodDays` is now `Long?`

`editGracePeriodDays` was typed `ULong?`, which is awkward from Java and inconsistent with the rest of the schedule API. It is now `Long?`, on both the property and `AutomationSchedule.Builder.setEditGracePeriodDays`.

### Removed: `PushManager` extension functions

The previously deprecated `PushManagerExtensions` file has been removed. Callers that explicitly referenced the extensions by name will need to update to use the replacement methods on directly on `PushManager`.

| Removed extension                                                 | Replacement                                                    |
|-------------------------------------------------------------------|----------------------------------------------------------------|
| `PushManager.pushNotificationStatusFlow` (extension)              | `PushManager.pushNotificationStatusFlow` (property)            |
| `PushManager.enableUserNotifications(promptFallback)` (extension) | `PushManager.enableUserNotifications(promptFallback)` (member) |

### `PushProvider.getRegistrationToken` is now suspend

`PushProvider` is a public extension point, so this affects any app that ships a custom provider (a custom ADM/FCM bridge, for example). Registration now runs in a coroutine rather than blocking the job thread, so the override must be marked `suspend`:

| Before                                                         | After                                                                  |
|----------------------------------------------------------------|------------------------------------------------------------------------|
| `override fun getRegistrationToken(context: Context): String?` | `override suspend fun getRegistrationToken(context: Context): String?` |

`PushManager.performPushRegistration` is now suspend as a result.

Because Kotlin `suspend` functions cannot be implemented from Java, a custom `PushProvider` written in Java must be converted to Kotlin.

### Consistent data class copy visibility

The SDK now uses the `-Xconsistent-data-class-copy-visibility` compiler flag, which makes the generated `copy()` methods on data classes inherit the same visibility as their primary constructors.

As a result of this change, the `copy()` methods on the following data classes are no longer `public`:

* `com.urbanairship.automation.AutomationSchedule.ScheduleData.Deferred`
* `com.urbanairship.automation.compose.EmbeddedViewItem`

### Embedded view selection and filtering

`AirshipEmbeddedView` and `AirshipEmbeddedViewGroup` (and their `remember*State` functions) now take an optional `filterInstances` between `selection` and the trailing composables. It decides *eligibility*; `selection` still decides order. Callers that passed the following arguments positionally need to name them, or move the argument over.

```kotlin
AirshipEmbeddedView(
    embeddedId = "home_banner",
    selection = AirshipEmbeddedSelection.Priority,
    filterInstances = { it.instanceId !in suppressedInstanceIds },
)
```

The view-system `AirshipEmbeddedView` gains the same thing, as a constructor parameter and as a settable property.

#### `AirshipEmbeddedSelection.ByInstanceId` takes a list

It now carries an ordered list of instance IDs rather than one, so `getInstanceId` is replaced by `getInstanceIds`. The single-ID constructor still works unchanged.

| Removed                      | Replacement                         |
|------------------------------|-------------------------------------|
| `ByInstanceId.getInstanceId` | `ByInstanceId.getInstanceIds`       |
| `ByInstanceId.component1()`  | `component1()` now returns the list |
| `ByInstanceId.copy(String)`  | `copy(List<String>)`                |

Two behavior changes come with it:

* **The earliest named instance that is pending wins**, rather than the only named one — the list is an order of preference.
* **It is an allow-list.** Pending content that isn't named is now excluded entirely rather than ordered after the target, so `AirshipEmbeddedViewGroup` renders only the named subset, and newly pending content will not display until your app includes it. Use `filterInstances` instead when the intent is to exclude specific instances rather than to enumerate the acceptable ones.

#### `AirshipEmbeddedInfo` now reports the real priority

`AirshipEmbeddedInfo.priority` was previously left at its `0` default everywhere the SDK built one, so it did not describe the instance it came with. Two places are affected:

* **A comparator passed to `AirshipEmbeddedSelection.ByComparator` compared `0` against `0`.** Sorting on `priority` was a no-op that left arrival order intact. Comparators that sort on `priority` will now actually reorder content.
* **`AirshipEmbeddedObserver` reported `0` for every instance**, so an observer filter testing `priority` matched everything. It now matches on the real value.

If your app relied on either of these being effectively inert, review it before upgrading.

`AirshipEmbeddedInfo` also gains `contentDescription`. Its constructor is now restricted — see [Internal APIs now marked `@RestrictTo`](#internal-apis-now-marked-restrictto).

### `@RestrictTo` changes

#### Internal APIs now marked `@RestrictTo`

These were public but are SDK plumbing, and are now `@RestrictTo(LIBRARY_GROUP)`:

* `com.urbanairship.android.layout.BannerPresentation`
* `com.urbanairship.android.layout.util.Timer`
* `com.urbanairship.util.CachedList`
* `com.urbanairship.messagecenter.User`, along with `MessageCenter.user` and `Inbox.user`, which exposed it
* `AirshipLayout.layoutInfo` (`LayoutInfo` was restricted already).
* `AirshipEmbeddedInfo`'s constructor.
* `FeatureFlag`'s deprecated 3-arg constructor, `FeatureFlag(Boolean, Boolean, JsonMap?)`. Obtain flags from `FeatureFlagManager.flag` or `FeatureFlagManager.flagAsPendingResult` instead.

The Message Center user (its ID and basic-auth password) is credential plumbing for our [out-of-the-box Message Center UI](https://www.airship.com/docs/developer/sdk-integration/android/message-center/getting-started/) and was never meant to be read or managed by app code directly. If you were using `messageCenter.user` to build a custom message list or detail screen, integrate the provided `MessageCenterFragment`/`MessageCenterActivity` (or the Compose equivalents) instead — see the getting-started guide linked above.

`@RestrictTo` now also covers nested types and overrides of already-restricted APIs. None of that is reachable from app code except `MessageWebViewClient`, when overriding `extendActionRequest` or `extendJavascriptEnvironment`. These are now considered internal with no public replacement.

Members that exposed internal types are no longer public:

* `Airship.runtimeConfig`
* Constructors for `LiveUpdateManager`, `AirshipWebViewClient(NativeBridge)`, `AttributeEditor` and `LandingPageAction`
* `Event`'s `Clock` constructors — `Event()` is unchanged
* `AutomationAudience`'s constructor, and `AutomationSchedule.Builder.setAudience` / `setCompoundAudience`
* The `SyncPrefKey` constants on `LocaleManager` and `PushManager`
* `MessageViewModel`'s `Inbox` constructor. The class stays public and its no-arg constructor is unchanged.

Except `LandingPageAction`, reaching any of these required an internal type, so app code that compiled without suppressing `RestrictedApi` is unaffected.

#### Public APIs no longer flagged as restricted:

A few APIs that were intended to be public carried overly restrictive `@RestrictTo(LIBRARY_GROUP)` annotations:

* `AirshipInputValidation.Request` and its nested types
* `ChannelType`, which `Contact.associateChannel`, `ContactChannel.channelType` and `ConflictEvent.ChannelInfo` all expose
* `InAppMessageColor`
* Custom view APIs:
  * `AirshipCustomViewManager.register` and `unregister`
  * `AirshipCustomViewHandler.onCreateView`
  * `AirshipCustomViewArguments`, along with its `properties`, `sizeInfo` and `sceneController` accessors
* `SceneController.Companion.empty()` has also been made public. It builds a no-op controller, which can be used in Compose `@Preview`s or tests:

```kotlin
AirshipCustomViewArguments(
    name = "preview",
    properties = jsonMapOf(),
    sizeInfo = AirshipCustomViewArguments.SizeInfo(true, true),
    sceneController = SceneController.empty()
)
```

Any `@Suppress("RestrictedApi")` or lint baseline entries your app added to work around the above previously being restricted can now be removed.

If you have a use case that relies on any of the newly restricted APIs, please open a GitHub issue to discuss it with us.

## Deprecated APIs

* `AirshipConfigOptions.backgroundReportingIntervalMS` — use `backgroundReportingIntervalMs`.
* `AirshipConfigOptions.Builder.setBackgroundReportingIntervalMS(Long)` — use `setBackgroundReportingIntervalMs(Long)`.

## Troubleshooting

### Common issues

**Build errors after migration**
- Update `minSdk` to 26 and `compileSdk` to 36 in your `build.gradle`.
- SDK 21 is built with Kotlin `2.2.20` and JDK 17 — make sure your project is on a compatible toolchain.
- Check that all `com.urbanairship.android` dependencies use the same version (`21.0.0`), then `./gradlew clean` and rebuild.

**Call requires API level 26**
- The SDK now uses `java.time`, which requires API 26.
- Raising `minSdk` to 26 is the only supported fix.

**Type mismatch: `Long` or `Date` found, `Instant` required**
- Convert with `Instant.ofEpochMilli(millis)` or `date.toInstant()`, and back with `instant.toEpochMilli()` or `Date.from(instant)`.

**Unresolved reference: `pushNotificationStatusFlow` or `enableUserNotifications`**
- `PushManagerExtensions` has been removed. Delete the `com.urbanairship.push` imports for both — they are now members on `PushManager` and resolve without an import.

**Cannot access `copy()` on an Airship data class**
- `copy()` now matches the visibility of its primary constructor, so `AutomationSchedule.ScheduleData.Deferred` and `EmbeddedViewItem` no longer expose it. Build a new instance through the public constructor or builder instead.

### Getting help

If you encounter issues not covered in this guide:
- Check the [Airship Documentation](https://docs.airship.com/)
- Review the [SDK API Reference](https://docs.airship.com/reference/libraries/android/)
- Contact [Airship Support](https://support.airship.com/)
- File an issue on [GitHub](https://github.com/urbanairship/android-library/issues)
