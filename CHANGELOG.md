# Android ChangeLog

[Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration)

## Version 16.8.0 November 2, 2022
Minor release that adds support for custom Airship domains.

### Changes
- Adds support for setting the initialConfigUrl when using custom domains.

## Version 16.7.5 October 4, 2022

Patch release that fixes an issue with the HMS push provider, improves WebView Safe Browsing for supported devices, and avoids Strict Mode warnings related to clipboard operations.

### Changes
- Ignore calls to `processNewToken` on HMS, if the new token is identical to one we've received previously.
- Wait for start Safe Browsing callback before loading URLs in WebViews, to improve security.
- Move clipboard copy operations for actions and Channel capture to a background thread.

## Version 16.7.4 September 20, 2022

Patch release that prevents a potential crash on WebView with uairship commands and on Message Center database at initialization. Also clears push token on FCM registration failure. 

## Version 16.7.3 September 9, 2022

Patch release that fixes a caching issue with named contact subscription lists when edits are made. Applications using contact based preference centers or accessing contact subscription lists should update.

## Version 16.7.2 September 2, 2022

Patch release that fixes a Message Center data migration and prevents any exceptions with failed migrations going forward.

### Changes
- Fix crashes due to Message Center database migrations.

## Version 16.7.1 August 12, 2022

Patch release that prevents potential crashes when downloading files.

### Changes
- Fix potential crashes during file downloads.

## Version 16.7.0 July 29, 2022

Minor release that adds support for sending a new `isActive` attribute on channel registration updates, improves accessibility descriptions for Preference Center subscription list items, and improves initialization of the Airship SDK for apps that make use of dependency-injection frameworks.

### Changes
- Channel registration will now send up `isActive` when updating registration in the foreground, for better MAU tracking.
- Added the ability to initialize the Airship SDK without any external dependencies, to better support apps that initialize Jetpack WorkManager via dependency-injection frameworks.
- Improved accessibility descriptions for Preference Center subscription list items.
- Location integration with Airship can be replaced with setting a location permission delegate on `PermissionsManager`.
- Fixed subscription list action.

## Version 16.6.1 June 30, 2022

Patch release that fixes issues with ADM push provider and a bad room migration when updating the SDK to a version before SDK 15.1 to SDK 16.5.1. Apps using SDK 15.0.0 and lower should update directly to SDK 16.6.1 or newer.

### Changes
- Added consumer proguard rules to prevent ADM crashes when using ADM push provider.
- Fixed ADM crash on older ADM devices.
- Fixed Message Center invalid schema exceptions.

## Version 16.6.0 June 21, 2022

Minor release that adds support for Android 13 (API 33) and fixes a Preference Center issue that could occur under poor network conditions.

### Changes
- Adds support for the new notification permissions prompt in Android 13 for apps that target API 33, with fallback prompt support on older API levels.
- Fixed Preference Center to always display the correct toggle states when navigating away and back to Preference Center under poor network conditions.

## Version 16.5.1 June 7, 2022

Patch release that fixes an issue that could potentially lead to duplicate messages in the Message Center Inbox table.

### Changes
- Prevent duplicate messages in the Inbox table and clean up duplicates, if any are present.

## Version 16.5.0 May 17, 2022
Minor release that fixes ADM registration on Windows 11 Android subsystem.

### Changes
- Updated the ADM plugin to the version 1.1.0
- Fixed using restricted App Compat APIs in Scenes & Surveys

## Version 16.4.0 May 4, 2022
Minor release that adds support for randomizing response order in a Survey, adds a new delegate method to InAppMessageManager that controls when a message can be displayed, and fixes several issues with Scenes & Surveys reporting. Apps using Scenes & Surveys should update.

### Changes
- Added new `InAppMessageManager.setDisplayDelegate` method that can control when a message is able to be displayed.
- Added support for randomizing Survey responses.
- Added subscription list action.
- In-App rules will now attempt to refresh before displaying. This change should reduce the chances of showing out of data or cancelled in-app automations, scenes, or surveys when background refresh is disabled.
- Updated localizations. All strings within the SDK are now localized in 48 different languages.
- Improved accessibility with OOTB Message Center UI.
- Fixed reporting issue with a single page Scene.
- Fixed rendering issues for Scenes & Surveys.
- Fixed a crash in Scenes on Android 8.
- Fixed Survey attribute storage.

## Version 16.3.3 March 4, 2022

A patch release that fixes potential crashes in the urbanairship-automation module when
displaying certain Scenes and Surveys on older API levels.

### Changes
- Fixed potential crashes in Scenes and Surveys on Android 6 and below.

## Version 16.3.2 March 3, 2022

A patch release that fixes a potential crash with the splash screen Jetpack library when using banner in-app messages.

## Changes
- Remove check for container view on Activity#onCreate for banner in-app messages

## Version 16.3.1 February 17, 2022

A minor release that fixes Preference Center and Automation issues.

## Changes
- Fixed PreferenceCenter theme attribute for chip style when embedding the Fragment directly.
- Fixed a PreferenceCenter crash that impacted Android 6 and below.
- Fixed a potential crash in Scenes and Surveys on Android 6 and below.

## Version 16.3.0 February 8, 2022

A minor release that adds support for multi-channel Preference Center. Currently, these features are only available to customers in Airship's Special Access Program. Please reach out to your account manager for more details.

## Changes
- Added support for multi-channel Preference Center.
- Added scoped subscription lists to contacts.
- Added methods to associate email, SMS, and open channels to a contact.

## Version 16.2.0 January 24, 2021

A minor release that adds support for two new features, Scenes and Surveys. Currently, these features are only available to customers in Airship's Special Access Program. Please reach out to your account manager for more details.

This version also includes a fix for a subset of devices sending duplicate events, and to reduce the number of retries when the device has an internet connection but Airship traffic is blocked on the network. Devices running SDK 15+ should update.

## Changes
- Added support for Scenes and Surveys
- Fixed devices sending duplicate events
- Fixed In-App Automation session trigger skipping sessions when automations are paused then resumed
- Reduce number of retries when Airship traffic is blocked on the network
- Fixed a crash on Android 6 and below

## Version 16.1.1 January 4, 2022

Patch release that fixes Message Center crashes and issues.

### Changes
- Fixed marking messages as read or deleting messages in Message Center.
- Fixed a crash when enabling Message Center feature.
- Fixed a crash when deleting 1000+ messages in Message Center.

## Version 16.1.0 November 15, 2021

Minor release that adds a new chat action and has some minor
fixes for Accengage, Chat, and the Preference center module.

### Changes
- Added send chat action
- Fixed showing the preference center loading indicator if the content is already loaded
- Updated the kotlin-coroutine-core dependency on chat to kotlin-coroutine-android
- Fixed treating a push with both Accengage and Airship keys as Accengage instead of Airship.

## Version 16.0.0 October 18, 2021

Major release that adds support for targeting Android 12 (API 31), upgrades SDK dependencies, and
updates the minimum required compile SDK version to API 31. No other breaking API changes are
included in this release.

### Changes
- Updated compile and target version to 31
- Updated Java source and target compatibility versions to 1.8
- Updated SDK dependencies

