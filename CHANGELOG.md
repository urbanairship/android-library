# Android 19.x ChangeLog

[Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration)

[All Releases](https://github.com/urbanairship/android-library/releases)

## Version 19.6.2 April 29, 2025
Patch release that fixes a crash in `PushManager.onTokenChanged`, introduced in release 19.6.0.
Apps should skip release 19.6.0 and 19.6.1 and update directly to this version, or later.

### Changes
- Fixed nullability of `oldToken` in `PushManager.onTokenChanged`.

## Version 19.6.1 April 28, 2025
Patch release that fixes a crash with NPS scores within a Scene that uses branching. Apps planning
on using the upcoming branching feature should update.

### Changes
- Fixed crash with a branching Scene with an NPS widget.

## Version 19.6.0 April 24, 2025
Minor release adding branching and SMS support for Scenes.

### Changes
- Added support for branching in Scenes.
- Added support for phone number collection and registration in Scenes.
- Added support for setting JSON attributes for Channels and Contacts.
- Added a new `mutationsFlow` to `AddTagsAction` and `RemoveTagsAction` to expose tag mutations when applied.
- Updated Message Center Inbox to refresh messages on app foreground.

## Version 19.5.1 April 17, 2025
Patch release with fix for regression in Contacts that could cause a failure to resolve a Contact ID when Contacts are disabled.

### Changes
- Fixed Contact ID resolution when contacts are disabled.

## Version 19.5.0 March 31, 2025
Minor release that adds a public method `Inbox.deleteAllMessages()` and remove restrictions for subclassing `MessageWebView` and `MessageWebViewClient`.

### Changes
- Added a new public method `Inbox.deleteAllMessages()` to delete all messages from Message Center.
- Removed library group restrictions on `MessageWebView` and `MessageWebViewClient`.


## Version 19.4.0 March 24, 2025
Minor release that adds support for Custom View in Scenes and fixes Privacy Manager issues when disabling all features.

### Changes
- Added Custom View support to enable showing App managed views within a Scene.
- Fixed an issue where the Privacy Manager sent multiple opt-out requests after features were disabled following being enabled.

## Version 19.3.0 March 6, 2025
Minor release that fixes an issue with modal Scenes and adds support for hoisting `AirshipEmbeddedViewGroup` composable state.
Apps that make use of Scenes should update to this version or greater.

### Changes
- Fix a potential crash when displaying a modal Scene
- Added support for hoisting `AirshipEmbeddedViewGroup` composable state

## Version 19.2.0 February 20, 2025
Minor release that includes improvements for Scenes and Feature Flags.

### Changes
- Added a fade transition for Scenes
- Added support for email registration in Scenes
- Fixed issues with autoplay videos in Scenes
- Improved image download and scaling logic
- Fixed an issue with image pre-caching when unable to successfully download an image
- Expose rule refresh status for Feature Flags

## Version 19.1.0 February 4, 2025
Minor release that fixes an issue with embedded view sizing in scrolling views, improves Message Center accessibility, and replaces usages of `Random` with `SecureRandom`.
Apps that make use of Embedded Content or Message Center should update.

### Changes
- Fixed an issue with embedded sizing in scrolling views
- Improved Message Center Accessibility
- Replaced usage of `Random` with `SecureRandom`
- Made `MessageWebView` and `MessageWebViewClient` both `public` and `open` for subclassing
- Exposed Message Center `ViewModel` state via `LiveData`, in addition to Kotlin `Flow`s
- Added `PendingResult` based methods to `Inbox`, for getting read and unread message counts and listing all message IDs

## Version 19.0.0 January 16, 2025
Major release that adds support for Android 15 (API 35) and updates Message Center and Preference Center to use Material 3.
Breaking changes in Message Center are included in this release. See the [Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration/migration-guide-18-19.md) for more info.

### Changes
- The Airship SDK now requires `compileSdk` version 35 (Android 15) or higher, and `minSdk` version 23 (Android 6) or higher.
- Migrated Message Center APIs to Kotlin, using asynchronous access patterns. New suspend functions and Flows have been added for Kotlin, and Java APIs have been updated to use `PendingResult` or callbacks.
- Rewrote the provided Message Center UI to follow modern Android UI conventions, use Material 3 theming, and support edge-to-edge mode for Android 15.
- Updated Preference Center to use Material 3 theming and support edge-to-edge mode for Android 15.
- Added `Feature.FEATURE_FLAGS` to `PrivacyManager` to control enablement of feature flags.
- Added support for wrapping score views in Scenes.
- Added support for Feature Flag experimentation.
