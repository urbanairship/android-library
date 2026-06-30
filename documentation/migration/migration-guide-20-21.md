# Airship Android SDK 20.x to 21.x Migration Guide

> **Note**
> This guide is a work in progress and will be updated as SDK 21.0 development continues.

This guide outlines the changes required when migrating your app from SDK 20.x to SDK 21.x.

## Breaking Changes

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