See the [Migration Guide](https://github.com/urbanairship/android-library/tree/main/documentation/migration/migration-guide-15-16.md) for further details.


## Version 15.1.0 October 1, 2021

Minor release that adds support for specifying `route_agent` values and passing single
messages to prepopulate into Airship Chat via deep link.

### Changes
- Added support for handling `route_agent` and `prepopulated_message` params on Airship Chat deep links.
- Updated `14.5` migration guide and `PrivacyManager` javadoc to clarify usage.
- Updated `14.x - 15.x` migration guide with instructions for enabling `FEATURE_CONTACTS`.


## Version 15.0.0 September 14, 2021

Major release that adds support for Airship Preference Center, Subscription Lists, and Contacts. This
release removes support for overriding Firebase sender ID to a non-default Firebase project. Firebase
currently recommends using a single Firebase project for both Crashlytics and Cloud Messaging.

### Changes
- Added new module `AirshipPreferenceCenter`.
- Added new subscription lists APIs for Channel.
- Replaced `NamedUser` with `Contact`, which allows setting data on a user without an external ID (Named User ID).
- Added Dokka for generation of Kotlin documentation.

See the [Migration Guide](https://github.com/urbanairship/android-library/tree/main/documentation/migration/migration-guide-14-15.md) for further details.


## Version 14.6.0 August 3, 2021

Minor release that adds Airship Chat routing support.

### Changes
- Added support for specifying a routing string in Airship Chat for directing messages to a particular agent.
- Added a `try/catch` around network callback register and unregister in `NetworkMonitor`, to prevent crashes on a small subset of devices.


## Version 14.5.1 June 21, 2021

Patch release that updates the version of `firebase-messaging` used by `urbanairship-fcm` and adds
a dependency on `firebase-iid` to maintain support for overriding `fcmSenderId` to a non-default
Firebase project. This configuration is no longer recommended by Firebase and may not be supported
in future versions of the Airship FCM module. Firebase currently recommends using a single Firebase
project for Crashlytics and Cloud Messaging.

### Changes
- Updated `firebase-messaging` to version `22.0.0`.
- Added `firebase-iid` dependency to `urbanairship-fcm`.
- Added a warning log message if `fcmSenderId` is being overridden to a non-default Firebase project.
- Marked `fcmSenderId` and related setter methods in `AirshipConfigOptions` as `@Deprecated`.


## Version 14.5.0 June 4, 2021

Minor release changing how the SDK handles data collection by introducing the privacy manager. Privacy manager allows fine-grained control over what data is allowed to be collected or accessed by the Airship SDK.

### Changes
- Added privacy manager
- Deprecated existing data collection flags

See the [Migration Guide](https://github.com/urbanairship/android-library/tree/main/documentation/migration/migration-guide-14.5.md) and the [Data Collection docs](https://docs.airship.com/platform/android/data-collection/) for further details.


## Version 14.4.4 May 26, 2021
Patch release with updates to support targeting the Android S preview SDK.

### Changes
- Explicitly declare `exported` for all `<intent-filter>` declarations in manifest files.
- Set explicit mutability flags on all uses of `PendingIntent`.
- Removed databinding in `urbanairship-chat`.

## Version 14.4.3 May 19, 2021
Patch release that fixes auto scrolling the live chat message list in the `ChatFragment` when a message is received and
removes some of the `urbanairship-chat` dependencies to make the module easier to generate Xamarin
bindings.

### Changes
- Drop `*-ktk` jetpack dependencies and `kotlin-serialization` from urbanairship-chat module.
- `ChatFragment` now auto scrolling the list when a message is received.


## Version 14.4.2 May 13, 2021
Patch release to fix styling of IAA banner buttons when using a Material theme.

### Changes
- Fix IAA banner button custom styling when using a Material theme.

## Version 14.4.1 May 7, 2021
Patch release to fix full screen In-App Automation reporting events when a user taps a button.

We are no longer able to deploy to JCenter. Updates will only be available through Maven Central
going forward.

### Changes
- Fixed IAA full screen reporting events reporting as dismissed instead of button click
- Remove JCenter deploys

## Version 14.4.0 April 26, 2021
Minor release that adds support for Airship Live Chat and drops support for Android KitKat and Jelly Bean.

### Changes
- Added new `urbanairship-chat` module.
- Updated `minSdkLevel` to API 21.
- Added `isAccengageVisiblePush()` and updated `isAccengagePush()` to support checking if an Accengage push is a message with content or a silent push.
- Updated Accengage module to fall back to Airship accent color and notification icon.

## Version 14.3.0 March 11, 2021
Minor release that drops support for uploading historic location data to Airship. The location
module can still be used to listen for location updates within the app and will be deprecated in
a future release.

### Changes
- AirshipLocationManager will no longer upload lat/longs to Airship.
- Removed max border radius validation to allow more than 20dps for in-app automations.

## Version 14.2.0 February 18, 2021
Minor release that uses WorkManager for more reliable task management and better support for Instant Apps.

### Changes
- Replaced JobScheduler with WorkManager.
- Added tag editor methods to the NativeBridge.
- Added ability to set NamedUser through the NativeBridge.

## Version 14.1.3 February 12, 2021
Patch release fixing a crash related to deferred IAA schedule logging. This issue only affects log levels debug and below, which by default are not enabled in production.

### Changes
 - Fixed misformatted log message in IAA
 - Additional safety in LoggingCore

## Version 14.1.2 February 4, 2021
Patch release improving SDK stability and to fix FCM config overrides.

### Changes
 - Fixed not using FCM sender ID override in Airship Config
 - Fixed SQLException crashes in the Automation module.
 - Fixed SecurityException when accessing the TelephonyManager.
 - Added error log if the URL Allow list is unmodified for SCOPE_OPEN.

## Version 14.1.1 December 30, 2020
Patch release to fix In-App Automations not displaying in the same session when the message is triggered on an activity that has been excluded from showing the IAA by using `com.urbanairship.push.iam.EXCLUDE_FROM_AUTO_SHOW` metadata entry in the `AndroidManifest.xml`.

## Version 14.1.0 December 16, 2020
Minor release adding support for frequency limits and advanced segmentation to In-App Automation, as well as new custom event templates.

### Changes
- Added frequency limits support to IAA
- Added support for advanced IAA segmentation
- Added a new search event template
- Added wishlist options to retail event template
- Added sms:, tel:, and mailto: to default allow list settings
- IAA messages no longer redisplay if interrupted due to app termination

## Version 14.0.3 - November 30, 2020
Patch release that fixes crashes related to In-App Automation SQL exceptions if the database is unable to be accessed due to low storage or permission issues.

## Version 14.0.2 - October 29, 2020
Patch release optimizing named user tag group cache usage in In-App Automation.

### Changes
- IAA tag group cache is now cleared when a named user is associated or disassociated

## Version 14.0.1 - September 23, 2020
Patch release to fix an exception caused by too many alarms being scheduled for in-app automation message intervals on Samsung devices. Applications seeing crashes related to alarms should update.

## Version 14.0.0 - September 3, 2020
Airship SDK 14 is a major update that prepares our automation module to support future IAA enhancements, revamps the Channel Capture tool, and provides other improvements.

The majority of apps will only be effected by the new `UrlAllowList` behavior changes.

## Changes
- **BEHAVIOR CHANGE** All URLs are not verified by default. Applications that use open URL action, landing pages, and custom in-app message image URLs will need to provide a list of URL patterns that match those URLs for SCOPE_OPEN_URL. The easist way to go back to 13.x behavior is to add `urlAllowListScopeOpenURL = *` to the AirshipConfig.
- Channel Capture tool now detects a `knock` of 6 app opens in 30 seconds. Instead of displaying anything to the user, the tool will write the current channel ID to the clipboard.
- Whitelist class and terminology removed and replaced with UrlAllowList.
- ActionAutomation and InAppMessageManager have been combined into InAppAutomation.
- InAppAutomation APIs have been updated to support future IAA enhancements. See migration guide for details.
- Removed deprecated APIs.
- Added sourcesJar maven artifact to each module, this allows browsing source code and java docs directly from Android Studio.

## Version 13.3.5 - August 29, 2020
Patch release to fix Android version equality checks in IAA and to fix a crash with the LocaleChangeReceiver if the Airship SDK is included in the Application but not initialized.

## Version 13.3.4 - August 27, 2020
Patch release to fix ANRs when opening a background notification button with an action. Applications that use background buttons should update.

## Version 13.3.3 - August 24, 2020
Patch release to fix SQL exceptions during SDK init. Applications that are seeing any SQL crashes from Airship should update.

## Version 13.3.2 - July 28, 2020
Patch release to fix In-App Automation version triggers to only fire on app updates instead of new installs.

## Version 13.3.1 - July 18, 2020
Patch release to fix ADM registration exceptions that occur on first run and text alignment issues with In-App Automation. Any apps that are configured to run ADM and are running 13.0.0 - 13.3.0 should update.

### Changes
- Fixed In-App Automation text alignment.
- Fixed ADM registration crash.

## Version 13.3.0 - July 16, 2020
Minor release that allows overriding the locale used by Airship.

### Changes
- Added locale override.
- Fixed IllegalStateException in NotificationProxyActivity.

## Version 13.2.2 - July 10, 2020
Patch release to make MessageWebViewClient methods `addAuthRequestCredentials` and
`removeAuthRequestCredentials` public instead of package-private.

## Version 13.2.1 - June 24, 2020
Patch release to fix In-App automation display intervals being ignored if the app is killed and HMS token registration on older Huawei devices.

### Changes
- Fixed display interval not being respected if app is killed.
- Fixed HMS on older Huawei devices.
- Removed debug logs from Autopilot.

## Version 13.2.0 - June 16, 2020
Minor release to handle `target="_blank"` URLs in Message Center and HTML In-App messages. This release also adds `extendedBroadcastsEnabled` that once enabled, will broadcast the app key and channel for easier partner integrations.

### Changes
- Fixed possible background ANRs when processing location updates.
- Fixed SecurityException when checking if location updates are enabled.
- Open URLs in an external browser if the target is set.
- Added extendedBroadcastsEnabled config flag to broadcast when Airship is ready with the channel and app key, and when the channel is created.

## Version 13.1.2 - May 26, 2020
Patch release to improve window inset handling for In-App message banners.

### Changes
- Use standard window insets for banners instead of root insets.

## Version 13.1.1 - May 21, 2020
Patch release to fix banner In-App messages displaying behind translucent navigation and status
bars.

### Changes
- Fixed window insets on banners

## Version 13.1.0 - May 4, 2020
Minor release that adds Named User attributes.

### Changes
- Added Named User attributes.
- Fixed NPE when retrying a failed video URL in IAA after the view has been detached from the window.
- Fixed logging an error message on registration failure due to using Huawei PushKit auto init feature on first run. The error was misleading as the registration will retry immediately after Push Kit is initialized.

## Version 13.0.0 - April 20, 2020

Major release that adds support for enhanced custom events, date attributes, HMS push provider, and breaks
the `urbanairship-core` module into several modules: `urbanairship-core`, `urbanairship-message-center`, `urbanairship-automation`, and
`urbananairship-location`. This allows apps to pull in only the feature modules they use. Most of the changes in
this release reflect the restructuring that makes this possible.

### Changes
- Break out feature modules from `urbanairship-core`:
  - `urbanairship-message-center`: Message Center
  - `urbanairship-automation`: InApp Automation/Messaging, Landing Page Action, and Action Automation
  - `urbananairship-location`: Airship Location
- Updated CustomEvents to allow arbitrary JSON as properties.
- Added module `urbanairship-hms` that adds support for Huawei Mobile Services (HMS).
- Added date attribute support.
- Removed usage of deprecated AsyncTask.
- Removed deprecated APIs.

## Version 12.2.4 - April 14, 2020
Patch release to fix a `TransactionTooLargeException` inside the Airship Job manager.

### Changes
- Fixed `TransactionTooLargeException` exception.

## Version 12.2.3 - March 30, 2020
Patch release to fix missing whitelist entries for EUCS cloud site and to fix a crash in event manager.

### Changes
- Fixed ArithmeticException in EventManager due to a race condition of disabling analytics at the same time as an event upload.
- Added Airship EUCS URLs to whitelist.

## Version 12.2.2 - March 10, 2020
Patch release to fix a whitelisting issue that prevented youtube and video URLs from working
properly in In-App Automation.

## Version 12.2.1 - March 6, 2020
Patch release improving SDK stability.

### Changes
- Fixed NullPointerException crash in AlarmOperationScheduler
- Fixed ClassCastException crash in JobScheduler
- Fixed SQLExceptions crashes in In-App Automation

Apps with any of these issues, and apps using In-App automation are encouraged
to update.

## Version 12.2.0 - January 30, 2020
Minor release that adds support for number attributes, new data collection flags,
and an Accengage transition module to facilitate Accengage customers upgrading to Airship.

### Changes
- Added support for number attributes.
- Added `AirshipConfigOptions#dataCollectionOptInEnabled` and `UAirship#setDataCollectionEnabled(boolean)`
  to make it easier to control Airship data collection.
- Added `urbanairship-accengage` module. This module migrates a device's attributes and ID to Airship,
  and allows devices to receive push notifications from Accengage during the upgrade period.
- Added option to automatically install Google's secure network provider before any Airship request
  by adding metadata to the manifest with key `com.urbanairship.INSTALL_NETWORK_SECURITY_PROVIDER`.
  The network installer is required for KitKat and older devices to work with the EUCS cloud site.

## Version 12.1.1 - January 16, 2020
Patch release to fix an issue with the same In-App Automation banner displaying multiple times on an activity
if the activity goes through a resume/pause/resume lifecycle state without a stop. This could occur
when starting an activity with NEW_TASK and CLEAR_TOP flags if the banner is already displaying. Apps
that are experiencing this issue with In-App Automation should update.

### Changes
- Fixed In-App Automation banner from displaying multiple times on a single activity in certain situations.

## Version 12.1.0 - December 18, 2019
Minor release that adds an accessor the In-App Automation name.

### Changes
- Added name accessor for In-App Automations.
- Fixed a crash when specifying duplicate locale audience conditions for an In-App Automation.
- Fixed application starting a new activity instead of resuming the current activity when launching from a notification.

## Version 10.1.4 - December 13, 2019
Fixes stability issues with in-app automation.

### Changes
- Fixed a crash when specifying duplicate locale audience conditions for an In-App Automation.

## Version 9.7.3 - December 13, 2019
Fixes stability issues with in-app automation.

### Changes
- Fixed a crash when specifying duplicate locale audience conditions for an In-App Automation.

## Version 12.0.0 - November 15, 2019
Major release decoupling Airship channel registration from push functionality,
and adding support for channel attributes, which allow key value pairs to be
associated with the application's Airship channel for segmentation purposes.

Custom channel attributes are currently a beta feature. If you wish to
participate in the beta program, please complete our [signup form](https://www.airship.com/lp/sign-up-now-to-participate-in-the-advanced-segmentation-beta-program/).

### Changes
- Added a new `AirshipChannel` class
- Channel related functionality in PushManager is now deprecated
- Added a new `AirshipChannel.AttributeEditor` class
- Added a new `editAttributes` method to `AirshipChannel`
- Android compile and target SDK versions are now 29

## Version 11.0.5 - September 16, 2019
Patch release to fix an issue with custom event attribution when adding a custom event through a Message
Center message. Apps that add events in a Message Center message should update.

## Version 10.1.3 - September 16, 2019
Patch release to fix an issue with custom event attribution when adding a custom event through a Message
Center message. Apps that add events in a Message Center message should update.

## Version 11.0.4 - August 30, 2019
Patch release to fix an issue with reporting the wrong push ID in the NotificationListener. Applications
that rely on the notification ID should update.

## Version 10.1.2 - August 30, 2019
Patch release to fix an issue with reporting the wrong push ID in the NotificationListener. Applications
that rely on the notification ID should update.

## Version 9.7.2 - August 30, 2019
Fixes an issue where push message could result in duplicate push notifications. Applications that are
running 9.4.0 - 9.7.1 should update.

### Changes
- Fixes issue with double processing messages on older devices.

## Version 11.0.3 - August 9, 2019
Patch release to fix direct open reporting for notifications when an activity is resumed from
the background. Applications running 11.x should update.

### Changes
- Fixed direct open attributions for push notifications.

## Version 10.1.1 - August 9, 2019
Patch release to fix direct open reporting for notifications when an activity is resumed from
the background. Applications running 10.x should update.

### Changes
- Fixed direct open attributions for push notifications.

## Version 11.0.2 - August 1, 2019
Patch release to fix an issue with not starting a custom activity with the intent filter action
`com.urbanairship.VIEW_RICH_PUSH_MESSAGE` for custom MessageCenter implementations and a fix for
a minor fullscreen In-App Automation style issue. Apps that have custom message center implementations
should update.

### Changes
- Fixed not starting an activity for the intent action `com.urbanairship.VIEW_RICH_PUSH_MESSAGE`.
- Fixed top padding on the fullscreen In-App Automation when the header is the top most element.

## Version 10.1.0 - August 1, 2019
Minor release that backports changes and fixes from 11.0.2 release.

### Changes
- Updated banner in-app message adapter to make it easier to customize the banner view.
- Fixed not starting an activity for the intent action `com.urbanairship.VIEW_RICH_PUSH_MESSAGE`.
- Fixed top padding on the fullscreen In-App Automation when the header is the top most element.
- Synchronize use of SimpleDateFormat instance across threads.

## Version 11.0.1 - July 17, 2019
Patch release to fix a rare crash caused by accessing a SimpleDateFormat across multiple threads.

### Changes
- Synchronize use of SimpleDateFormat instance across threads.

## Version 11.0.0 - July 11, 2019
Major release that migrates from the Android Support Libraries to the Jetpack (AndroidX) Libraries.
Applications are required to migrate to Android X before using this version. For more info, see
[Migrating to AndroidX](https://developer.android.com/jetpack/androidx/migrate)

### Changes
- Migrated to AndroidX.
- Updated banner in-app message adapter to make it easier to customize the banner view.
- Allow defining notification channel `sound` with a raw resource ID through XML instead of a URL.


## Version 10.0.2 - June 26, 2019
Patch release to fix issues with the banner in-app message type and background location
updates on Android O+.

### Changes

- Fixed banner in-app messages not dismissing when clicking the banner body.
- Fixed banner in-app messages not auto dismissing.
- Fixed background location updates on Android O+.

## Version 10.0.1 - June 4, 2019
Patch release fixing a minor regression in AirshipNotifiationProvider
when overriding small icon resources via the push API.

### Changes

- AirshipNotificationProvider defaults to the small icon specified in
  the PushMessage, if available.
- AirshipNotificationProvider uses getters instead of instance variables
  when building its default NotificationArguments.

## Version 10.0.0 - May 22, 2019
Major release that addresses new background restrictions with Android Q,
includes enhancements to In-App Automation, and adds notification channel
compatibility to simplify notification channel settings across Android versions.

[9.x to 10.x Migration Guide](https://github.com/urbanairship/android-library/tree/main/documentation/migration/migration-guide-9-10.md)

### Changes

### Packages
- Removed `urbanairship-sdk` and `urbanairship-gcm`. Apps should use `urbanairship-fcm`
  and/or `urbanairship-adm` instead.
- Preferences (com.urbanairhsip.preferences) have been moved into their own
  module `urbanairship-preferences`. The new
  preferences use the preference support library instead of the deprecated system preferences.
- Advertising ID tracking has been moved into its own module `urbanairship-ads-identifier`.
- Updated to Firebase Messaging 18.0.

### Notifications
- NotificationFactory has been deprecated and replaced with a more flexible
  NotificationProvider interface.
- AirshipReceiver has been removed and replaced with 3 new listeners on the PushManager
  class: NotificationListener, PushListener, and RegistrationListener.
- The notification's push message is now available in the launched activity intent.
- Added notification channel compat that works on Android 16 (Jellybean) and newer devices.

### In-App Automation
- Added support for localized messages.
- Button resolution events can be generated from HTML messages via the native bridge.
- Display coordinator architecture for more flexible custom display management.
- There is a new, app-extendable, mechanism for caching the message's assets.
- Banner messages now use a view group rather than a fragment, as system fragments were
  deprecated in Android P.
- Landing Pages are now scheduled as an HTML IAA.

### Other
- Updated APIs for better kotlin interop support.
- Apps can now provide their own image loader.
- Added `DeepLinkListener` to make it easier to customize deep link handling.
- Removed styleable attribute `urbanAirshipFontPath`. Applications should use the
  Android xml font support instead.
- Builder factory methods and from JSON methods have been normalized throughout the SDK.
- Added `OnShowMessageCenterListener` that can be set on the Message Center to make it
  easier to perform custom message center actions.

### Bug fixes
- Fixed incorrect font in Message Center Listing.
- Fixed an error when loading the favicon in Message Center.
- Fixed potential ANR if notifications actions take longer than 10 seconds to complete.
- Fixed potential resource-not-found exception on app update for legacy in-app messages
  when using messages with button drawables.


## Version 9.7.1 - March 14, 2019

Fixed a security issue within Urban Airship SDK, that could allow trusted URL redirects in certain
edge cases. All applications that are using Urban Airship SDK 9.0.0 - 9.7.0 should update as soon as
possible. For more details, please email security@urbanairship.com.

## Version 9.7.0 - January 22, 2019
Minor release that allows listing for Urban Airship log messages.

### Changes
- Added ability to listen for logs using a LoggerListener on the Logger class.
- Modal, HTML in-app automation window animations are now defined in the style sheets to
  make them easier to override.


## Version 9.6.1 - January 10, 2019

### Changes
- SDK will now catch any exceptions when attempting to post a notification. None of the notification
  factories provided by the SDK exhibit this behavior, but it's possible that a custom notification
  factory could produce an exception.
- Fix potential crash when attempting to register with FCM if proguard is not configured properly.
- Fix modal In-App Automation message animations.
- The UAWebViewClient will no longer attempt to fetch favicons.


## Version 9.6.0 - December 5, 2018
Minor release that targets Android P and updates dependencies.

### Changes
- Updated compile and target SDK version 28 (Android P)
- Updated urbanairship-core dependencies:
  - Support library version to 28.0.0
  - Optional play-services-location to 16.0.0
  - Optional play-services-ads-identifier to 16.0.0
- Updated urbanairship-fcm dependencies:
  - firebase-messaging to 17.3.4
  - play-services-base to 16.0.1
- Updated urbanairship-gcm dependencies:
  - play-services-gcm to 16.0.0


## Version 9.5.6 - November 20, 2018

Patch release that fixes a race condition when a named user ID changes at the same time as a named
user tag group update is being POSTed. Any apps that are using named user tag groups should update.

## Version 9.5.5 - November 14, 2018

Patch release that fixes an issue with Autopilot. If early take off was disabled it would prevent the
display of push notifications. Apps that disable early take off should update.

### Changes
- Fixed PersistableBundle NPE.
- Fixed ChannelCapture tool sometimes throwing an exception.
- Attempt automatic take off when receiving a new push or token from FCM.
- Marked `RichPushUser#update(boolean)` as library only. Applications should not call this method. It
  is handled internally by the SDK.

## Version 9.5.4 - October 25, 2018

Patch to fix an issue where if two in-app messages have an audience condition for the same tag group,
only the newest tag group will be requested properly. Apps that use in-app automation should update.

## Version 9.5.3 - October 15, 2018
Patch release that fixes displaying HTML in-app messages as full screen
on smaller screen (w480dp) devices.

## Version 9.5.2 - September 20, 2018

Patch release to fix build errors when building with code shrinking enabled while proguard is disabled.

## Version 9.5.1 - September 11, 2018

Patch release to fix a rare NPE with banner messages in In-App Automation. Apps supporting banner
messages should update to this version.

## Version 9.5.0 - September 4, 2018

Minor release that adds support for tag group audiences, miss behaviors and resizable HTML messages in
In-App Automation. HTML in-app messages are now displayed as dialogs by default, with an option
to display fullscreen on smaller devices.

### Changes
- Added support for tag group audience conditions for in-app messages.
- Added `isReady` method to InAppMessageAdapter so that adapters can
  wait for custom app conditions to be fulfilled before displaying
- Fixed media layout in the modal in-app messages when using template HEADER_MEDIA_BODY.
- Fixed videos autoplaying in an in-app message.

## Version 9.4.2 - August 7, 2018

Patch release to fix proguard warnings with ADM.

### Changes
- Update proguard rules.


## Version 9.4.1 - July 26, 2018

Patch release that fixes a bug in the json matcher that caused app version equality checks to malfunction.
Apps that use in-app automation with version triggers or audience conditions should update.

### Changes
- Fixed JsonMatcher scope parsing.


## Version 9.4.0 - July 19, 2018

Minor release that adds new NotificationFactory APIs to better handle limited background
time when building notifications. This release also addresses ANRs from the GCM and ADM
Broadcast Receiver that occur when notification building takes longer than 10 seconds when
using a custom notification factory. To avoid the ANR, the braodcast will wait a max of 10
seconds before finishing received broadcast instead of waiting for the notification to
finish. This will prevent the ANR from occurring, but the OS might kill the app before it
has time to display the notification and it will be lost. To avoid this issue, either use
`NotificationFactory#createNotificationResult(PushMessage, int, boolean)` to retry later
if the notification is taking too long, or use `NotificationFactory#requiresLongRunningTask(PushMessage)`
if the notification requires more than 10 seconds.

### Changes
- Deprecated `NotificationFactory#createNotificationResult(PushMessage, int)` in favor of `NotificationFactory#createNotificationResult(PushMessage, int, boolean)` that also tells the factory if it has longer than 10 seconds to build the notification.
- Added listener to the Whitelist class to reject URLs.
- Added method to generate AirshipConfigOptions from a Properties instance.
- Fixed ANRs caused by slow notification builds in custom factories when using GCM or ADM modules.
- Fixed issue where HTML in-app automation messages were cancelled instead being displayed.


## Version 9.3.2 - June 28, 2018

Fixed an issue where if you define an end time on a automation schedule (action or in-app messages),
it would mark it as expired during the next app init, even if the schedule was not still current. Any app
using either action automation or in-app messages should update.

### Changes
- Fixed marking current schedules with an end time as expired.

## Version 9.3.1 - June 15, 2018

Fixed issues with in-app automation not displaying due to the display being paused by default. Apps
that use in-app automation should update.

### Changes
- Fixed In-app automation display being paused by default.
- Fixed CoreActivity having a visible theme.

## Version 9.3.0 - June 7, 2018

### Changes
- Added support to add custom notification action buttons from xml.
- Added missing `play-services-base` dependency to `urbanairship-fcm`.
- Added method to InAppMessageManager to pause display of in-app messages.
- Locale and Timezone info is now sent up with the channel registration even if analytics are disabled.
- Removed use of custom permissions in the manifest.


## Version 9.2.0 - May 16, 2018

Minor release that includes new APIs to allow extending in-app messages before they are displayed to
the user, exposes information on the ResolutionInfo, and adds a new NotificationFactory create method
that allows retrying a failed notification at a later time. This release also includes fixes to the
Rate App Action crashing on Marshmallow and older devices. Applications that use the rate
app action should update.

### Changes
- Added message extenders to the InAppMessageManager.
- Added a new optional createNotification method that returns status.
- Expose type, duration, and button info on the ResolutionInfo class.
- Fixed BuildConfig conflicts with the urbanairship-sdk module.
- Fixed Rate App Action crashes.


## Version 9.1.1 - May 14, 2018

Patch release to fix issues with proguard as well as enables fullscreen video for landing pages and
message center.

### Changes
- Enable fullscreen video for landing pages and message center.
- Added new error message when trying to display a message center message that is no longer available.
- Fixed proguard issue.

## Version 9.1.0 - April 18, 2018

Minor release that introduces support for FCM apis, modular packages, and in-app message design
updates. For FCM migration, please follow the [FCM Migration Guide](https://github.com/urbanairship/android-library/blob/main/documentation/migration/migration-guide-fcm.md).

### Changes
- Added support for FCM Google Play Services dependency.
- When using `urbanairship-fcm`, setting the FCM sender ID in the airship config options is now optional.
- Moved push providers into own packages - `urbanairship-fcm`, `urbanairship-gcm`, `urbanairship-adm`,
  and `urbanairship-core`. The package `urbanairship-sdk` still exists and is now just a wrapper package
  that depends on gcm, adm, and core to prevent breaking apps.
- Deprecated `urbanairship-sdk` and `urbanairship-gcm` packages. They will be dropped in SDK release 10.0.
- Updated in-app message designs.
- Added support to display an in-app modal message as fullscreen on smaller screen devices.
- Normalized the custom event builder APIs.
- Added metadata option to enable local storage in Urban Airship webviews.
- Updated to Play Services version 15.0.0 and Support Library 27.1.1. Tracking
  Advertising IDs now require the `play-services-ads-identifier` dependency.

### Bug Fixes
- Added calls to takeOff autopilot when in-app message activities are being restored when the app is suspended.
- Fixed packages not declaring the proper dependencies in the pom file.


## Version 9.0.6 - April 5, 2018

Patch release to fix an issue with delaying takeOff. Applications that make use of the isReady method
may want to update.

### Changes
- Remove wait for takeOff in the PushService
- Allow autopilot creation to be retried if the app info is unavailable

## Version 9.0.5 - March 28, 2018

Patch release to fix a BadParcelableException when accessing the PushMessage from an intent's bundle
on some devices.

### Changes
- Fixed BadParcelableException when handling push messages from intents.
- Fixed lint warnings/errors.


## Version 9.0.4 - March 21, 2018

Patch release to fix a NPE due to a race condition in the in-app messaging manager and fixes an issue with
cancelling in-app automation messages. Applications running older versions of SDK 9.0 should update.

### Changes
- Added proguard rule to keep Autopilot class
- Fixed crash in the in-app automation manager.
- Fixed issue with cancelling in-app automation messages.

## Version 9.0.3 - March 14, 2018

Patch release to fix a NPE introduced in 9.0.2 when sending a
notification with a button without any actions.

### Changes
- Fixed NPE in core receiver.
- Allow custom schemes when whitelisting urls.

## Version 9.0.2 - March 5, 2018

Patch release to fix an issue with background services in Android O and a background ANR
when delaying takeOff. Applications that are targeting Android O and take advantage of
push notification actions should update.

### Changes
 - Fixed ANR when takeOff is delayed.
 - Fixed IllegalStateException when opening a push notification with actions.


## Version 9.0.1 - February 13, 2018

Patch release to fix a minor display issue and data validation for in-app messaging.

### Changes
- Fixed in-app message displays when using the EXCLUDE_FROM_AUTO_SHOW flag in the manifest.
- Added missing checks for identifier lengths for both in-app messages and message buttons.


## Version 9.0.0 - January 31, 2018

Major release required for new in-app messaging capabilities.

### New Features
- In-app messaging v2. The new in-app messaging module includes several different
  view types that are fully configurable, including modal, banner, and fullscreen. An
  in-app message is able to be triggered using the same rules as the Action automation
  module.
- A rate app action to prompt the user to rate the application.
- Automation schedule priority: Used to determine the execution order of schedules
  if multiple schedules are triggered by the same event.
- Support for editing automation schedules.
- New active session automation trigger. The trigger will increment its count
  if it has been scheduled during an active session instead of waiting for the next
  foreground.
- New app version automation trigger.
- Extended whitelist URL checking for URL loading instead of just JS bridge
  injection. By default these checks are disabled, but you can enable them
  with the AirshipConfigOptions field `enableUrlWhitelisting`.
- Updated localizations.
- Updated to Google Play Services 11.8.0 and Support Library 27.0.2.


## Version 8.9.7 - January 22, 2018

Fixes a bug with location updates not generating events that was introduced in 8.6. Applications that
make use of this feature should update.

### Changes
- Fixed location updates not generating analytic events.

## Version 8.9.6 - November 21, 2017

Fixes a bug with channel registration updates happening too often if you set the alias as an empty
string instead of null. Applications that are seeing frequent channel updates should update.

### Changes
- Fixed channel registration updates.

## Version 8.9.5 - November 20, 2017

### Changes
- Removes the use of AsyncTaskCompat.
- Added proguard rules to ignore warnings for classes that use optional dependencies.


## Version 8.9.4 - October 24, 2017

Minor change to the dependencies to depend on 26.0.2 instead of 26.1.0 of the support libraries
to prevent pulling in the architecture components. Applications that want to use 26.1.0 can continue
to do so by defining 26.1.0 in the app's build.gradle file. The SDK is still fully compatible with 26.1.0.

### Changes
- Change support library version to 26.0.2.


## Version 8.9.3 - October 17, 2017

Fixes a NPE during channel registration if the devices do not have any tags set. Apps running
8.9.1 or 8.9.2 should update.

### Bug Fixes
- Fixed a NPE during channel registration.


## Version 8.9.2 - October 13, 2017

Bug fixes for a rare NPE that can occur on takeoff when loading XML resources
on takeOff. Any apps seeing this crash should update.

### Bug Fixes
- Catch Android framework NPEs in ActionButtonGroupsParser and ActioRegistry
  XML resource loading

## Version 8.9.1 - October 9, 2017

Bug fixes for the Urban Airship mParticle kit and for some applications that are experiencing large number
of channel registration updates.

### Bug Fixes
- Fixed Channel Registration Payload equality checks to prevent extra channel registration updates.
- Fixed NPE when receiving a push with the Urban Airship mParticle kit.


## Version 8.9.0 - September 28, 2017

Minor feature release.

### New Features
- Added airship config options to set the production and development FCM sender ID. Applications that
  use the same sender ID for both production and development can use `fcmSenderId` to be used in
  both modes.
- Made the Timer class public for in-app messaging customization.
- Updated Google Play Services to 11.4 and Support Library to 26.1.0

### Deprecations
- gcmSender is deprecated in the AirshipConfig options. Use fcmSenderId instead.


## Version 8.8.4 - September 22, 2017

Patch release for a crash involving the analytics event resolver.

### Bug Fixes
- Fixed NPE for an edge case where session ID is null during a database maintenance operation.


## Version 8.8.3 - September 13, 2017

### Bug Fixes
- Fixed not retrying push registration in the same app session when Google Play Services is out of date.
- Fixed security exceptions when trying to start background services.
- Fixed NPE when the push service was started with a null intent.
- Fixed processing push messages sent from other providers when using the same GCM sender ID.

## Version 8.8.2 - August 14, 2017

Patch release for a rare crash involving GCM push handling. Any apps using
GCM and experiencing NPEs in GCMPushReceiver should update.

### Bug Fixes
- Fixed NPE in GCMPushReceiver for the rare case of null extras.


## Version 8.8.1 - August 8, 2017

Patch release for Message Center and GCM Registration.

### Bug Fixes
 - Fixed an index out of bounds exception in the MessageViewAdapter.
 - Fixed NPE in the MessageListFragment.
 - Fixed GCM security exception when trying to register for push.


## Version 8.8.0 - July 25, 2017

Minor release relevant for users requiring high priority delivery support for Android O.

### New Features
- Exposed the in-app message display timer on the InAppMessageFragment.
- Added high priority delivery support for Android O.

### Bug Fixes
- Added null checks to prevent a very rare potential null pointer exception when an intent is received without extras in the AdmPushReceiver.

## Version 8.7.0 - July 18, 2017

### New Features
- Added a fallback job scheduler using the Android Jobs API for Lollipop+ devices when the GcmNetworkManager
  is unavailable. By default, Urban Airship will schedule jobs with Ids between 3000000 - 3000099.
  The start ID can be changed from 3000000 by adding metadata to the AndroidManifest.xml with
  a new start ID under the key `com.urbanairship.job.JOB_ID_START`. The new scheduler can also be prioritized
  over the GcmNetworkManger scheduler by adding metadata `com.urbanairship.job.PREFER_ANDROID_JOB_SCHEDULER`
  with the value `true`.
- Added ability to disable the GcmNetworkManger scheduler by adding the metadata `com.urbanairship.job.DISABLE_GCM_SCHEDULER`
  with the value `false` to the AndroidManifest.xml.


### Bug Fixes
- Fixed warning logs about starting services while the device is in the background on Android O.
- Fixed trying to use the alarm manager to schedule jobs as a fallback on Android O when the GcmNetworkManager is unavailable.
- Added workaround for invalid GCM tokens - https://github.com/googlesamples/google-services/issues/231
- Fixed cursors not being closed in the on device automation.

## Version 8.6.1 - July 3, 2017

### Bug Fixes
- Fixed another GcmNetworkManager crash due to an IllegalArgumentException (https://issuetracker.google.com/issues/37113668).

## Version 8.6.0 - June 20, 2017

### New Features
- Android O compatibility.
- Added support for setting the default notification channel in Airship Config options.
- Added support for setting the notification channel per push from the push API.
- Added a default notification channel. Currently the default channel's name and description is only
  available in english. Other localizations will be provided in an SDK update.

### Behavior Changes
- Android O devices will only have 10 seconds to process a push notification. Custom notification factories
  that require more time can implement `public boolean requiresLongRunningTask(PushMessage message)` to have
  the SDK schedule a job to process the notification when the application has more than 10 seconds.
- Listening for location updates no longer keeps the LocationService alive in the background and is
  only supported on the main process.
- Push messages that are associated with a message center message will no longer wait for the inbox
  to be refreshed. Custom message center implementations should be updated to handle the message
  not being available when the user tries to deep link to the message.
- Fetching images for a big picture notification style will timeout after 10 seconds. If the image times out,
  the notification will be posted without the big picture style.

### Bug Fixes
- Added proguard rules to prevent default Urban Airship actions from being stripped out.
- Fixed tag actions predicates from being applied.

## Version 8.5.1 - June 1, 2017

### Bug Fixes
- Fixed a rare crash that is caused by GcmNetworkManager throwing an IllegalArgumentException (https://issuetracker.google.com/issues/37113668).

## Version 8.5.0 - May 31, 2017

### New Features
- Added a getExtra method to the PushMessage class.
- Added support for setting notification tags from the push API.
- Added support for overriding the icon color/image from the push API.

### Behavior Changes
- Updated the ActionRegistry to lazy-load actions from a resource file.
- Updated pass requests to use basic access authentication.

### Deprecations
- Alias is now deprecated. It will be removed in SDK 10.0.0.

## Version 8.4.3 - May 24, 2017

### Bug Fixes
- Fixed potential time in app reporting bug caused by app suspension.

## Version 8.4.2 - May 23, 2017

### Bug Fixes
 - Fixed crash when rescheduling tag group updates with GcmNetworkManager.

## Version 8.4.1 - May 10, 2017

### Bug Fixes
 - Fixed bug that caused some ADM devices to crash during registration.

## Version 8.4.0 - May 2, 2017

### New Features
- Added support for delayed automation action schedules. The execution of an
automated schedule can now be delayed by up to 30 seconds or made contingent
upon some condition.
- Added support for GcmNetworkManager.
- Added EnableFeatureAction that can enable background location, location, or user
notifications.
- Added an automation trigger for app init events.

### Behavior Changes
- Updated the channel capture tool to function when push is not enabled.
- Decreased the automation schedule limit from 1000 to 100.


## Version 8.3.2 - April 5, 2017

### Bug Fixes
 - Fixed a bug that occurs when setting tags on devices migrating from old SDKs.

## Version 8.3.1 - March 22, 2017

### Bug Fixes
 - Fixed channel registration bug that prevented device tags from updating.

## Version 8.3.0 - February 16, 2017

### New Features
- Added accessor to get the app key in the Javascript native bridge.
- Added support for onNewIntent for both MessageCenterActivity and MessageActivity.
- Added support for intent action com.urbanairship.VIEW_RICH_PUSH_INBOX in the MessageCenterActivity.

### Bug Fixes
- Fixed marking the app-compat library resources as hidden.
- Fixed inbox style notifications to actually display inbox lines.

### Behavior Changes
- Big picture images will now override the largeIcon to the image to expose
  a thumbnail of the image when the notification is collapsed.
- Channel Capture tool is now disabled by default if the app is able
  to receive background push. A new action has been added to enable the
  tool for a limited duration.

### Deprecations
- AdmUtils class and GcmConstants interface are now deprecated. They will be removed in 9.0.0.

## Version 8.2.5 - January 9, 2017
- Fixed bug that caused devices running Ice Cream Sandwich to crash when
  fetching the default Bluetooth adapter.

## Version 8.2.4 - December 22, 2016
- Fixed rare crash in location service for some older devices.
- Fixed typo in `book now` interactive notification button.

## Version 8.2.3 - December 16, 2016
- Updated consumer proguard rule for parcelables to prevent preserving all names in Parcelable classes.

## Version 8.2.2 - December 9, 2016
- Fixed regression where minSDK reverted back to 16.
- Fixed possible NPE when auto tracking advertising ID.
- Removed `.js` extension on the ua_native_bridge file.

## Version 8.2.1 - December 6, 2016
- Fixed MessageFragment empty view when recreating the fragment's view.

## Version 8.2.0 - November 29, 2016
- Added external ID setter to PassRequest class.
- Fixed MessageListFragment empty view when recreating the fragment's view.

## Version 8.1.1 - November 21, 2016
- Fixed rare NPE when ClipboardManager service is unavailable.

## Version 8.1.0 - November 18, 2016
- Added support for resizable landing pages.
- Added support for being able to perform `set` operation on tag groups.
- Added support for tag groups in the add and remove tag actions.
- Added FetchDeviceInfoAction to get an updated snapshot of the devices information from the JS bridge.
- Added broadcast event "com.urbanairship.AIRSHIP_READY" when Airship is ready.
- Changed default predicate on the AddCustomEventAction to accept more situations.
- Changed RetailEventTemplate to only set `ltv` on purchase events.

## Version 8.0.4 - November 11, 2016
- Fixed `unmarshalling unknown type` exception when using location updates.
- Fixed NPE in AirshipService.
- Updated minSDKVersion to 15 from 16.

## Version 8.0.3 - October 13, 2016
- Fixed quiet time if end hour is before the start hour.

## Version 8.0.2 - October 4, 2016
- Fixed analytics issue causing app foreground events during screen rotation.
- Fixed bug when running actions synchronously.
- Reduced inbox user update calls.

## Version 8.0.1 - September 16, 2016
- Fixed message center duplicate ID crash in nested fragments.

## Version 8.0.0 - September 13, 2016
- Removed deprecated APIs.
- Updated minSdkVersion to 16.
- Allowed actions to run on the UI thread.
- Rewrote CustomLayoutNotificationFactory to simplify custom view creation.
- Fixed in-app message background color display on pre 5.x devices without the card view.
- Fixed notification display failure when receiving a big picture URL to an unsupported file type.
- Fixed regression introduced in 7.3.0 where ADM fails to generate a registration token.

## Version 7.3.0 - August 31, 2016
- Added Custom Event templates.
- Added action automation to schedule actions to run when predefined conditions are met.
- Added action situation `SITUATION_AUTOMATION` for actions that are triggered from automation.
- Added action "schedule_actions" to support scheduling actions from the Actions framework.
- Added action "cancel_scheduled_actions" to support canceling scheduled actions in the Actions framework.
- Fixed Quiet Time issue resulting in intervals occasionally starting a day early.
- Fixed issue resulting in latent uploads of event batches greater than 1KB.
- Built SDK against Android N.

## Version 7.2.5 - August 26, 2016
- Added support to route display message requests through the custom message center.

## Version 7.2.4 - August 12, 2016
- Fixed potential NPE in applications using the Gimbal adapter when serializing a RegionEvent without an RSSI value set.

## Version 7.2.3 - August 11, 2016
- Fixed Location module from constantly canceling and requesting location updates when using the
  standard location adapter and location services are disabled.
- Fixed building the sample and library on PCs.

## Version 7.2.2 - August 4, 2016
- Fixed blocking the calling thread when asynchronously requesting UAirship instance if the
  application performs long operations in the onReadyCallback during takeOff.

## Version 7.2.1 - July 27, 2016
- Fixed an issue where the `quiet time enabled` setting wasn’t properly migrated from a key change in 7.1.0.

## Version 7.2.0 - June 21, 2016
- The internal GCM integration has been updated to not conflict with other integrations and will no longer trigger the
  GcmListenerService when receiving GCM messages. Any application that contains additional GCM clients outside of Urban
  Airship needs to register the GcmReceiver from Google Play Services in the AndroidManifest.xml.
- Autopilot is now a concrete class and can be used directly to `takeOff` with config options loaded
  from `airshipconfig.properties`.
- Added support to override the notification sound from the push API.
- Added a wallet action to handle Android Pay deep links.
- Added the Named User ID and Channel ID to the JavaScript bridge.
- Moved the accessor for Named User to UAirship. Old accessor has been deprecated.
- Removed `sample-lib`, `sample` now build against the sdk module directly.
- Added pass creation APIs to generate an Android Pay deep link.

## Version 7.1.5 - June 1, 2016
- Fixed bug preventing the removal of expired RichPushMessages on empty RichPushInbox update responses.

## Version 7.1.4 - May 25, 2016
- Ignore expired RichPushMessages when reloading the RichPushInbox.
- Fixed potential IllegalStateException when dismissing in-app messages.
- Fixed rare IllegalArgumentException due to an invalid URI when listening for content changes (Only reported
on an HTC device).

## Version 7.1.3 - May 11, 2016
- Channel registration failures will now notify AirshipReceiver on 500s.
- Prevent erroneous `RichPushInbox.Listener.onInboxUpdated` update during takeOff.
- Prevent an extra channel registration during first run.

## Version 7.1.2 - April 28, 2016
- Fixed 60 second delay when displaying message center push notifications.

## Version 7.1.1 - April 26, 2016
- Prevent possible ANR when executing the OnReadyCallback during takeOff().

## Version 7.1.0 - April 21, 2016
- Message Center filtering.
- Associated identifiers editor API.
- Associated identifiers support limited ad tracking.
- Tag editor API.
- Analytics.setAutoTrackAdIdentifier to automatically track the user's advertising identifier.
- Autopilot will now take off before Application.onCreate. Applications that use Autopilot can disable early.
  take off by overriding Autopilot.allowEarlyTakeOff in their Autopilot class.
- The default notification icon and accent color are configurable through AirshipConfigOptions.
- AirshipConfigOptions can be defined in an XML resource file.
- Added new notification action button groups.
- Fixed GcmPushReceiver logging erroneous errors.
- Increased custom event property limit to 100.
- Broader localization support.
- Data is now stored on a per app key basis to support conflict-free app key changing.
- Remote input support.
- Deprecated BaseIntentReceiver in favor of AirshipReceiver.

## Version 7.0.4 - March 25, 2016
- Fixed SecurityException on Samsung devices running Lollipop when scheduling alarms beyond the allowed limit.
- Fixed video playback in landing pages and Message Center.
- Fixed rare BadParcelableException by avoiding custom parcelable use in broadcasted intents.

## Version 7.0.3 - February 24, 2016
 - Fixed rare RunTimeException when checking for location permissions in UALocationManager.

## Version 7.0.2 - February 17, 2016
 - Removed the icon padding for in-app message buttons without an icon.
 - Fixed the OnePlus READ_CLIPBOARD security exception in the channel capture tool.

## Version 7.0.1 - February 5, 2016
 - Fixed MessageCenterFragment selection loss on configuration changes.
 - Catch SecurityExceptions when attempting to register for GCM.
 - The LocationService now checks if the application has location permissions before starting.
 - Added a Message Center indicator to the Sample.
 - Simplified the Sample's ParseDeepLinkActivity.

## Version 7.0.0 - January 28, 2016
 - Includes support for out of the box Message Center. The Message Center can be themed to match the application
   or it can be overridden with a custom Message Center implementation.
 - Replaced the usage of com.android.internal.util.Predicate with ActionRegistry.Predicate in the ActionRegistry.
 - Replaced any usage of enums with ints and @IntDef.
 - RichPushManager has been removed. The inbox is now accessible directly off of the UAirship instance.
 - UALocationManager now returns a Cancelable instance instead of a PendingResult when requesting a single location.
 - Removed Eclipse style library distribution. Apps should move to Android Studio and use
   the aar artifact provided in a maven repository.
 - Replaced RichPushSample and PushSample with a new unified sample.

## Version 6.4.3 - January 8, 2016
 - Fixed background push calling the wrong com.urbanairship.push.BaseIntentReceiver callback.

## Version 6.4.2 - January 5, 2016
 - Added a check that Google Play Services version is greater than 8.1.
 - Fixed a possible crash when a device has too many installed apps during the Google Play Store availability check.

## Version 6.4.1 - November 11, 2015
 - Fixed share action dropping the last entry.
 - Fixed rare null pointer exception in our channel capture tool.
 - Added an improved workaround for the GCM security exception.

## Version 6.4.0 - October 29, 2015
 - Added a flag to disable sending the GCM/ADM token with channel registration.
 - Added support for screen tracking.
 - Location permissions are now automatically requested when using the LocationUpdatesEnabledPreference
   on Android Marshmallow (API 23).
 - Added new toast action that displays text in a toast.
 - Fixed displaying in-app messages with translucent status bars.

## Version 6.3.1 - October 23, 2015
 - Fixed possible GCM security exception when receiving messages during upgrade.
 - Improved HTTP 301 handling for big picture notifications.

## Version 6.3.0 - October 1, 2015
 - Support for custom defined properties in custom events.
 - Support for setting associated device identifiers for analytics.
 - Added InstallReceiver to track install attributions.
 - Urban Airship databases are now automatically excluded from the Android Marshmallow auto backup feature.
 - InAppMessageManager's display ASAP flag is no longer persisted in a data store.
 - Google Play Services 8.1.0 or newer is now a required dependency.
 - Updated the minimum Android SDK version to 10 (Gingerbread).
 - Fixed location updates when using the fused location provider.

## Version 6.2.3 - August 28, 2015
 - Fixed missing resources in Eclipse style library project.
 - Fixed malformed rich push user requests introduced in 6.2.0 by correcting the user update payload.
 - Removed check for android.permission.GET_ACCOUNTS.

## Version 6.2.2 - August 24, 2015
 - Schedules alarms with FLAG_UPDATE_CURRENT instead of FLAG_CANCEL_CURRENT to prevent SecurityExceptions
   on Samsung devices running Lollipop.
 - Rich Push API client no longer performs remote deletes when messages are omitted in the message
   list retrieval. This mitigates an edge case when a user’s inbox contains more than 500 messages.

## Version 6.2.1 - August 20, 2015
 - Updated the samples to point to 6.2.1.

## Version 6.2.0 - August 19, 2015
 - GCM registration now uses Instance ID tokens instead of registration IDs.
 - Google Play Services 7.5 or newer is required for GCM registration.
 - Added AndroidManifest.xml for automatic manifest merging. Existing integration
   will be required to remove Urban Airship manifest entries from their AndroidManifest.xml.
 - Added annotations using the Android support annotation library to improve code inspection.
 - Added a default landing page theme and layout to display as a modal overlay.
 - Fixed NPE when creating a CustomLayoutNotificationFactory before takeOff.

## Version 6.1.3 - July 28, 2015
 - Fixed UAWebView not displaying the soft keyboard when interacting with an html input field.
 - Catches SecurityException when attempting to use UrbanAirship location without the proper location
   permissions. Useful for Android M when location permissions can be revoked.

## Version 6.1.2 - July 1, 2015
 - Fixed tag group error logging.
 - First version available through https://bintray.com/urbanairship.

## Version 6.1.1 - June 24, 2015
 - Fixed tag group retries.

## Version 6.1.0 - June 22, 2015
 - Support for channel and named user tag groups.
 - Support for displaying RichPushMessages in the LandingPageActivity.
 - Added new action "open_mc_overlay_action" that displays a RichPushMessage in a LandingPageActivity.
 - Updated "open_mc_action" action to fall back to displaying a RichPushMessage in the LandingPageActivity
   if the intent action "com.urbanairship.VIEW_RICH_PUSH_MESSAGE" fails to start an activity.
 - Notification opens that are associated with a RichPushMessage will now automatically trigger the
   "open_mc_action" action if neither the "open_mc_overlay_action" or "open_mc_action" is present.
 - Added clipboard action that allows copying text to the clipboard.
 - Added channel capture test tool to retrieve the Urban Airship Channel ID from a device for testing
   and diagnostics in end user devices.
 - Notification content intents will now only be triggered if the push intent receiver is missing or
   does not launch an activity.
 - In-app message "display ASAP" mode now attempts to display any pending in-app messages on dismiss.
 - Updated the minimum sdk version to API 8 (Froyo).


## Version 6.0.2 - May 21, 2015
 - Fix intermittent in-app message crashes.

## Version 6.0.1 - April 3, 2015
 - Fix deadlock caused by an expired in-app message.

## Version 6.0.0 - March 31, 2015
 - Support for in-app messaging.
 - Support for associating and disassociating a channel to a named user.
 - Added a flag to enable/disable analytics at runtime. Useful for providing a privacy opt-out switch.
 - Added new region events for proximity triggers.
 - Support listening for UAWebViewClient action runs by allowing an optional ActionCompletionCallback.
 - Updated AutoPilot to now call takeOff instead of a general "execute" method.
 - Exposed the PushMessage directly instead of the Push bundle when broadcasting push events.
 - Fixed Urban Airship Javascript bridge when the associated RichPushMessage contains invalid characters in its title.

 ### Actions framework
   - Added new ActionValue class that limits the type of values ActionArguments and ActionResults
     can contain.
   - ActionArgument's metadata now returns a bundle of metadata.
   - Removed action name from action method signatures. The action name is now available with the action arguments
     metadata.
   - Replaced ActionRunner with a new ActionRunRequest class that provides a fluent API for running actions.
   - Replaced the RichPushMessage with its ID in the ActionArguments metadata when triggering actions
     from a web view with an associated RichPushMessage.

## Version 5.1.6 - March 5, 2015
 - Added Intent flag FLAG_ACTIVITY_SINGLE_TOP when automatically launching the application from a notification open to prevent
   relaunching the launcher activity if its already on top.
 - Fixed the Airship Javascript bridge sometimes failing to load on API 19+.

## Version 5.1.5 - January 27, 2015
 - Fixed a regression introduced in 5.1.4 where the LocationService will cause a NPE if started with a null intent.

### Samples
 - Fixed the Rich Push Sample's deep linking.

## Version 5.1.4 - December 31, 2014
 - Updated logging usage to use the various log levels more appropriately.
 - Default to mixed content mode MIXED_CONTENT_COMPATIBILITY_MODE on all UAWebViews. This allows landing
   pages and rich messages to continue to display images from non https sources.

## Version 5.1.3 - December 15, 2014
 - Fixed crash that occurred when the fused location provider failed to connect. Applications with the UA
   location service in their manifest were potentially impacted.
 - Added workaround for an Ice Cream Sandwich issue (https://code.google.com/p/android/issues/detail?id=20915)
   in which the AsyncTask would be initialized on the wrong thread and throw a RuntimeException.

## Version 5.1.2 - December 8, 2014
 - Fix crash when building against Google Play Services 6.5 without the location APIs.
 - Updated samples to the latest Android gradle plugin 1.0.0.

## Version 5.1.1 - December 8, 2014
 - Fixed GCMPushReceiver crash when receiving unordered broadcasts due to a misconfigured AndroidManifest.xml.
 - Updated samples to the latest Android gradle plugin 1.0.0-rc4.

## Version 5.1.0 - November 3, 2014
 - Advanced notification support through the Push API, including support for Android Wear and styles.
 - Added setter on provided notification factories to set the notification accent color for Android Lollipop.
 - Includes enhanced security in the Urban Airship Javascript interface by providing a URL whitelist.
 - The aar package now includes a consumer proguard file for required Urban Airship proguard rules.
 - Updated interactive notification icons to material design icons provided by Google from
   http://google.github.io/material-design-icons/.
 - The default notification flags are no longer applied after the notification factory. Instead, they are
   now expected to be set in the factory.

### Samples
 - Fixed the CoreReceiver priority in AndroidManifest.xml for Push Sample and Rich Push Sample by
   moving the priority from the receiver to the receiver's intent filter.
 - Rich Push Sample has been updated with material design and simplified navigation.

## Version 5.0.3 - October 23, 2014
 - Removed "allowBackup" from the libraries application manifest entry to avoid merge conflicts with
 the Android manifest merger.

## Version 5.0.2 - October 21, 2014
 - Fix issue with running actions through the JavaScript native bridge from the "ualibraryready" event.
 - Fix issue with landing page not displaying on top of the launcher activity.

## Version 5.0.1 - October 8, 2014
 - Fix issue with channel not creating when missing Google Play Services for GCM registration.
 - Fix issue with the Urban Airship Javascript interface not loading for custom web views that extend
   UAWebView.

## Version 5.0.0 - October 1, 2014

### New Features
 - Unified support for both GCM (Google) and ADM (Amazon) transports.
 - Includes support for interactive notifications. Includes more than 25 built-in
   interactive notification sets, including button resources for 9 languages.
   Additional/replacement localization strings may be added to built-in actions.
 - Includes a new action for social sharing that can be called from pushes or web views.
 - Includes support for defining custom events in our reporting system.
 - Rewritten location module with a simplified API and support for Google's Fused
   location provider.
 - Support for asynchronous takeoff and access to the Urban Airship Library to avoid
   blocking the main thread.
 - Introduced a new device identifier, the Channel ID, which will replace APIDs as the push address
   in Urban Airship API calls.
 - Includes support for registering multiple GCM sender Ids. Only the main GCM sender
   ID's messages will be handled by Urban Airship. The rest will be ignored.
 - Simplified push integration. The push intent receiver is now optional and the library
   supports automatically launching the application if the push intent receiver is not
   set or does not handle the open notification broadcasts.
 - A new default notification factory that uses the Big Text notification style.
 - User notifications can be disabled without disabling push. This allows the application
   to still be messaged when notifications are disabled.
 - The push broadcasts for push received, push opened, and registration events have
   been updated. A base broadcast receiver 'BaseIntentReceiver' can be extended
   to parse the intents and provide convenient listener style callbacks.
 - It is no longer necessary to check for Urban Airship Actions when deciding to
   launch the application in the push intent receiver.

### Packaging Changes
 - Urban Airship library is now a library project with resources. Eclipse users will
   have to import the library as a project and reference it in the projects settings. An
   aar package is provided for Android Studio users.
 - The library now depends on the latest version of the v4 support library.
 - GCM and Fused location depends on the Google Play Services library.

### Sample Changes
 - Samples now set up to work with both GCM (Google) and ADM (Amazon).


Amazon Only - ## Version 4.0.0 - July 14, 2014
 - Initial Amazon release.

## Version 4.0.4 - August 5, 2014
 - Fix regression in 4.0.3 that broke webview actions for devices running API 17 or higher.

## Version 4.0.3 - July 21, 2014
 - Add if-modified-since header and accept 304 not modified responses for Rich Application message listing.
 - Remove any usage of addJavascriptInterface for Android devices older than API 17 to prevent abuse of upstream security issue CVE-2012-6636.

### Samples Changes
 - Updated to use the latest Android Gradle plugin and build tools.

## Version 4.0.2 - April 30, 2014
 - Fix NPE when defining a Landing Page Activity with no meta data elements.
 - Fix location security issue where all location data was broadcasted with
   implicit intents. Thank you to Yu-Cheng Lin for reporting this issue.
 - Added ability to set the intent receiver for location updates on UALocationManager.

### Push Sample Changes
 - Use local broadcast receiver from the support package to broadcast intents to
 the application.

## Version 4.0.1 - March 26, 2014
- Fix possible rich push user token corruption.

## Version 4.0.0 - March 25, 2014
- Added Urban Airship Actions framework - a generic framework that provides a convenient way to
automatically perform tasks by name in response to push notifications, Rich App Page interactions and JavaScript.
- Added UAWebViewClient class to be used with RichPushMessageWebView and LandingPageWebView to
provide proper auth and inject the javascript bridge.
- Renamed RichPushMessageView to RichPushMessageWebView.
- Removed ability to set a custom Rich Push Javascript Interface and namespace through RichPushManager.
- Removed RichPushMessageJavascriptInteface and RichPushMesssageJavascript in favor of the new UAJavascriptInterface.

- Deprecated "urbanairship" Javascript namespace.  iOS and Android now both use the common namespace "UAirship".
- Deprecated Javascript interface methods isMessageRead, markMessageRead, markMessageUnread,
navigateTo, getViewWidth, getViewHeight, and getDeviceOrientation.

### Rich Push Sample Changes
 - Updated AndroidManifest.xml for Actions.
 - Added custom Landing Page layout and styles.
 - Added MessagePagerFragment to display Rich Application Pages in a view pager.
 - Refactored inbox sample code to move most of the logic to fragments.
 - Added deep linking.

### Push Sample Changes
 - Updated AndroidManifest.xml for Actions.
 - Added deep linking.

## Version 3.3.2 - March 7, 2014 - Internal Release
- Use nondestructive User api requests.  Allows multiple device tokens and APIDS
to share a single user.
- Deprecated RichPushUser getApids, setApids, addApids.

## Version 3.3.1 - February 24, 2014
- Persist Urban Airship push ids to help prevent duplicate pushes.
- Fix analytics crash when using autopilot with background location.

## Version 3.3.0 - January 15, 2014
- For rich push enabled apps, automatically refresh rich push messages when the application is foregrounded.
- Remove unused RichPushManager listener callbacks.
- Prevent multiple APID registration when database read and write failures occur.

### Sample changes
- Remove action bar sherlock dependency, replaced with AppCompat in the android support v7 library.
- Added calls to the webkit's onPause/onResume methods for api >= 11 when displaying a rich push message.
- Android Gradle plugin support (Gradle 1.9 with the plugin 0.7.0).

## Version 3.2.3 - December 19, 2013
- Fix possible NPE when the rich push service unexpectedly die.
- Fix a possible crash in the content resolver when it throws an unexpected runtime exception.

## Version 3.2.2 - December 4, 2013
- Exposes UA push identifiers to the application in the push receiver and notification builder.

## Version 3.2.1 - November 18, 2013
- Fixed database exception crash when uploading analytics in multi-process applications.

## Version 3.2.0 - October 31, 2013
- Added support for server side expired Rich Push Messages.
- Added support for setting contentIntent on a notification through the notification builder.
- Added process manifest validation for Urban Airship services and receivers.
- Added pre-authorization headers when displaying rich push messages through the RichPushMessageView.
- Added wake lock expiration to ensure wake locks do not wake the app indefinitely.
- Fix permission crash when using location with permission ACCESS_COARSE_LOCATION.

## Version 3.1.0 - July 31, 2013
- Removed tags and alias from rich push user. Tags and aliases are now only set on the APID.
- QuietTimePickerPreference now respects the system/locale settings in UI components.
- Rich push inbox style notification now respects quiet time, vibrate, and sound preferences.
- Updated third party license.
- Updated copyrights.
- Built-in methods for JavaScript embedded in rich push messages:
  - Added getMessageSentDateMS that returns the unix epoch time in milliseconds.
  - Added seconds to getMessageSentDate to return format "yyyy-MM-dd HH:mm:ss.SSSZ".

## Version 3.0.0 - June 10, 2013
- Rich Push Inbox no longer implements or exposes any cursors.
- Added warnings when running on BlackBerry.
- Added default Android preferences for push, location, vibration, quiet time and sound.
- Added RichPushMessageView to easily display rich push message for API level >= 5.
- Removed Helium and hybrid push transportations. Only GCM is supported.
- Removed Push Worker Service.

- Fixed Rich Push crash at first start when the device has no internet.
- Fixed activity instrumentation warnings when analytics is disabled.
- Fixed crash in location service when the network provider is missing.
- Fixed security exception crash when registering with GCM.
- Fixed javascript bridge on devices targetting API 17 because of missing annotation.

### Rich Push Sample changes
- New preferences screen using built-in Android preferences
  - Added Advanced Settings: APID, UserID, Set Alias, Add Tags
- Updated the Home and Inbox layouts
- Removed the cursor adapter
- Added a Home screen and key guard Inbox widget
- Added a custom Inbox style notification builder
- Added automated ui tests

### Push Sample changes
- Added extra preferences screen using built-in Android preferences

## Version 2.1.4 - April 4, 2013
- Fixed crash when switching rapidly between message and inbox views for RichPushSample.
- Fixed crash in phone layout whenever a message is opened from a push notification for RichPushSample.
- Add ActivityLifecycleCallbacks to record session events and warn devs for incomplete implementation.
  Make instrumentation activities in API 14 (Ice Cream Sandwich) optional.
  Added 'minimumSdkVersion' parameter in airshipconfig.properties file. This value is used for detecting
  incomplete activity instrumentation.

## Version 2.1.3 - March 22, 2013
- Use application package name instead of app name for user-agent creation.
- Fixed crash in inbox view running on a non-paned (phone) view for RichPushSample.
- Added validation for integer values in .properties files
- Added a foreground location enabled preference. If the background location enabled preference is true,
  then the foreground location enabled is implicitly enabled. Added foreground location enabled checkbox
  to the Push Sample app.
- Modified recordCurrentLocation() and recordCurrentLocation(criteria) methods to handle the binding
  to the LocationService if it has not been bound already. This will save devs some trouble when
  they just want to record the current location without binding to the service.
- Updated README and documentation with correct links and updated config options.
- Replaced deprecated 'horizontalAccuracy' location configuration property with 'accuracy'.
- Updated sample config files to use strings for properties mapped to system constants..
- Updated sample apps to use the default Android Proguard config file.

## Version 2.1.2 - February 15, 2013
- Fix for push opt-out analytics

## Version 2.1.1 - February 14, 2013
- Now with Rich Push
- Property files now allow strings in addition to integer values for properties mapped to system constants

## Version 2.1.0 - February 7, 2013
- Moved In-App Purchase code from the library to the IAP Sample.

## Version 2.0.5 - January 29, 2013
- Fixed incorrect location parameter handling (min update intervals)
- Stop the PushService after GCM registration is complete and all
  additional work has been passed to worker services.
- Updated internal test suite

## Version 2.0.4 - January 2, 2013
- Fixed ExceptionInInitializerError that could arise on firstrun
  using Helium transport
- Sample app PushPreferencesActivity no longer accesses location-related
  preferences if location is not enabled

## Version 2.0.3 - December 12, 2012
- Fixed miscellaneous crashes based on submitted crash reports.
- Format locations in a locale-independent manner.
- Reset the GCM registration ID when the package is replaced (requires addition of
  a PACKAGE_REPLACED filter on GCMPushReceiver).
- Manifest validation is now only performed in development mode.
- Limit API re-registrations (no delta) to once every 24H.
- Applications can now optionally defer calling takeOff with a new 'Autopilot' feature.
  If you have access to the Application class, it is best to continue calling takeOff()
  there, but if you do not (e.g., in an AIR app), this feature will allow your app
  to delegate the takeOff to a class declared in the manifest.
- The library will no longer stop the PushService on shutdown if the transport is GCM (not necessary).

## Version 2.0.2 - November 13, 2012
- Added checks for duplicate messages based on the UA canonical push ID

## Version 2.0.1 - October 31, 2012
- Fixed a crash issue during push receipt.

## Version 2.0.0 RC2 - October 12, 2012
- Run all GCM registrations in a worker service
- Added validation for new manifest requirements

## Version 2.0.0 RC1 - October 1, 2012
- Added GCM support and removed C2DM support
- Added Rich Push support
- Replaced use of Android shared preferences with a SQL-backed provider. This was done to address
  data corruption issues seen in multi-process applications.
- The library will now print the application's APID to the console even if logging is turned down.
  This allows developers to expose the APID for debugging without logging any other UA information.
- FIX: Addressed an issue with UA API call retries
- Moved the event reporting uploads to an intent service. This will allow developers to decide which
  process will be used for event uploads.
- FIX: The library will no longer attempt to register with the API if a Helium connection cannot be
  established.

## Version 1.1.6 - August 29, 2012
- Catch all exceptions (even unchecked ones) in the Helium connection loop. This is designed to help
  mitigate OS and Carrier/MFR mod bugs like the one addressed in 1.1.5 and an IllegalArgumentException
  reported by a customer that was originating in a low-level OS component.

## Version 1.1.5 - August 22, 2012
- FIXED: Addressed crash in java.net.NetworkInterface. Library will explicitly catch a
  NullPointerException caused by ICS bug. See http://code.google.com/p/android/issues/detail?id=33661

## Version 1.1.4 - July 31, 2012
- FIXED: Reset backoffTime after successful C2DM registration
- FIXED: Updated the copyright for 2012
- FIXED: Removed logging of app secret in takeOff
- Moved log levels to airshipconfig.properties
- Added a User-Agent to the httpclient
- Added handling for the new boolean return from EmbeddedPushManager.init()
- Added logic to handle when no location providers are available.
- Wrapped db calls in EventDataManager so sqliteexception's won't kill apps.
- Added the application package name to implicitly broadcasted actions.

## Version 1.1.3 - May 4, 2012
- FIXED: issue with location event payload

## Version 1.1.2 - May 1, 2012
- FIXED: incorrect handling of minimum updated time in location preferences
- FIXED: unnecessary starting of location service
- Updated target SDK level of sample apps to API level 9 to address ICS
  notification background rendering issues

## Version 1.1.1 - April 11, 2012
- FIXED: Issues with background location
- FIXED: Issues with no location providers enabled
- Sample App updates

## Version 1.1.0 - April 3, 2012
- Added location collection for Push to Location
- FIXED: C2DM null APIDs will now be regenerated
- FIXED: C2DM deregistration race condition is resolved

## Version 1.0.9 - March 1, 2012
- FIXED: Character encoding bug preventing push registration with Unicode tags

## Version 1.0.8 - February 16, 2012
- FIXED: Hybrid mode now works on the Kindle Fire
- FIXED: Retry inserts to analytics DB when write fails due to DB lock
- FIXED: Connectivity issues following device sleep
- FIXED: Default to a maximum of 40 notifications (from 100). Android allows a given app to post up to 50.
- Added additional analytics data points
- Added a note to the "Holding Pattern" error to indicate that it can occur during scheduled maintenance.
- Added an option to wake a wifi-only device periodically to receive notifications

## Version 1.0.7 - December 15, 2011
- FIXED: Crash occurring when UA API registrations were disabled

## Version 1.0.6 - December 14, 2011
- FIXED: Cached Helium server list is no longer removed when the connection is destroyed
- FIXED: Helium connections dropped due to network reachability issues will no longer invalidate the server
- Improved reconnection logic when Android sends multiple connectivity intents
- The Helium server cache TTL is now configurable by the server
- Added a short 3 sec delay after server lookup to allow server-side state to propagate
- Use elapsed realtime clock for heartbeat timer rather than the wall clock

## Version 1.0.5
- Improved socket keepalive logic

## Version 1.0.4
- StrictMode compatibility for analytics events and com.urbanairship.restcient.*
- Fixed incomptibility between Request.executeAsync and API level 11+
- Improved InventoryAdapter and Drawable caching in IAPSample
- Fixed AsyncImageLoader OutOfMemoryException issue
- New PushManager.getApid convenience method
- Disabling push results in a call to DELETE an existing APID on go.urbanairship.com
- Improved C2DM registration handling

## Version 1.0.3
- Fixed an issue preventing immediate connections to Helium when re-enabling push notifications

## Version 1.0.2

- Improved Helium connectivity
- Added push expiration handling
- Added additional key and secret validation
- PushService now stops when push is disabled
- Added helpful logging, removed unhelpful logging

## Version 1.0.1

### New Features
- Manifest validation for both IAP and Push reports permission, receiver and service
  misconfiguration in the log
- AirshipConfigOptions validation now reports specific errors in the log

### Push Changes
- Explicitly log errors when PushService is started before UAirship.takeOff has been called.
- Documented and deprecated PushManager.EXTRA_STRING_EXTRA. Push extras should be sent as a
  Map<String,String>
- Sample app now iterates through the extras instead of using EXTRA_STRING_EXTRA
- Fixed NullPointerException occurring when a user opened a notification when an intent receiver
  had not been registered with PushManager
- Helium now explicitly reports when connections are denied due to billing status
- Aliases can now be removed by passing null to PushManager.setAlias()
- ACTION_REGISTATION_FINISHED is now called only after the APID is valid and can receive pushes
- BasicPushNotificationBuilder and CustomPushNotificationBuilder now ignore pushes where the alert
  payload is null or empty. To override this behavior, provide your own implementation of
  PushNotificationBuilder.

### IAP Changes
- Moved IMarketBillingService from the JAR to the sample project (requires projects to include
  IMarketBillingService.aidl in their source tree)
- Repackaged all Google billing code to prevent conflicts (no action required)
- Use Product ID instead of product name for download location
- IAP download paths are now sanitized to prevent write failure on SD cards
- Purchased products can now be restored at any time

## Version 1.0.0

### New Features
- Added Push Settings UI to PushSample
- Devices can now switch from C2DM to Helium

### Bug Fixes
- Properly handle C2DM failures in devices with an API Level < 8
- Ignore C2DM registration intents if the current transport type
  does not use C2DM
- Properly deregister a device from C2DM if push is disabled
- Fixed a Helium push NPE if UA servers refuse connections

### Other Changes
- Cleaned up logging
- Replaced various broadcast receivers with com.urbanairship.CoreReceiver (this requires
  a Manifext.xml change as the CoreReceiver package changed)
- Added Status Bar icon field to CustomNotificationBuilder
- Added C2DM Ping capability (invisible push for audience counting)
- Updated sample projects with improved layouts and explicit Honeycomb compatibility

## Version 0.9.1

### New Features
- C2DM Support
- Reports Support
- Built in APID Tagging and Alias support

### Bug Fixes
- Fixed asynchronous image loading issue in the InventoryAdapter class in StoreFront sample
- Notification builders can now return null if a notification should not be displayed
- Sending a notification with an empty alert field prevents the notification from being
  displayed by the default notification builders

### Other Changes
- Combined Push/IAP HTTP clients in the rest client package
- IAP Sorting updates
- EmbeddedPushService is now called PushService (to accommodate C2DM). A manifest change is
  required
- Analytics/Reporting push received proxy broadcast handler must be registered in
  AndroidManifest.xml
- In your implementation of the custom push/registration IntentReceiver,
  PushManager.ACTION_NOTIFICATION_OPENED_BASE should now be
  PushManager.ACTION_NOTIFICATION_OPENED
- The REGISTRATION_FINISHED action now has C2DM-specific extras
