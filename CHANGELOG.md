# Android 20.x ChangeLog

[Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration)

[All Releases](https://github.com/urbanairship/android-library/releases)

## Version 20.3.0 - February 24, 2026

Minor release that adds support for Native Message Center. Native content type requires displaying the message content in an Airship Message View. Apps that do not use Airship's message views (e.g. using a WebView directly) should filter out messages where `message.contentType` is not `Message.ContentType.Html`.

### Changes

- Removed library group restrictions on  `PushProviderBridge`.
- Added support for Native Message Center.


## Version 20.2.2 - February 19, 2026

Patch release with an FCM availability check improvement to better handle unexpected Google Play service lookup failures.

### Changes

- Added exception handling and logging around the FCM Google Play Store availability check to prevent unexpected crashes when Google checks fail.

## Version 20.2.1 - February 6, 2026

Patch release with several minor improvements for the Compose Message Center UI. Apps that make use of the Compose Message Center should update to take advantage of these improvements.

### Changes

- Allows the Compose Message Center toolbar title to be overridden via `MessageCenterOptions`
- Option to disable message deletion in the Compose Message Center via `MessageCenterOptions`
- Fixed the up arrow and `onNavigateUp` callback for the Compose Message Center list screen

## Version 20.2.0 - January 28, 2026

Minor release that adds a new `PreferenceCenterView`, fixes fetching subscription lists after changing contact IDs, and improvements for Scenes.

### Changes
- Added `PreferenceCenterView` for easier integration of Preference Centers when the `Fragment` API is not desired
- Fixed issue where subscription lists could remain cached after changing contact IDs
- Improved measurement of videos inside of containers in Scenes
- Improved checkbox and radio button accessibility in Scenes
- Improved TalkBack navigation for Scenes with Pagers
- Fixed issue that caused custom events being double counted for IAX triggers (reporting was not affected)

## Version 20.1.1 - January 16, 2026

Patch release that fixes a potential image-related crash in Scenes and acessibility issues.

### Changes
- Fixed a potential crash in Scenes with specific images and display settings
- Fixed Message Center title not being marked as a heading
- Fixed Scene icon buttons not having a proper disabled effect

## Version 20.1.0 - January 9, 2026

Minor release that includes several fixes and improvements for Scenes, In-App Automations, and notification handling.

### Changes
- Fixed a measurement issue with videos inside of containers in Scenes in certain configurations
- Fixed a potential crash in `NotificationProxyActivity`
- In-app automations and Scenes that were not available during app launch can now be triggered by events that happened in the previous 30 seconds
- Added support for additional text styles in Scenes
- Added highlight markdown support in Scenes (`==highlighted text==`)
- Fixed incrementing frequency limits before a message is ready to display
- Improved support for WebViews in Scenes
- Added support for Story pause/resume and back/next controls

## Version 20.0.7 - December 30, 2025

Patch release with improvements to notification processing timing that resolves a crash when app is opened from a notification on Android 15.

### Changes
- Fixed notification processing timing for Android 15 compatibility

## Version 20.0.6 - December 15, 2025

Patch release to fix a regression in `NotificationIntentProcessor` that interfered with handling of `PendingIntent`s set on custom built notifications.

### Changes
- Fixed issue with custom notification handling of `PendingIntent`s in `NotificationIntentProcessor`

## Version 20.0.5 - December 5, 2025

Patch release that fixes an issue with opening the Compose Message Center.

### Changes
- Fixed opening of Compose MessageCenterActivity
- Merged PushManagerExtensions into PushManager

## Version 20.0.4 - November 25, 2025

Patch release that fixes a potential race condition when setting metadata and creating action arguments concurrently. Apps experiencing crashes when processing push notifications should update to resolve this issue.

### Changes
- Fixed potential `ConcurrentModificationException` in `ActionRunRequest` when metadata is modified concurrently with action execution, most likely occurring when processing incoming push notifications ([#258](https://github.com/urbanairship/android-library/issues/258)).


## Version 20.0.3 - November 14, 2025

Patch release that fixes YouTube video playback in In-App Automation and Scenes and minor fixes for the Preference Center Compose module. Applications that use YouTube videos in Scenes and non-html In-App Automations (IAA) must update to resolve playback errors.

### Changes
- Fixed YouTube video embedding to comply with YouTube API Client identification requirements.
- Allow multiple Preference Centers to be displayed with Preference Center Compose.
- Fixed checked/unchecked icon assets for Preference Center Compose.
- Updated Preference Center Compose default toolbar to allow `navIcon` to be `null`.

## Version 20.0.2 - November 4, 2025

Patch release that fixes prompting for permissions on foreground.

### Changes
- Fixed prompting for permissions on foreground.
- Removed usage of material icons compose library.
- Updated Message Center titles to be marked as headings.

## Version 20.0.1 - October 23, 2025

Patch release that fixes packaging and publishing for the modules added in 20.0.0. Apps upgrading to
SDK 20.x should update directly to 20.0.1 to ensure proper packaging of these modules.

### Changes

- Fixed publishing for:
  - `urbanairship-message-center-core` 
  - `urbanairship-message-center-compose`
  - `urbanairship-preference-center-core`
  - `urbanairship-preference-center-compose`
  - `urbanairship-debug`

## Version 20.0.0 – October 23, 2025

Major SDK release with several breaking changes. See the [Migration Guide](https://github.com/urbanairship/android-library/tree/main/documentation/migration/migration-guide-19-20.md) for detailed instructions on upgrading.

### Changes
- compileSdkVersion updated to 36
- Kotlin updated to 2.2.0
- The `UAirship` singleton has been deprecated and replaced with `Airship`
    - `Airship` is no longer a shared instance; instead, it exposes static methods for accessing components
- Majority of the SDK has been migrated to Kotlin
- Message Center package changes:
  - `message-center-core`: Core API with no UI
  - `message-center`: Android XML layouts (depends on `message-center-core`)
  - `message-center-compose`: New Jetpack Compose UI (depends on `message-center-core`)
- Preference Center package changes:
  - `preference-center-core`: Core API with no UI
  - `preference-center`: Android XML layouts (depends on `preference-center-core`)
  - `preference-center-compose`: New Jetpack Compose UI (depends on `preference-center-core`)
- New AirshipDebug package that exposes insights and debugging capabilities into the Airship SDK for development builds, providing enhanced visibility into SDK behavior and performance.
- Removed automatic collection of `connection_type` and `carrier` device properties