# Android Urban Airship SDK

Urban Airship SDK for Android.

## Resources

- [Getting started guide](http://docs.urbanairship.com/platform/android/)
- [Javadocs](https://docs.urbanairship.com/android-lib/reference/packages.html)
- [Migration Guides](documentation/migration)

## Contributing Code

We accept pull requests! If you would like to submit a pull request, please fill out and submit a
Code Contribution Agreement (http://docs.urbanairship.com/contribution-agreement.html).

## Requirements
- minSdkVersion 15+
- compileSdkVersion 26
- Google Play Services 11.0.0+

## Quickstart

Include Urban Airship into the build.gradle file:

```
   dependencies {
     ...

     // Urban Airship SDK
     compile 'com.urbanairship.android:urbanairship-sdk:8.6.+'
     compile 'com.google.android.gms:play-services-gcm:11.0.1'

     // Recommended for in-app messaging
     compile 'com.android.support:cardview-v7:25.3.1'

     // Recommended for location services
     compile 'com.google.android.gms:play-services-location:11.0.1'
   }
```


Create a new `airshipconfig.properties` file with your application’s settings:

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

   # GCM Sender ID
   gcmSender = Your Google API Project Number

   # Notification customization
   notificationIcon = ic_notification
   notificationAccentColor = #ff0000

   # Optional - Set the default channel
   notificationChannel = "customChannel"
```

Set the Autopilot meta-data in the AndroidManifest.xml file:

```
      <meta-data android:name="com.urbanairship.autopilot"
               android:value="com.urbanairship.Autopilot"/>
```

## Sample Application

A [sample](sample) application is available that showcases the majority of the features offered by
the Urban Airship SDK. Before running the sample, copy the file in `sample/src/main/assets/airshipconfig.properites.sample` to
`sample/src/main/assets/airshipconfig.properties` and modify the properties to match your application's config.

## Sample Test
An automated test is available to test basic pushes, message center and in-app messages with the Sample application.

To run the test suite on an emulator or device with API 21+:

```
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.appKey="APP_KEY" -Pandroid.testInstrumentationRunnerArguments.masterSecret="MASTER_SECRET"
```
