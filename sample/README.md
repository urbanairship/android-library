# Urban Airship Sample

Sample application for the Urban Airship SDK.

## Setup

Copy [airshipconfig.properties.sample](src/main/assets/airshipconfig.properties.sample) to `airshipconfig.properites` in
the assets directory.

Update `airshipconfig.properties` with your application's config.

Configure a push provider:
- [Amazon setup docs](https://docs.urbanairship.com/platform/push-providers/adm/)
- [FCM setup docs](https://docs.urbanairship.com/platform/push-providers/fcm/)

Add a google-services.json file to your project. This can be done in one of two ways:
- Use the [Firebase Assistant](https://developer.android.com/studio/write/firebase.html) tool in Android Studio
- [Download the google-services.json file](https://support.google.com/firebase/answer/7015592)) from the [Firebase Console](https://console.firebase.google.com) and then copy it into your project's module folder, typically `app/`

