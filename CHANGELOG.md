# Android 21.x ChangeLog

[Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration)

[All Releases](https://github.com/urbanairship/android-library/releases)

## Version 21.0.0 - September 14, 2026

SDK 21.0 raises the minimum SDK to 26, moves public timestamps and durations to `java.time.Instant` and `kotlin.time.Duration`, brings on-device AI to Scenes and in-app experiences, adds Compose banner and embedded carousel hosts, and tightens the public API surface across every module. See the [Migration Guide](https://github.com/urbanairship/android-library/blob/main/documentation/migration/migration-guide-20-21.md) for details.

### Changes
- `minSdk` is now 26 (Android 8.0), and the SDK builds against `compileSdk` 36 with Kotlin 2.2.20 and JDK 17.
- Migrated public timestamps from `Long`/`Date` to `java.time.Instant` and durations from `Long` to `kotlin.time.Duration`. Every `Duration`-typed member ships a Java-friendly counterpart. JSON, wire formats, and persisted values are unchanged.
- Tightened the public API surface across all modules with `@RestrictTo`, which now also covers nested types and overrides. The layout module in particular went from 235 accidentally-public declarations to 9. A few APIs that were always meant to be public — `AirshipInputValidation.Request`, `ChannelType`, `InAppMessageColor`, and the custom view APIs — are no longer flagged as restricted.
- Added binary compatibility validation, so the public API of every module is now dumped to `api/` and checked in CI.
- Changed `PushProvider.getRegistrationToken` and `PushManager.performPushRegistration` to suspend functions. Custom `PushProvider` implementations must update their overrides.
- Removed the deprecated `PushManagerExtensions`. Both extensions are now members on `PushManager`.
- Renamed the layout event types on `EventType` and `EventAutomationTriggerType` from `IN_APP_*` to `LAYOUT_*`, since they are emitted by Scenes rather than only by in-app messages. `IN_APP_DISPLAY` and `IN_APP_RESOLUTION` keep their names. Reporting values sent to Airship are unchanged.
- Enabled `-Xconsistent-data-class-copy-visibility`, so `copy()` on `AutomationSchedule.ScheduleData.Deferred` and `EmbeddedViewItem` is no longer public.

#### On-Device AI
- Added on-device AI support for three usages, reached through `Airship.ai`: in-app message suppression, embedded view selection, and Scene text-input inference. Apps supply their own model by implementing `ModelAdapter`; prompts and context stay on the device, and app-supplied context is never sent to Airship.
- Added the `PrivacyManager.Feature.ON_DEVICE_AI` privacy manager feature gating on-device AI. It is included in `Feature.ALL`, but apps that enable an explicit set of features must add it or `Airship.ai` will behave as though no model were configured.

#### Scenes
- Added six `Permission` values — `APP_TRACKING_TRANSPARENCY`, `CAMERA`, `MICROPHONE`, `BLUETOOTH`, `PHOTO_LIBRARY`, and `CONTACTS` — for the Composer's new system-permission action.
- Added the `stack_image_view` view type and `redact_input` support for text input form fields.
- Let a modal's or banner's entrance and exit use different transition effects, matching web.
- Fixed a broad set of layout and sizing issues, including percent- and ratio-sized children under auto-sized parents, media sizing, pager indicator dot sizing, scroll viewport handling, banner placement parsing, and label text clipping and truncation. Verify your live Scenes after upgrading.
- Fixed label icon placement and gaps to resolve from the layout's start/end and the locale's direction, so `icon_start` is correct in RTL.
- Fixed score items and animated icon drawables not always starting.

#### Embedded Views
- Added `AirshipEmbeddedCarousel`, a Compose carousel container for embedded content.
- Added `filterInstances` to `AirshipEmbeddedView` and `AirshipEmbeddedViewGroup`, a predicate that decides which pending instances are eligible. `selection` still decides order.
- Added `AirshipEmbeddedSelection.ByAi`, which ranks pending content with an on-device model and falls back to priority, a comparator, or an instance list.
- Changed `AirshipEmbeddedSelection.ByInstanceId` to take an ordered list of instance IDs rather than one. It is now an allow-list — pending content that isn't named is excluded rather than ordered last.
- Fixed `AirshipEmbeddedInfo.priority` always reporting `0`, which made comparators that sort on it a no-op and made observer filters that test it match everything. `AirshipEmbeddedInfo` also gains `contentDescription`.

#### In-App Automation
- Added `InAppMessagingInterface.onCheckSuppression`, an app-side callback that can suppress a message before it displays and choose how the miss is reported.
- Added `AirshipBannerHost`, a Compose host for in-app message banners, with `rememberAirshipBannerHostState`.
- Changed display limit calculation to use a display ledger, with retention and compaction, and fixed limit enforcement gaps for shared display groups and penalized attempts. Shared-group pooling is behind an explicit opt-in.
- Added Experiment Groups variant control and miss resolution, and `audience_subset_overrides` support in audience hashing.
- Let a deferred response specify its audience miss behavior.
- Improved parse error handling, so a malformed schedule or payload from one source no longer prevents the rest from being parsed.

#### Message Center
- Added localized accessibility strings.
- Fixed message date formatting.

#### Preference Center
- Added support for embedding the Compose Preference Center in wrap content parents.
- Fixed XML Preference Center edge-to-edge insets.

#### Feature Flags
- Added a public `FeatureFlagManager.waitRefresh`, with a `waitRefreshPendingResult` counterpart for Java callers.

### Other Fixes
- Fixed remote data status not always updating as expected after a refresh.
- Hardened Live Update notification `PendingIntent`s.
- Fixed `PushMessage.isExpired` throwing on an out-of-range expiration.
- Fixed an inverted delay comparison in `EventManager.scheduleEventUpload`.
- Fixed `info.isChecked` not being set in accessibility delegates.

