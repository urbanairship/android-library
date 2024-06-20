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

## Privacy Manager

### Feature Constants

Integer constants for features have been moved to a class, `PrivacyManager.Feature`.

| 17.x                                         | 18.x                                         |
|----------------------------------------------|----------------------------------------------|
| `PrivacyManager.FEATURE_ALL`                 | `PrivacyManager.Feature.ALL`                 |
| `PrivacyManager.FEATURE_NONE`                | `PrivacyManager.Feature.NONE`                |
| `PrivacyManager.FEATURE_MESSAGE_CENTER`      | `PrivacyManager.Feature.MESSAGE_CENTER`      |
| `PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES` | `PrivacyManager.Feature.TAGS_AND_ATTRIBUTES` |
| `PrivacyManager.FEATURE_IN_APP_AUTOMATION`   | `PrivacyManager.Feature.IN_APP_AUTOMATION`   |
| `PrivacyManager.FEATURE_CONTACTS`            | `PrivacyManager.Feature.CONTACTS`            |
| `PrivacyManager.FEATURE_ANALYTICS`           | `PrivacyManager.Feature.ANALYTICS`           |
| `PrivacyManager.FEATURE_PUSH`                | `PrivacyManager.Feature.PUSH`                |

### Methods

Methods on `PrivacyManager` that previously accepted an integer feature mask now accept a `PrivacyManager.Feature` object.

| 17.x                                                  | 18.x                                                  |
|-------------------------------------------------------|-------------------------------------------------------|
| `disable(FEATURE_PUSH)`                               | `disable(Feature.PUSH)`                               |
| `enable(FEATURE_PUSH)`                                | `enable(Feature.PUSH)`                                |
| `isEnabled(FEATURE_PUSH)`                             | `isEnabled(Feature.PUSH)`                             |
| `isAnyEnabled(FEATURE_PUSH, FEATURE_ANALYTICS)`       | `isAnyEnabled(Feature.PUSH, Feature.ANALYTICS)`       |
| `setEnabledFeatures(FEATURE_PUSH, FEATURE_ANALYTICS)` | `setEnabledFeatures(Feature.PUSH, Feature.ANALYTICS)` |

A new value, `PrivacyManager.Feature.FEATURE_FLAGS`, has also been added to control the enabled state of Airship Feature Flags.

## Contact

### Conflict Events

The signature of `ConflictEvent.associatedChannels` was changed from `List<AssociatedChannel>` to `List<ConflictEvent.ChannelInfo>` and the `AssociatedChannel` class has been removed.
Apps that have implemented a custom `ContactConflictListener` may need to make minor adjustments to update to the new type. The `ChannelInfo` class provides the same fields as the removed `AssociatedChannel` class.

## Image Loading

The ability to provide a custom image loader via `UAirship.setImageLoader(...)` has been removed. The SDK now uses Glide internally to handle image loading.
Additionally, the `ImageLoader` and `ImageRequestOptions` classes has been removed. Apps that were using these class should migrate to using another image loading library, like Coil or Glide, directly.

## Automation

The `urbanairship-automation` module has been rewritten in Kotlin and now uses Kotlin coroutines. For most apps, this will be a trivial update, but if your app uses custom display adapters,
this update maybe more extensive. See below for more info about custom display adapter migration.

### Accessors

The accessors for InAppMessaging and LegacyInAppMessaging have moved.

| 17.x                                           | 18.x                                            |
|------------------------------------------------|-------------------------------------------------|
| `InAppAutomation.shared().inAppMessageManager` | `InAppAutomation.shared().inAppMessaging`       |
| `LegacyInAppMessageManager.shared()`           | `InAppAutomation.shared().legacyInAppMessaging` |

### Cache Management

The `PrepareAssetsDelegate`, `CachePolicyDelegate`, and `AssetManager` classes have been removed and are no longer available to extend. These APIs were difficult to use and often times lead to unintended consequences. The Airship SDK will now manage its own assets. External assets required by the App that need to be fetched before hand should happen outside of Airship. If assets are needed and can be fetched at display time, use the `isReady` Flow in a custom implementation of `CustomDisplayAdapter.SuspendingAdapter` to indicate when the adapter has finished fetching assets and is ready to display by emitting a `true` value.

### Display Coordinators

Display coordinators was another difficult to use API that has been removed. Instead, use the `InAppMessageDisplayDelegate.isMessageReadyToDisplay(message, scheduleId)` method to prevent messages from displaying, and `InAppAutomation.shared().inAppMessaging.notifyDisplayConditionsChanged()` to notify when the message should be tried again. If a use case is not able to be solved with the replacement methods, please file a GitHub issue with your use case.

### Extending Messages

InAppMessages are no longer extendable when displaying. If this is needed in your application, please file a GitHub issue with your use case.

### Custom Display Adapters

The `InAppMessageAdapter` interface has been replaced with a sealed interface, `CustomDisplayAdapter`, that contains `SuspendingAdapter` and `CallbackAdapter` subtypes. The new interface provides roughly the same functionality as before just with a different structure.

| 17.x `InAppMessageAdapter`           | 18.x `CustomDisplayAdapter`                                                                    |
|--------------------------------------|------------------------------------------------------------------------------------------------|
| `Factory.createAdapter(message)`     | No mapping. A factory is no longer required.                                                   |
| `onDisplay(context, displayHandler)` | `SuspendingAdapter.display(context)` or `CallbackAdapter.display(context, callback)`           |
| `onPrepare(context, assets)`         | Use the `isReady` Flow on `SuspendingAdapter` to indicate when the adapter is ready to display |
| `isReady(context)`                   | Use the `SuspendingAdapter.isReady` Flow                                                       |

#### Custom display adapter example:

```kotlin
class MyCustomDisplayAdapter(
    private val context: Context,
    private val message: InAppMessage,
    private val assets: AirshipCachedAssets
) : CustomDisplayAdapter.SuspendingAdapter {

    // Implement the isReady property, which can be used to signal when the adapter is ready to display the message.
    // If this adapter does not need to wait for anything before displaying the message,
    // you can return a StateFlow with an initial value of true to indicate that it is always ready.
    override val isReady: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun display(context: Context): CustomDisplayResolution {
        
        // Display the message...

        // Return a result after the message has been displayed.
        return CustomDisplayResolution.UserDismissed
    }

    companion object {
        fun register() {
            InAppAutomation.shared().inAppMessaging.setAdapterFactoryBlock(
                type = CustomDisplayAdapterType.BANNER,
                factoryBlock = { context, message, assets ->
                    MyCustomDisplayAdapter(context, message, assets)
                }
            )
        }
    }
}
```

Then, register the custom display adapter in your `Autopilot` implementation:

```kotlin
class MyAutopilot : Autopilot() {
    override fun onAirshipReady(airship: UAirship) {
        // Other Airship setup...

        // Register the custom display adapter
        MyCustomDisplayAdapter.register()
    }
}
```

## Live Update

### Changes to Live Update Handlers

The `LiveUpdateNotificationHandler` or `LiveUpdateCustomHandler` classes that were previously marked
as deprecated have been removed. The new handler interfaces / abstract classes are:

* `CallbackLiveUpdateNotificationHandler`
* `SuspendLiveUpdateNotificationHandler`
* `CallbackLiveUpdateCustomHandler`
* `SuspendLiveUpdateCustomHandler`

Apps that were using `LiveUpdateNotificationHandler` or `LiveUpdateCustomHandler` should update to
the suspend or callback versions, as appropriate.