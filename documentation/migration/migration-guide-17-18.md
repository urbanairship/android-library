# Airship Android SDK 17.x to 18.0 Migration Guide

## Compile and Target SDK Versions

Urban Airship now requires `compileSdk` version 34 (Android 14) or higher.

Please update the `build.gradle` file:

###### Groovy
```groovy
android {
    compileSdkVersion 34

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 34

        // ...
    }
}
```

## Removed modules

### `urbanairship-preference`

The Preference module is no longer supported and has been removed from the SDK.
Apps should migrate to the `urbanairship-preference-center` module, where appropriate,
or maintain a copy of the preference UI controls from the [preference module sources](https://github.com/urbanairship/android-library-dev/tree/bf43533eb106d7c2570ccf808e0ff057776bae2f/urbanairship-preference/src/main/java/com/urbanairship/preference).

### `urbanairship-ads-identifier`

The Ads Identifier module is no longer supported and has been removed from the SDK.
Apps that were making use of this module should migrate to using the `AssociatedIdentifiers.Editor` directly.

###### Kotlin
```kotlin
// Call from Autopilot onAirshipReady, or in Application onCreate.
fun updateAdvertisingId(context: Context) {
    try {
        val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context) ?: return
        val advertisingId = adInfo.id ?: return
        val limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled ?: return

        UAirship.shared().analytics.editAssociatedIdentifiers()
            .setAdvertisingId(advertisingId, limitedAdTrackingEnabled)
            .apply()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to retrieve and update advertising ID!", e)
    }
}
```

###### Java
```java
// Call from Autopilot onAirshipReady, or in Application onCreate.
public void updateAdvertisingId(@NonNull Context context){
    try {
        AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
        if (adInfo == null) {
            return;
        }
        
        advertisingId = adInfo.getId();
        limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled();
        if (advertisingId != null && limitedAdTrackingEnabled != null) {
            UAirship.shared().getAnalytics().editAssociatedIdentifiers()
                .setAdvertisingId(advertisingId, limitedAdTrackingEnabled)
                .apply();
        }
    } catch (IOException | GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
        Log.e(TAG, "Failed to retrieve and update advertising ID!", e);
    }
}
```

## Migration to non-transitive R classes

The Airship SDK is now using non-transitive R classes, which helps prevent resource duplication by ensuring that each module's R class only includes resources for the module, without merging references from the module's dependencies. This can lead to faster build times and is the new default behavior in Android Gradle plugin 8.0.0 and newer.

###### 17.x imports
```kotlin
import com.urbanairship.R
```

###### 18.x imports
```kotlin
// Core resources (unchanged):
import com.urbanairship.R

// Automation resources:
import com.urbanairship.automation.R

// Message Center resources:
import com.urbanairship.messagecenter.R

// Preference Center resources:
import com.urbanairship.preferencecenter.R
```
