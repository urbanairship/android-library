# Android Urban Airship SDK

Urban Airship SDK for Android.

## Resources

- [Getting started guide](http://docs.urbanairship.com/build/android.html)
- [Javadocs](https://docs.urbanairship.com/android-lib/reference/packages.html)
- [Migration guide](http://docs.urbanairship.com/topic_guides/android_migration.html)

## Contributing Code

We accept pull requests! If you would like to submit a pull request, please fill out and submit a
Code Contribution Agreement (http://docs.urbanairship.com/contribution-agreement.html).

## Quickstart

Include Urban Airship into the build.gradle file:

```
   repositories {
       ...

       maven {
           url  "https://urbanairship.bintray.com/android"
       }
   }

   dependencies {
     ...

     // Urban Airship SDK
     compile 'com.urbanairship.android:urbanairship-sdk:7.1.+'

     // Recommended for in-app messaging
     compile 'com.android.support:cardview-v7:23.3.0'

     // Recommended for location services
     compile 'com.google.android.gms:play-services-location:8.4.0'
   }
```

Verify the `applicationId` is set:

```
   android {
     ...

     defaultConfig {
       ...

       applicationId "com.example.application"
     }
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
```

Create a custom application to call takeOff in the application's `onCreate` method:

```
   public class CustomApplication extends Application {
       @Override
       public void onCreate() {
           super.onCreate();

           UAirship.takeOff(this);
       }
   }
```

Set the custom application in the AndroidManifest.xml file:

```
   <application android:name=".CustomApplication" ... />
```

## Sample Applications

The available [sample](sample) application showcases the majority of the features offered by
the Urban Airship SDK. The [sample-lib](sample-lib) shares the same source as the [sample](sample),
but uses the Urban Airship SDK from source instead of the prebuilt aar package from
[bintray](https://bintray.com/urbanairship/android/urbanairship-sdk/view).


