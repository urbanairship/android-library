# Airship Android SDK 16.x to 17.0 Migration Guide

## Compile and Target SDK Versions

Urban Airship now requires `compileSdk` version 33 (Android 13) or higher.

Please update the `build.gradle` file:

```groovy
android {
    compileSdkVersion 33

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 33

        // ...
    }
}
```

## Channel listener removed

The Channel class has changed, and the listener for updates is now removed :

```java
public void onChannelUpdated(@NonNull String channelId) {
            Log.i(TAG, "Channel updated " + channelId);
    }
```

You can instead use the PushNotificationStatusListener if you want to listen for push optin changes :

```kotlin
    val listener = = PushNotificationStatusListener {
            // Receive the new Push status
    }

    UAirship.shared().pushManager.addNotificationStatusListener(listener)
```

## Subscription lists 

Getting subscription lists has new methods :

```java
  // Deprecated
  contact.getSubscriptionLists()

  // Replacement
  contact.fetchSubscriptionListsPendingResult()
```

And a new method that uses a Kotlin *suspend* function

```kotlin
  // Deprecated
  contact.getSubscriptionLists()

  // Replacement
  scope.launch {
        val result = fetchSubscriptionLists().getOrNull()
    }
```

