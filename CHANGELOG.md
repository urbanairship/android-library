# Android 20.x ChangeLog

[Migration Guides](https://github.com/urbanairship/android-library/tree/main/documentation/migration)

[All Releases](https://github.com/urbanairship/android-library/releases)

## Version 20.0.2 - November 4, 2025

Patch release that fixes prompting for permissions on foreground.

### Changes
- Fixed prompting for permissions on foreground.
- Removed usage of material icons compose library.
- Updated Message Center titles to be markes as headings.

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
