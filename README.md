# Android Airship SDK

Airship SDK for Android.

## Resources

- [Getting started guide](http://docs.airship.com/platform/android/)
- [Javadocs](https://docs.airship.com/reference/libraries/android/latest/reference/packages.html)
- [Migration Guides](documentation/migration)

## Requirements
- minSdkVersion 16
- compileSdkVersion 28

## Quickstart

1) Include Airship into the build.gradle file:

```
   dependencies {
     ...

     // Airship SDK - FCM
     implementation 'com.urbanairship.android:urbanairship-fcm:10.0.1'
   }
```

2) [Add Firebase to your app](https://firebase.google.com/docs/android/setup#add_firebase_to_your_app).

3) Create a new `airshipconfig.properties` file with your application’s settings:

```
   developmentAppKey = Your Development App Key
   developmentAppSecret = Your Development App Secret

   productionAppKey = Your Production App Key
   productionAppSecret = Your Production Secret

   # Toggles between the development and production app credentials
   # Before submitting your application to an app store set to true
   inProduction = false

   # LogLevel is "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR" or "ASSERT"
   developmentLogLevel = DEBUG
   productionLogLevel = ERROR

   # Notification customization
   notificationIcon = ic_notification
   notificationAccentColor = #ff0000

   # Optional - Set the default channel
   notificationChannel = "customChannel"
```

4) Set the Autopilot meta-data in the AndroidManifest.xml file:

```
      <meta-data android:name="com.urbanairship.autopilot"
               android:value="com.urbanairship.Autopilot"/>
```

## Sample Application

A [sample](sample) application is available that showcases the majority of the features offered by
the Airship SDK. Before running the sample, copy the file in `sample/src/main/assets/airshipconfig.properties.sample` to
`sample/src/main/assets/airshipconfig.properties` and modify the properties to match your application's config.

## Sample Test
An automated test is available to test basic pushes, message center and in-app messages with the Sample application.

To run the test suite on an emulator or device with API 21+:

```
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.appKey="APP_KEY" -Pandroid.testInstrumentationRunnerArguments.masterSecret="MASTER_SECRET"
```
