# Android 19.x ChangeLog

[Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration)

[All Releases](https://github.com/urbanairship/android-library/releases)

## Version 19.13.8 - January 28, 2026
Patch release that fixes an issue with custom events being double counted for IAX triggers (reporting was not affected). 
Apps that make use of custom event triggers in IAX should update to this version or later.

### Changes
- Fixed issue that caused custom events being double counted for IAX triggers (reporting was not affected)

## Version 19.13.7 - January 16, 2026
Patch release that fixes a potential image-related crash in Scenes.

### Changes
- Fixes a potential crash in Scenes with specific images and display settings.

## Version 19.13.6 - November 14, 2025
Patch release that fixes YouTube video playback in In-App Automation and Scenes. Applications that use YouTube videos in Scenes and non-html In-App Automations (IAA) must update to resolve playback errors.

### Changes
- Fixed YouTube video embedding to comply with YouTube API Client identification requirements.

## Version 19.13.5 - October 13, 2025
Patch release that handles BigDecimal in our JSON parsing. This prevents parse exceptions if the default Android org.json package is replaced by the org.json maven package. 

### Changes
- Handle BigDecimal and other number values when parsing JSON from a string.

## Version 19.13.4 - October 6, 2025
Patch release that addresses an issue with handling Play Services errors before `takeOff` and fixes a Scene pager transition bug.

### Changes
- Updated `PlayServiceErrorActivity` to handle play services errors before `takeOff` is called.
- Fixed Scene pager issue where tapping the scene during a transition from one page to another would interrupt the transition.

## Version 19.13.3 - September 26, 2025
Patch release that fixes an issue with handling `uairship://close` in Message Center and improves Scene accessibility.

### Changes
- Fixed handling of `uairship://close` links in Message Center
- Improved accessibility for Scene pager indicators
- Improved `DeferredResult` logging
 
## Version 19.13.2 - September 17, 2025
Patch release that adds more logs to the deferred schedules preparing process.

### Changes
- Added more logs to the deferred schedules preparing process.

## Version 19.13.1 - September 15, 2025
Patch release to fix an issue with showing out of date In-App Automations and Scenes.

### Changes
- Fixed refreshing out of date In-App Automations and Scenes before displaying.

## Version 19.13.0 - September 5, 2025
Minor release that adds support for handling `uairship://message_center/message/<message_id>` links to open a specific message in Message Center.

### Changes
- Added support for handling `uairship://message_center/message/<message_id>` links to Message Center

## Version 19.12.0 - September 4, 2025
Minor release that adds a new flag to HTML In-App message content to force full screen on all devices.

### Changes
- Added `forceFullScreenDisplay` to HTML In-App message content
- Improved accessibility in Scenes by removing labels from being focusable when using keyboard navigation

## Version 19.11.0 - August 21, 2025
Minor release that enforces that incoming pushes are for the current channel ID and adds a manifest 
metadata entry to control handling of insets for IAM banners for edge-to-edge mode.

### Changes
- Added Activity metadata entry (`com.urbanairship.iam.banner.BANNER_INSET_EDGE_TO_EDGE`) to force handling of insets for IAM banners in edge-to-edge mode.
- Channel ID is now enforced for incoming pushes, ensuring that only pushes for the current channel ID are processed.

## Version 19.10.2 - August 13, 2025
Patch release that fixes embedded display reporting and a potential crash in Scenes. 
Apps that use Scenes or Embedded Content should update to this version or later.

### Changes
- Fixed an issue that could cause embedded content displays to be reported too early.
- Fixed a potential crash that can occur when a Scene is dismissed.

## Version 19.10.1 - August 1, 2025
A patch release that fixes an automation dao crash if an expected nonnull JSON field contains invalid JSON.

### Changes
- Fixed potential automation dao crash when an expected nonnull JSON field contains invalid JSON.

## Version 19.10.0 - July 24, 2025
A minor release with accessibility and layout improvements to Scenes, a key performance update, and several bug fixes.

### Changes
- Added support in Scenes for linking form inputs to a label for better accessibility.
- Added container item alignment to Scenes to change the natural alignment within a container.
- Updated the initial remote-data request (IAX, Config, Feature Flags, etc...) to bypass work manager to improve performance.
- Fixed setting content-descriptions on a text/number/email input in Scenes to provide better accessibility.
- Fixed potential automation dao crash when migrating from an older SDK version.
- Fixed an issue where dismissing a Scene with a back gesture could prevent it from displaying again in the same session.

## Version 19.9.2 - July 11, 2025
Patch release with several fixes and accessibility improvements for Scenes. 

### Changes
- Fixed a crash when dismissing an in-app automation view.
- Fixed multiple page views being recorded for pages in branching Scenes.
- Fixed a bug in Message Center Message WebView that could potentially interfere with JS in other web views.
- Accessibility fixes and improvements for Scenes.

## Version 19.9.1 - June 24, 2025
Patch release that enhances logging, fixes a potential memory leak in paging Scenes, and improves accessibility.

### Changes
- Fixed potential memory leak in paging Scenes by improving accessibility listener lifecycle management.
- Added 'logPrivacyLevel' to the config to improve managing logging visibility.
- Added accessibility dismissal announcement for in-app messages.

## Version 19.9.0 - June 17, 2025
A minor update with enhancements to the Scenes and Message Center functionality and bug fixes for Analytics and Automation. This version is required for Scene branching and phone number collection.

### Changes
Analytics:
- Fixed bug that could cause locale-based descrepancies in reports.

Automation:
- Fixed version trigger predicate matching to properly evaluate app version conditions.

Message Center:
- Automatically retries failed message list refreshes for improved reliability.
- Expired messages will no longer trigger a network request to refresh the listing.

Scenes:
- Fixed layout issues with modal frames, specifically related to margins and borders.
- Fixed border rendering issues when stroke thickness exceeds corner radius.
- Fixed several issues related to Scene branching.
- Added support for custom corner radii on borders.
- Added support for more flexible survey toggles.

## Version 19.8.0 - May 23, 2025
Minor release focused on performance improvements for Scenes.

### Changes
- Improved load times for Scenes by prefetching assets concurrently.

## Version 19.7.0 - May 15, 2025
Minor release that adds support for using Feature Flags as an audience condition for other Feature Flags and Vimeo videos in Scenes.

### Changes
- Added support for using Feature Flags as an audience condition for other Feature Flags.
- Added support for Vimeo videos in Scenes.
- Fixed minor issue with SMS collection in Scenes where the button loading indicator does not clear if submission encounters an error.

## Version 19.6.2 - April 29, 2025
Patch release that fixes a crash in `PushManager.onTokenChanged`, introduced in release 19.6.0.
Apps should skip release 19.6.0 and 19.6.1 and update directly to this version, or later.

### Changes
- Fixed nullability of `oldToken` in `PushManager.onTokenChanged`.

## Version 19.6.1 - April 28, 2025
Patch release that fixes a crash with NPS scores within a Scene that uses branching. Apps planning
on using the upcoming branching feature should update.

### Changes
- Fixed crash with a branching Scene with an NPS widget.

## Version 19.6.0 - April 24, 2025
Minor release adding branching and SMS support for Scenes.

### Changes
- Added support for branching in Scenes.
- Added support for phone number collection and registration in Scenes.
- Added support for setting JSON attributes for Channels and Contacts.
- Added a new `mutationsFlow` to `AddTagsAction` and `RemoveTagsAction` to expose tag mutations when applied.
- Updated Message Center Inbox to refresh messages on app foreground.

## Version 19.5.1 - April 17, 2025
Patch release with fix for regression in Contacts that could cause a failure to resolve a Contact ID when Contacts are disabled.

### Changes
- Fixed Contact ID resolution when contacts are disabled.

## Version 19.5.0 - March 31, 2025
Minor release that adds a public method `Inbox.deleteAllMessages()` and remove restrictions for subclassing `MessageWebView` and `MessageWebViewClient`.

### Changes
- Added a new public method `Inbox.deleteAllMessages()` to delete all messages from Message Center.
- Removed library group restrictions on `MessageWebView` and `MessageWebViewClient`.


## Version 19.4.0 - March 24, 2025
Minor release that adds support for Custom View in Scenes and fixes Privacy Manager issues when disabling all features.

### Changes
- Added Custom View support to enable showing App managed views within a Scene.
- Fixed an issue where the Privacy Manager sent multiple opt-out requests after features were disabled following being enabled.

## Version 19.3.0 - March 6, 2025
Minor release that fixes an issue with modal Scenes and adds support for hoisting `AirshipEmbeddedViewGroup` composable state.
Apps that make use of Scenes should update to this version or greater.

### Changes
- Fix a potential crash when displaying a modal Scene
- Added support for hoisting `AirshipEmbeddedViewGroup` composable state

## Version 19.2.0 - February 20, 2025
Minor release that includes improvements for Scenes and Feature Flags.

### Changes
- Added a fade transition for Scenes
- Added support for email registration in Scenes
- Fixed issues with autoplay videos in Scenes
- Improved image download and scaling logic
- Fixed an issue with image pre-caching when unable to successfully download an image
- Expose rule refresh status for Feature Flags

## Version 19.1.0 - February 4, 2025
Minor release that fixes an issue with embedded view sizing in scrolling views, improves Message Center accessibility, and replaces usages of `Random` with `SecureRandom`.
Apps that make use of Embedded Content or Message Center should update.

### Changes
- Fixed an issue with embedded sizing in scrolling views
- Improved Message Center Accessibility
- Replaced usage of `Random` with `SecureRandom`
- Made `MessageWebView` and `MessageWebViewClient` both `public` and `open` for subclassing
- Exposed Message Center `ViewModel` state via `LiveData`, in addition to Kotlin `Flow`s
- Added `PendingResult` based methods to `Inbox`, for getting read and unread message counts and listing all message IDs

## Version 19.0.0 - January 16, 2025
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
