# Urban Airship Android SDK Migration Guide

## UrbanAirship Library 9.0.x to 9.1.x

### FCM Migration

For FCM migration, please follow the [FCM Migration Guide](https://github.com/urbanairship/android-library/tree/master/documentation/migration/migration-guide-fcm.md).


## UrbanAirship Library 8.x to 9.0.0

### GCM

All of the GCM classes have been moved into the package `com.urbanairship.push.gcm`. If
you have manually added the `GcmPushReceiver` or `UAInstanceIDListenerService` in your
manifest, please update the entries.

### ADM

All of the ADM classes have been moved into the package `com.urbanairship.push.adm`. If
you have manually added the `AdmPushReceiver` in your manifest, please update the entry.

### In-App Messaging

Urban Airship's banner-only In-App Messaging feature has been replaced with a more
functional In-App Messaging feature that includes banner, modal and full screen
messages. Please refer to [In-App Messaging for Android](https://docs.urbanairship.com/guides/android-in-app-messaging)
for more information.

### Automation

The automation for managing schedules has been replaced with a simplified version.
The sync and async APIs have been unified into a single api that returns either
a pending result or a future. The pending result implements the Future interface,
so it can be used to wait for the result making the API synchronous, and for
the asynchronous API, the pending result is able to add a result callback to be notified
when the result is ready.

> Scheduling in 8.x :

```java

// Async
UAirship.shared().getAutomation().scheduleAsync(actionSchedule, new PendingResult.ResultCallback<ActionSchedule>() {
    @Override
    public void onResult(@Nullable ActionSchedule result) {
        // Handle result
    }
});

// Sync
UAirship.shared().getAutomation().schedule(actionSchedule);

```

> New 9.0 way of scheduling:

```java
// Add a callback when finished
PendingResult<ActionSchedule> pendingResult = UAirship.shared().getAutomation().schedule(actionSchedule);

 // Add a callback when finished
 pendingResult.addResultCallback(new ResultCallback<ActionSchedule>() {
     @Override
     public void onResult(@Nullable ActionSchedule result) {
       // Handle result
     }
 });

 // Alternatively, wait for the result
 ActionSchedule result = pendingResult.get();
```

***See [legacy migration guide](migration-guide-legacy.md) for older migrations***
