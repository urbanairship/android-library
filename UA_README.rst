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

======================
Important Gradle Tasks
======================

build
  Builds the SDK and samples.

test:
  Runs all the unit tests.

urbanairship-sdk:javaDoc:
  Builds the docs. Generated docs will be created under urbanairship-sdk/build/docs/javadoc.

continuousIntegration
  Builds the SDK, samples, docs, and runs all the unit tests.

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
