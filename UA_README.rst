==================
First Things First
==================

This is an Android project. You will need:

- Java 1.7, Java 1.6
- Android Studio
- Android SDK

=====
Setup
=====

Clone the latest android library:
    git clone --recursive git@github.com:urbanairship/android-lib.git

Update Android Studio's Android SDK Manager:

 - Open Android Studio
 - From the welcome screen: configure -> SDK Manager
 - From the IDE: Tools -> Android -> SDK Manager
 - Install any updates
 - Under Extras, Install Android Support Repository and Google Repository.

 Import the project:

 - Open Android Studio to the welcome screen
 - Select Import Project, open the cloned library
 - Verify Use default gradle wrapper is selected

=======================
AndroidManifest Entries
=======================

Permissions:

    <!-- Common -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <permission android:name="${applicationId}.permission.UA_DATA" android:protectionLevel="signature" />
    <uses-permission android:name="${applicationId}.permission.UA_DATA" />

    <!-- Push -->
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- GCM -->
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
    <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />
    <permission android:name="${applicationId}.permission.C2D_MESSAGE" android:protectionLevel="signature" />
    <uses-permission android:name="${applicationId}.permission.C2D_MESSAGE" />

    <!-- ADM -->
    <uses-permission android:name="com.amazon.device.messaging.permission.RECEIVE" />
    <permission android:name="${applicationId}.permission.RECEIVE_ADM_MESSAGE" android:protectionLevel="signature" />
    <uses-permission android:name="${applicationId}.permission.RECEIVE_ADM_MESSAGE" />

    <!-- Location -->
    <!-- Use ACCESS_COARSE_LOCATION if GPS access is not necessary -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />


Activities:

    <!-- Common -->
    <activity android:name="com.urbanairship.CoreActivity"/>

    <!-- Landing Page -->
    <activity android:name="com.urbanairship.actions.LandingPageActivity"
        android:exported="false">

        <intent-filter>
            <action android:name="com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION" />
            <data android:scheme="http" />
            <data android:scheme="https" />
            <category android:name="android.intent.category.DEFAULT" />
        </intent-filter>
    </activity>

    <!-- PlayServiceUtils.handleAnyPlayServicesError to handle Google Play services recoverable errors. -->
    <activity android:name="com.urbanairship.google.PlayServicesErrorActivity"
        android:theme="@android:style/Theme.Translucent.NoTitleBar" />

Services:

    <!-- Push -->
    <service android:name="com.urbanairship.push.PushService" android:label="Push Notification Service" />

    <!-- Analytics -->
    <service android:name="com.urbanairship.analytics.EventService" android:label="Event Service" />

    <!-- Actions -->
    <service android:name="com.urbanairship.actions.ActionService" />

    <!-- Rich Push -->
    <service android:name="com.urbanairship.richpush.RichPushUpdateService" />

    <!-- Location -->
    <service android:name="com.urbanairship.location.LocationService" android:label="Segments Service" />

Receivers:

    <!-- Common -->
    <receiver android:name="com.urbanairship.CoreReceiver"
              android:exported="false">
        <intent-filter android:priority="-999">
            <action android:name="com.urbanairship.push.OPENED" />
            <category android:name="${applicationId}" />
        </intent-filter>
    </receiver>

    <!-- ADM -->
    <receiver android:name="com.urbanairship.push.ADMPushReceiver"
        android:permission="com.amazon.device.messaging.permission.SEND">

        <intent-filter>
            <action android:name="com.amazon.device.messaging.intent.REGISTRATION" />
            <action android:name="com.amazon.device.messaging.intent.RECEIVE" />
            <category android:name="${applicationId}" />
        </intent-filter>
    </receiver>

    <!-- GCM -->
    <receiver
        android:name="com.urbanairship.push.GCMPushReceiver"
        android:permission="com.google.android.c2dm.permission.SEND">
        <intent-filter>
            <action android:name="com.google.android.c2dm.intent.RECEIVE" />
            <action android:name="com.google.android.c2dm.intent.REGISTRATION" />
            <category android:name="${applicationId}" />
        </intent-filter>
    </receiver>

Providers:

    <!-- Common -->
    <provider android:name="com.urbanairship.UrbanAirshipProvider"
              android:authorities="${applicationId}.urbanairship.provider"
              android:permission="${applicationId}.permission.UA_DATA"
              android:exported="true"
              android:multiprocess="true" />

Other:

    <!-- ADM -->
    <amazon:enable-feature
        android:name="com.amazon.device.messaging"
        android:required="false" />

    <!-- GCM -->
    <meta-data
        android:name="com.google.android.gms.version"
        android:value="@integer/google_play_services_version" />

======================
Important Gradle Tasks
======================

build
  Builds the SDK and samples.

test:
  Runs all the unit tests.

urbanairship-sdk:javaDoc:
  Builds the docs. Generated docs will be created under urbanairship-sdk/build/docs/javadoc.

packageUrbanAirshipRelease
  Builds the distribution zip. The generated will be created under build/ua-package.

continuousIntegration
  Builds the SDK, samples, docs, generates the distribution zip, and runs all the unit tests.

bintrayUploadInternal
  Build the SDK and uploads the release to https://bintray.com/urbanairship/android-internal/urbanairship-sdk. Before
  you can upload, your bintray credentials must be defined in ~/.gradle/gradle.properties under "bintrayUser" and
  "bintrayApiKey".

bintrayUploadRelease
  Build the SDK and uploads the release to https://bintray.com/urbanairship/android/urbanairship-sdk. Before
  you can upload, your bintray credentials must be defined in ~/.gradle/gradle.properties under "bintrayUser" and
  "bintrayApiKey".


To run a gradle command, be in the root of the project folder and run: `./gradlew <TASK>`

==================
Command Line Tools
==================
There are some useful tools in the android-lib/tools folder:

- ``lc`` - a fancy color ``adb -v time`` wrapper
- ``lcgrep`` - a fancier lc + grep wrapper. you can pass it any grep arguments e.g., ``lcgrep -i pushsample``

=============
Eclipse Setup
=============

Eclipse currently does not support gradle, but you can still use Eclipse for samples and ui automator tests.  If you do
use Eclipse still, you need to make sure it uses the our style settings.  It is recommended to use the latest ADT bundle
that includes Eclipse with common Android SDK plugins: http://developer.android.com/sdk/installing/bundle.html

########
Settings
########

The ``android-lib/tools/settings`` folder contains Eclipse-specific settings. Import them to ensure that we're all using
the same formatting.

- ``save_settings.rst`` is the set of Save Actions your editor should support
- ``ua_android_eclipse_formatter.xml`` is the set of Code Formatting styles Eclipse should use. Install at
  ``Eclipse->Preferences->Java->Code Style->Formatter``
- ``ua_android_eclipse_import_order.importorder`` sets the preferred import order. We use the Android standard ordering.
  Install at ``Preferences->Java->Code Style->Organize Imports``





