# Android 20.x ChangeLog

[Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration)

[All Releases](https://github.com/urbanairship/android-library/releases)

## Version 20.10.0 - July 22, 2026

Minor release that adds version name support to app version triggers and audience checks, along with fixes for pager button touch handling and media view video playback.

### Changes
- Added version name alongside version code to the app version trigger predicate and device audience version check
- Fixed pager button taps triggering an unintended double page advance and the story progress bar freezing when tapping previous on the first page
- Fixed media view videos getting stuck in an endless reload loop and collapsing to zero size when both dimensions were unset
- Fixed background videos in Scenes suppressing tap-to-advance pager gestures
- Various other bug fixes and refactoring

## Version 20.9.0 - July 9, 2026

Minor release that adds a new `AirshipEmbeddedSelection` for controlling which content is displayed in embedded views and includes a couple of Scene rendering improvements.

### Changes
- Add `AirshipEmbeddedSelection` for controlling which content is displayed in embedded views
- Improved Scene rendering for a couple of edge cases involving horizontal/vertical containers
- Removed orientation locking for Scenes on tablets and other large-screen devices (`sw >= 600dp`), due to inconsistent support across Android versions and manufacturers

## Version 20.8.2 - June 30, 2026

Patch release that improves the reliability of Live Update registrations and enhances reporting when launching Scenes from push notifications using the new Scene Action.
Apps that make use of Live Updates or the new Scene Action should update to this version or newer.

### Changes
- Fixed a potential race condition where Live Update mutations could be orphaned if channel registration ran before the mutation was committed to storage
- Scenes and landing pages triggered via `SceneAction` and `LandingPageAction` now emit v2 analytics event IDs with the correct source, campaigns, and send metadata.

## Version 20.8.1 - June 22, 2026

Patch release that fixes a couple of Scene issues. Apps should update to this version or newer if using buttons in Scenes that launch a deep link and dismiss the Scene, or if using Scenes launched from push notifications.

### Changes
- Fixed a regression in Scene buttons that caused deep link actions to not always trigger when combined with dismiss behavior
- Fixed image caching for Scenes launched from push notifications via `SceneAction`

## Version 20.8.0 - June 16, 2026

Minor release that adds support for launching Scenes from push notifications using the new Scene action and a fix for getter methods in the native bridge.
Apps that make use of custom JavaScript integrations with the native bridge should update to this version or newer.

### Changes
- Added support for the new `SceneAction`, which allows launching Scenes from push notifications
- Fixed getter methods in the native bridge
- Improved Scene Pager rendering when branching updates the available next pages

## Version 20.7.4 - June 8, 2026

Patch release that fixes an edge case where Live Updates created in rapid succession could get stuck on START, and fixes screenview triggers incorrectly re-firing when the app returns to the foreground.

### Changes

- Fixed a timing issue where a pending channel update (including a Live Update registration) added within ~300ms of another could be dropped if it arrived while a channel update upload was already in flight
- Fixed screenview triggers re-firing when the app is brought to the foreground, which could cause In-App Automation rules with screenview triggers to activate unexpectedly

## Version 20.7.3 - May 29, 2026

Patch release that fixed two potential crashes. Apps upgrading from older SDK major versions (prior to 20.x) should update to this version or newer.

### Changes

- Fixed a potential crash in `AutomationStoreMigrator` that could occur when migrating old automation schedule data
- Harden the analytics event screen (from `urbanairship-debug`) against crashes if event types that are no longer supported are encountered

## Version 20.7.2 - May 13, 2026

Patch release that fixes a couple of Scene issues and improves remote data refresh behavior for kiosk-style apps. Apps that make use of SMS inputs in Scenes should update to this version or newer.

### Changes

- Fixed SMS text input in Scenes
- Updated Scene label and label button to improve rendering consistency
- Improved remote data refresh behavior for kiosk-style apps that stay foregrounded for long periods of time

## Version 20.7.1 - May 7, 2026

Patch release that includes minor API changes to allow for email and SMS registration in Airship cross-platform frameworks.

### Changes

- Internal API changes to support email and SMS registration in frameworks.

## Version 20.7.0 - April 30, 2026

Minor release that adds support for Native Message Center.

### Changes

- Added support for rendering Native Content in Message Center.

## Version 20.6.4 - April 24, 2026

Patch release that fixes keyboard resize handling in modal scenes.

### Changes

- Fixed modal scenes so content resizes correctly when the soft keyboard appears, closing a gap between content and the keyboard on older API levels

## Version 20.6.3 - April 20, 2026

Patch release with several push reliability improvements.

### Changes

- Fixed a race condition in `PushManager` that could cause false push opt-outs when FCM tokens rotate or registration fails transiently
- Fixed `SQLiteBlobTooBigException` errors in `PreferenceDataStore` for large stored values
- Fixed invalid JSON logging
- Fixed unnecessary backoff when Airship's WorkManager jobs are canceled externally

## Version 20.6.2 - April 14, 2026

Patch release that hardens against a specific WebView crash that can occur on certain Android 16 devices.

### Changes

- Avoid crashing if WebView inflation fails in `HtmlActivity`, which displays Custom HTML IAX messages, when we encounter a known issue on Android 16 that primarly impacts Samsung devices (https://issuetracker.google.com/issues/448359671)

## Version 20.6.1 - March 27, 2026

Patch release that fixes a dependency resolution issue with the FCM module introduced in 20.4.0. Apps that depend on `urbanairship-fcm` and reference Firebase Messaging classes directly should update to this version.

### Changes
- Fixed `firebase-messaging` dependency not being available on the compile classpath

## Version 20.6.0 - March 25, 2026

Minor release that extends Markdown support in Scenes and improves handling of navigation to invalid Message Center message IDs.

### Changes
- Added superscript and subscript Markdown support in Scenes (`^^superscript^^` and `,{subscript},`)
- Updated Message Center to show the message view with an error when attempting to open a message with an invalid message ID, instead of failing to the Messages list

## Version 20.5.0 - March 17, 2026

Minor release that improves video playback and pager navigation reliability in Scenes, along with several bug fixes. This release also includes updates to proguard rules to support behavior changes in AGP 9. Apps that have migrated to AGP 9.x should update to this version or newer.

### Changes

- Improved video playback lifecycle handling in Scenes
- Improvements for Scenes with complex branching
- Fixed `Airship.takeOff` returning before `onReady` callbacks have completed
- Fixed possible hang when calling `fetchMessages` on Message Center
- Fixed Message Center inbox update notifications
- Fixed Message Center message content type parsing
- Fixed SMS validation error handling
- Fixed overly frequent permission listener callbacks
- Updated proguard rules to keep default constructors for Airship classes

## Version 20.4.0 - March 3, 2026

Minor release with a pair of improvements for Scenes.

### Changes

- Adjusted Markdown rendering in Scenes to be less aggressive when interpreting styling delimiters inside of words
- Improved Scene border rendering when rounded corners are present

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