==================
First Things First
==================

This is an Android project. You will need:

- Java 1.6 (OSX will install it for you if you try to use java)
- ant
- Android Studio: http://developer.android.com/sdk/installing/studio.html

=====
Setup
=====

Clone the latest android library:

.. code:: bash
    git clone git@github.com:urbanairship/android-lib.git
    cd android-lib
    git submodule update --init --recursive

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

Required for library:
    <permission android:name="PACKAGE_NAME.permission.UA_DATA" android:protectionLevel="signature" />
    <uses-permission android:name="PACKAGE_NAME.permission.UA_DATA" />

    <provider android:name="com.urbanairship.UrbanAirshipProvider"
              android:authorities="PACKAGE_NAME.urbanairship.provider"
              android:permission="PACKAGE_NAME.permission.UA_DATA"
              android:exported="true"
              android:multiprocess="true" />

Required for Push:
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.VIBRATE"/>
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />

    <receiver android:name="com.urbanairship.CoreReceiver" />

    <receiver android:name="com.urbanairship.push.GCMPushReceiver"
              android:permission="com.google.android.c2dm.permission.SEND">
      <intent-filter>
          <action android:name="com.google.android.c2dm.intent.RECEIVE" />
          <action android:name="com.google.android.c2dm.intent.REGISTRATION" />
          <category android:name="PACKAGE_NAME" />
      </intent-filter>
    </receiver>

    <service android:name="com.urbanairship.push.PushService"/>

Required for RichPush:
    <uses-permission android:name="android.permission.INTERNET"/>
    <service android:name="com.urbanairship.richpush.RichPushUpdateService"/>

Required for location:
    <!-- Only one permission is required -->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <service android:name="com.urbanairship.location.LocationService"/>

Required for actions:
    <service android:name="com.urbanairship.actions.ActionService" />

    <!-- Needed for base action framework, but its currently an unused feature -->
    <activity android:name="com.urbanairship.actions.ActionActivity"/>

Required for landing page:
    <activity
        android:name="com.urbanairship.actions.LandingPageActivity"
        android:exported="false">
        <intent-filter>
            <action android:name="com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION"/>
            <data android:scheme="http" />
            <data android:scheme="https" />
            <category android:name="android.intent.category.DEFAULT"/>
        </intent-filter>
    </activity>



======================
Important Gradle Tasks
======================

build:
  Builds the Urban Airship SDK.

test:
  Run all of the library's unit tests.

deploy:
  Builds the SDK, updates the samples to the latest SDK, and creates release zips
  of the samples and library in the deploy directory.

docs:
 Builds the docs.  Generated docs will be created in docs/doclava-build.  For
 more information, see the README in the docs folder.

To run a gradle command, be in the root of the project folder and run:

.. code:: bash

    ./gradlew <TASK>

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





