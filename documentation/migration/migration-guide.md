# Urban Airship Android SDK Migration Guide

***See [legacy migration guide](migration-guide-legacy.md) for older migrations***

## UrbanAirship Library 9.x to 10.0.0

### In App Automation/Message changes

Several updates have been made to the In App automation frameworks. Most apps will
not be affected by these changes, only those that have implemented custom display
logic.

#### DisplayHandler

The `requestDisplayLock` method has been renamed to `isDisplayAllowed`:

```java
    // 9.x
   public boolean requestDisplayLock(@NonNull Activity activity)

    // 10.x
   public boolean isDisplayAllowed(@NonNull Activity activity)
```

#### InAppMessageAdapter

The adapter's `onDisplay` no longer returns a boolean. Any checks that might prevent
the in-app message from displaying should be moved into `isReady`.

```java
    // 9.x
   public boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, @NonNull DisplayHandler displayHandler);

    // 10.x
   public void onDisplay(@NonNull Activity activity, boolean isRedisplay, @NonNull DisplayHandler displayHandler);
```


### Packages Removed

Both `urbanairship-gcm` and `urbanairship-sdk` have been removed. Please
migrate to `urbanairship-fcm` and or `urbanairship-adm` instead. For GCM to FCM
migration, please follow the [FCM Migration Guide](https://github.com/urbanairship/android-library/tree/master/documentation/migration/migration-guide-fcm.md).

### Auto tracking Advertising ID

Advertising ID auto tracking has been removed from the core library. A new package
`urbanairship-ad-identifier` is available to enable this behavior:

```java
    AdvertisingIdTracker.shared(context).setEnabled(enabled);
```

Alternatively you can gather the Advertising ID manually and notify Urban Airship
by editing the associated identifiers:

```java
    UAirship.shared().getAnalytics()
                    .editAssociatedIdentifiers()
                    .setAdvertisingId(advertisingId, isLimitedTrackingEnabled)
                    .apply();
```


### Kotlin Interop

To better support Kotlin interop, all public APIs now have nullability annotations and a few
methods had to be changed to move the lambda to the last parameter.

#### UAirship

```java
    // 9.x
    public static Cancelable shared(final OnReadyCallback callback, Looper looper)

    // 10.x
    public static Cancelable shared(@Nullable Looper looper, @NonNull final OnReadyCallback)
```

#### ActionRegistry#Entry

```java
    // 9.x
    public void addSituationOverride(@NonNull Action action, @Action.Situation int situation)

    // 10.x
    public void setSituationOverride(@Action.Situation int situation, @Nullable Action action)
```

#### ActionRunner#ActionRunRequest

```java
    // 9.x
    public void run(final ActionCompletionCallback callback, Looper looper)

    // 10.x
    public void run(@Nullable Looper looper, @Nullable final ActionCompletionCallback callback)
```

#### RichPushInbox

```java
    // 9.x
    public Cancelable fetchMessages(final FetchMessagesCallback callback, Looper looper)

    // 10.x
    public Cancelable fetchMessages(@Nullable Looper looper, @NonNull FetchMessagesCallback callback)
```

### From Json method normalization

All from/parse JSON methods have been updated to have a consistent signature.

#### ActionScheduleInfo

```java
    // 9.x
    public static ActionScheduleInfo parseJson(@NonNull JsonValue value) throws JsonException

    // 10.x
    public static ActionScheduleInfo fromJson(@NonNull JsonValue value) throws JsonException
```

#### ScheduleDelay

```java
    // 9.x
    public static ScheduleDelay parseJson(@NonNull JsonValue value) throws JsonException

    // 10.x
    public static ScheduleDelay fromJson(@NonNull JsonValue value) throws JsonException
```

#### Audience

```java
    // 9.x
    public static Audience parseJson(@NonNull JsonValue jsonValue) throws JsonException

    // 10.x
    public static Audience fromJson(@NonNull JsonValue value) throws JsonException
```

#### ButtonInfo

```java
    // 9.x
    public static ButtonInfo parseJson(@NonNull JsonValue jsonValue) throws JsonException

    public static List<ButtonInfo> parseJson(@NonNull JsonList jsonList) throws JsonException

    // 10.x
    public static ButtonInfo fromJson(@NonNull JsonValue value) throws JsonException

    public static List<ButtonInfo> fromJson(@NonNull JsonList jsonList) throws JsonException
```

#### MediaInfo

```java
    // 9.x
    public static MediaInfo parseJson(@NonNull JsonValue jsonValue) throws JsonException

    // 10.x
    public static MediaInfo fromJson(@NonNull JsonValue value) throws JsonException
```

#### TagSelector

```java
    // 9.x
    public static TagSelector parseJson(@NonNull JsonValue jsonValue) throws JsonException

    // 10.x
    public static TagSelector fromJson(@NonNull JsonValue value) throws JsonException
```

#### TextInfo

```java
    // 9.x
    public static TextInfo parseJson(@NonNull JsonValue jsonValue) throws JsonException

    // 10.x
    public static TextInfo fromJson(@NonNull JsonValue value) throws JsonException
```

#### BannerDisplayContent

```java
    // 9.x
    public static BannerDisplayContent parseJson(@NonNull JsonValue json) throws JsonException

    // 10.x
    public static BannerDisplayContent fromJson(@NonNull JsonValue value) throws JsonException
```

#### CustomDisplayContent

```java
    // 9.x
    public static CustomDisplayContent parseJson(@NonNull JsonValue jsonValue)

    // 10.x
    public static CustomDisplayContent fromJson(@NonNull JsonValue value) throws JsonException
```

#### FullScreenDisplayContent

```java
    // 9.x
    public static FullScreenDisplayContent parseJson(@NonNull JsonValue json) throws JsonException

    // 10.x
    public static FullScreenDisplayContent fromJson(@NonNull JsonValue value) throws JsonException
```

#### HtmlDisplayContent

```java
    // 9.x
    public static HtmlDisplayContent parseJson(@NonNull JsonValue json) throws JsonException

    // 10.x
    public static HtmlDisplayContent fromJson(@NonNull JsonValue value) throws JsonException
```

#### ModalDisplayContent

```java
    // 9.x
    public static ModalDisplayContent parseJson(@NonNull JsonValue json) throws JsonException

    // 10.x
    public static ModalDisplayContent fromJson(@NonNull JsonValue value) throws JsonException
```

#### LocationRequestOptions

```java
    // 9.x
    public static LocationRequestOptions parseJson(@Nullable String json) throws JsonException

    // 10.x
    public static LocationRequestOptions fromJson(@Nullable JsonValue value) throws JsonException
```

#### QuietTimeInterval

```java
    // 9.x
    public static QuietTimeInterval parseJson(@Nullable String json)

    // 10.x
    public static QuietTimeInterval fromJson(@Nullable JsonValue value) throws JsonException
```

#### DisableInfo

```java
    // 9.x
    public static DisableInfo parseJson(@NonNull JsonValue jsonValue) throws JsonException

    // 10.x
    public static DisableInfo fromJson(@NonNull JsonValue value) throws JsonException
```


### Removed Methods


#### ActionRegistry

Removed:
```java
public Entry registerAction(@NonNull Class<? extends Action> c, Predicate predicate, @NonNull String... names)
```

Instead, you can set the predicate after registering the action:

```java
    UAirship.shared().getActionRegistry()
            .registerAction(SomeAction.class, "some-action-name")
            .setPredicate(predicate);
```

### Renamed Classes

BitmapUtils is now ImageUtils

### FCM provider changes

`AirshipFirebaseInstanceIdService` was removed to avoid using the deprecated `FirebaseInstanceIdService`. To notify Urban Airship of token changes, please use the new `AirshipFirebaseMessagingService.processNewToken(Context)` method.
