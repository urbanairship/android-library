# Airship Android SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.urbanairship.android/urbanairship-core.svg)](https://search.maven.org/search?q=g:com.urbanairship.android)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

The Airship SDK for Android provides a comprehensive way to integrate Airship's customer experience platform into your Android applications.

## Features
- **Push Notifications** - Rich, interactive push notifications with deep linking (FCM, HMS, ADM)
- **In-App Experiences** - Contextual messaging, automation, and Scenes
- **Message Center** - Inbox for push notifications and messages
- **Preference Center** - User preference management with customizable UI
- **Feature Flags** - Dynamic feature toggles and experimentation
- **Live Updates** - Real-time content updates
- **Analytics** - Comprehensive user behavior tracking
- **Contacts** - User identification and contact management
- **Tags, Attributes & Subscription Lists** - User segmentation, personalization, and subscription management
- **Privacy Controls** - Granular data collection and feature management
- **Jetpack Compose Support** - Modern Compose UI components

## Installation

Add the Airship Android SDK to your project using Gradle. In your app's `build.gradle.kts`:

```kotlin
val airshipVersion = "<latest_version>"

dependencies {
    // Core SDK - Required for all features
    implementation("com.urbanairship.android:urbanairship-core:$airshipVersion")

    // --- Push Providers ---
    // Choose one or more
    implementation("com.urbanairship.android:urbanairship-fcm:$airshipVersion")   // Firebase Cloud Messaging
    implementation("com.urbanairship.android:urbanairship-hms:$airshipVersion")   // Huawei Mobile Services
    implementation("com.urbanairship.android:urbanairship-adm:$airshipVersion")   // Amazon Device Messaging

    // --- In-App Automation & Scenes ---
    implementation("com.urbanairship.android:urbanairship-automation:$airshipVersion")
    // Optional - For Jetpack Compose support in Scenes
    implementation("com.urbanairship.android:urbanairship-automation-compose:$airshipVersion")

    // --- Message Center ---
    // Choose one UI implementation
    implementation("com.urbanairship.android:urbanairship-message-center:$airshipVersion")   // View-based UI
    implementation("com.urbanairship.android:urbanairship-message-center-compose:$airshipVersion") // Compose UI

    // --- Preference Center ---
    // Choose one UI implementation
    implementation("com.urbanairship.android:urbanairship-preference-center:$airshipVersion")  // View-based UI
    implementation("com.urbanairship.android:urbanairship-preference-center-compose:$airshipVersion") // Compose UI
    
    // --- Feature Flags ---
    implementation("com.urbanairship.android:urbanairship-feature-flag:$airshipVersion")

    // --- Live Updates ---
    implementation("com.urbanairship.android:urbanairship-live-update:$airshipVersion")

    // --- Debug ---
    // Optional - For development builds only
    debugImplementation("com.urbanairship.android:urbanairship-debug:$airshipVersion")
}
```

## Quick Start

### 1. Initialize Airship

Create an `Autopilot` class to automatically initialize Airship:

```kotlin
class MyAutopilot : Autopilot() {
    override fun createAirshipConfigOptions(context: Context): AirshipConfigOptions {
        return airshipConfigOptions {
            setAppKey("YOUR_DEFAULT_APP_KEY")
            setAppSecret("YOUR_DEFAULT_APP_SECRET")
            setInProduction(!BuildConfig.DEBUG)
        }
    }

    override fun onAirshipReady(context: Context) {
        // Airship is ready! Configure additional settings here.
        Airship.push.userNotificationsEnabled = true
    }
}
```

Register your Autopilot in your `AndroidManifest.xml`:

```xml
<application>
    <meta-data
        android:name="com.urbanairship.autopilot"
        android:value="com.example.MyAutopilot" />
</application>
```

### 2. Configure a Push Provider

Add the FCM module and Google Services plugin to your `build.gradle`:

```groovy
dependencies {
    implementation("com.urbanairship.android:urbanairship-fcm:<latest_version>")
}

apply plugin: 'com.google.gms.google-services'
```

Add your `google-services.json` file to your app directory.

## Requirements

- Minimum SDK 23+ (Android 6.0+)
- Compile SDK 36+ (Android 15+)

## Documentation

- **[Getting Started Guide](https://docs.airship.com/platform/android/)** - Complete setup guide
- **[API Reference](https://docs.airship.com/reference/libraries/android/latest/)** - Full API documentation
- **[Migration Guides](documentation/migration/README.md)** - Comprehensive migration documentation
- **[Sample Apps](https://github.com/urbanairship/android-sample-apps)** - Example implementations

## Support

- 📚 [Documentation](https://docs.airship.com/)
- 🐛 [Report Issues](https://github.com/urbanairship/android-library/issues)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
