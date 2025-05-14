# Android 18.x ChangeLog

[Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration)
[All Releases](https://github.com/urbanairship/android-library/releases)

## Version 18.7.1 February 25, 2025
Patch release to fix a casting exception with Embedded Content.

### Changes
- Fixed exception due to a bad cast when using Embedded Content.

## Version 18.7.0 February 6, 2025
Minor release that updates AndroidX libraries. A `compileSdk` of 35+ is required.

### Changes
- Updated several AndroidX dependencies
- Updated to Kotlin 2.x

## Version 18.6.0 December 19, 2024
Minor release that updates how Feature Flags are resolved, improves Scene rendering on Android 15,
and fixes potential exceptions related to PermissionsManager and PermissionDelegates.
 
### Changes
- Added `resultCache` to `FeatureFlagManager`. This cache is managed by the app and can be optionally used when resolving a flag as a fallback if the flag fails to resolve or if
  the flag rule set does not exist.
- FeatureFlag resolution will now resolve a rule set even if the listing is out of date.
- Improved Scene rendering on Android 15, for scenes that do not ignore safe areas.
- Prevent potential "Already resumed" exceptions that could be caused by a permission delegate calling the callback multiple times.
- Improved constraint version matching

## Version 18.5.0 December 5, 2024
Minor release that includes various improvements to scenes, data management and some minor bug fixes.

### Changes
- Added support to mark a label as a heading in Scenes.
- Improved live update database handling to mitigate rare filesystem crashes.
- Improved automation store to avoid query limits.

## Version 18.4.2 November 26, 2024
Patch release that fixes an issue with Embedded Views being impacted by certain App theme customizations, avoids a potential NPE related to network failures, and adds more useful logging around Feature Flag evaluation.

### Changes
- Prevent App-level theme customizations from impacting Embedded Views
- Avoid a potential NPE related to network failures, when no error body is present
- Improved logging around Feature Flag evaluation

## Version 18.4.1 November 15, 2024
Patch release that fixes an issue with pausing and resuming In-App Automations and avoids a potential crash in the Automation database.

### Changes
- Fixed an issue with `AutomationEngine.setEnginePaused(...)` that could prevent message displays when paused an then un-paused
- Fixed a potential crash in Automation DB if 1000+ rows are present in the schedules table

## Version 18.4.0 November 1, 2024
Minor release with several enhancements to Scenes and In-App Automations.

### Changes
- Added shadow support for modal Scenes
- Added new Scene layout to allow adding actions to anything within a Scene
- Added new `AirshipEmbeddedViewGroup` composable to make it possible to show a carousel of embedded views for the same embedded ID
- Improved accessibility of scene story indicator. Indicator has been updated to make it obvious which page is active by reducing the height of the inactive pages. Previously this was conveyed only through color
- improved accessibility for In-App Automation views
- Fixed issue with FCM registration if the FCM application is not configured before Airship starts, causing launch notifications to be ignored

## Version 18.3.3, October 16, 2024
Patch release that fixes a potential crash when displaying In-App Automation messages, improves WebView security, and improves accessibility in Scenes and Stories. 
Apps that make use of In-App Automation, Landing Pages, or Message Center should update.

### Changes
- Fix a potential crash when displaying In-App messages
- Explicitly disallow file and content access in all WebViews
- Accessibility improvements for Scenes and Stories

## Version 18.3.2, October 2, 2024
Patch release that improves markdown support in Scenes and fixes for automation display interval and frequency limit handling.
Apps that make use of markdown in Scenes, or automations with display intervals or frequency limits should update.

### Changes
- Improve markdown support in Scenes, including better handling of newlines in the input text.
- Fixed automation display interval and frequency limit handling.

## Version 18.3.1, September 30, 2024
Patch release that fixes modal IAA border radius and fixes scenes with wide images.

### Changes
- Fixed modal IAA border radius.
- Fixed scenes with wide images.

## Version 18.3.0, September 13, 2024
Minor release that adds a new method `enableUserNotifications(PermissionPromptFallback)` on `PushManager`.

### Changes
- Added a `enableUserNotifications(PermissionPromptFallback)` method on `PushManager` that will attempt to enable notifications and use the fallback if the permission is denied.

## Version 18.2.0, September 6, 2024
Minor release with several enhancements to In-App Automation, Scenes, and Surveys. This version also contains a fix
for applications that are targeting API 35.

### Changes
- Updated compose bom to 2024.06.00.
- Replaced the usage of `removeFirst` to avoid crashes when targeting API 35.
- Added ability to customize the content per In-App Automation with the new `InAppMessageContentExtender`.
- Added plain markdown support for text markup in Scenes.
- Added execution window support to In-App Automation, Scenes, and Surveys.
- Updated handling of priority for In-App Automation, Scenes, and Surveys. Priority is now taken into consideration at each step of displaying a message instead of just sorting messages that are
triggered at the same time.
- Updated handling of long delays for In-App Automation, Scenes, and Surveys. Delays will now be preprocessed up to 30 seconds before it ends before the message is prepared.

## Version 18.1.6, August 9, 2024
Patch release that fixes in-app experience displays when resuming from a paused state. Apps that use in-app experiences are encouraged to update.

### Changes
- Fixed Automation Engine updates when pause state changes.

## Version 18.1.5, August 06, 2024
Patch release that fixes test devices audience check and holdout group experiments displays.

### Changes
- Fixed test devices audience check.
- Fixed holdout group experiment displays.

## Version 18.1.4, July 31, 2024
Patch release that includes bug fixes for Embedded Content.

### Changes
- Fixed an issue with dismissing Embedded Content after pausing and resuming the app.
- Updated the default `PreferenceCenterFragment` to scope the `PreferenceCenterViewModel` to the fragment's view lifecycle.

## Version 18.1.3, July 30, 2024
Patch release that includes bug fixes for Embedded Content and Preference Center, and accessibility improvements for Message Center. 

### Changes
- Fixed an issue with container child item measurement in Scenes, when margins were set on the container items.
- Fixed a Preference Center bug that could lead to subscription channel chips not being visible when initially displaying a Preference Center.
- Fixed dismissing multiple embedded views in the same session.
- Fixed an issue with automation trigger state not correctly persisting across sessions.
- Message Center accessibility improvements.
- Updated the default style for the pull to dismiss view in In-App Message Banners to better match iOS.

## Version 18.1.2, July 15, 2024
Patch release that includes fixes for Preference Center.

### Changes
- Fixed warning message on preference center email entry field.
- Fixed country code listing.

## Version 18.1.1, June 28, 2024
Patch release that includes fixes for Preference Center, Privacy Manager, and Embedded Content.

### Changes
- Fixed a Preference Center issue that caused contact subscription toggles to show the incorrect state after being toggled
- Fixed test dependency being included in the automation module
- Fixed Embedded Content impression event interval
- Fixed privacy manager crash when enabling, disabling, or setting an empty set of features
- Contact channel listing is now refreshed on foreground and from a background push

## Version 18.1.0, June 20, 2024
Minor SDK release that fixes a potential crash related to analytics during app init and adds public
builders for modifying `InAppMessage` and `AutomationSchedule` objects via extenders set on`LegacyInAppMessaging`.

### Changes
- Fixed a potential crash related to analytics during app init
- Added builders for modifying `InAppMessage` and `AutomationSchedule` objects via extenders set on `LegacyInAppMessaging`

## Version 18.0.0, June 14, 2024
Major SDK release with several breaking changes. 
See the [Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration/migration-guide-17-18.md) for more info.

### Changes
- The Airship SDK now requires `compileSdk` version 34 (Android 14) or higher.
- New Automation module
  - Check schedule’s start date before executing, to better handle updates to the scheduled start date
  - Improved image loading for In-App messages, Scenes, and Surveys
  - Reset GIF animations on visibility change in Scenes and Surveys
  - Pause Story progress while videos are loading
  - Concurrent automation processing to reduce latency if more than one automation is triggered at the same time
  - Embedded Scenes & Survey support
  - New module `urbanairship-automation-compose` to support embedding a Scene & Survey in compose
  - Added new compound triggers and IAX event triggers
  - Ban lists support
- Added new `PrivacyManager.Feature.FEATURE_FLAGS` to control access to feature flags
- Added support for multiple deferred feature flag resolution
- Added contact management support in preference centers
- Migrated to non-transitive R classes
- Removed `urbanairship-ads-identifier` and `urbanairship-preference` modules

## Version 18.0.0-alpha, May 3, 2024
Initial alpha release of SDK 18.0.0. This version is not suitable for a production app, but we encourage testing out the new APIs and providing us feedback so we can make changes before the final SDK 18 release.

The Airship SDK now requires `compileSdk` version 34 (Android 14) or higher.

### Changes
- Improved image loading for In-App messages, Scenes, and Surveys
- Reset GIF animations on visibility change in Scenes and Surveys
- Pause Story progress while videos are loading
- Migrated to non-transitive R classes
- Check schedule’s start date before executing, to better handle updates to the scheduled start date
- Removed `urbanairship-ads-identifier` and `urbanairship-preference` modules

See the [Migration Guide](https://github.com/urbanairship/android-library/tree/main/documentation/migration/migration-guide-17-18.md) for further details.
