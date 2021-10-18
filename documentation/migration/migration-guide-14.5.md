# Airship Android SDK 14.5 Migration Guide

Airship SDK 14.5 is a minor update that changes how the SDK handles data collection by introducing the
privacy manager. Privacy manager allows fine-grained control over what data is allowed to be collected
or accessed by the Airship SDK. In addition to better control, if all features are disabled in the privacy
manager, the SDK will no-op.

The privacy manager can be accessed from the shared `UAirship` instance:

```java
  PrivacyManager privacyManager = UAirship.shared().getPrivacyManager();
```

## Privacy Manager API

#### `public void setEnabledFeatures(@Feature int... features)`

Enables the given features, replacing the set of currently enabled features with the new set that was passed in. Any features that were enabled before the call to `setEnabledFeatures(...)` will no longer be enabled. This method is effectively the same as calling `disable(FEATURE_ALL)`, followed by `enable(...)`.

#### `public void enable(@Feature int... features)`

Enables the given features, adding them to the set of currently enabled features. Any features that were enabled prior to calling `enable(...)` will remain enabled.

#### `public void disable(@Feature int... features)`

Disables the given features, removing them from the set of currently enabled features. Any features that were previously enabled and not passed to `disable(...)` will remain enabled.

#### `public int getEnabledFeatures()`

Returns the current set of enabled features.

#### `public boolean isEnabled(@Features int... features)`

Returns true if *all* of the given features are currently enabled.

#### `public boolean isAnyEnabled(@Features int... features)`

Returns true if *any* of the given features are currently enabled.

## Enabling & Disabling Data Collection

To enable data collection set enabled features to `FEATURE_ALL`:

```java
  // Deprecated
  UAirship.shared().setDataCollectionEnabled(true);

  // Replacement
  UAirship.shared().getPrivacyManager().setEnabledFeatures(PrivacyManager.FEATURE_ALL);
```

To disable data collection set enabled features to `FEATURE_NONE`:

```java
  // Deprecated
  UAirship.shared().setDataCollectionEnabled(false);

  // Replacement
  UAirship.shared().getPrivacyManager().setEnabledFeatures(PrivacyManager.FEATURE_NONE);
```

The behavior prior to SDK 14.5 would still allow broadcasts in In-App Automation and Message Center.
To keep that behavior, set the enabled features to `FEATURE_MESSAGE_CENTER` and `FEATURE_IN_APP_AUTOMATION` instead of `FEATURE_NONE`:

```java
  UAirship.shared().getPrivacyManager().setEnabledFeatures(PrivacyManager.FEATURE_MESSAGE_CENTER, PrivacyManager.FEATURE_IN_APP_AUTOMATION);
```

## Enabling Data Collection Opt-In

The flag `dataCollectionOptInEnabled` is deprecated and replaced with `enabledFeatures`.
To start the SDK in a fully opted out state, set `enabledFeatures = none` in the `airshipconfig.properties`
file or directly on the config object:

```java
AirshipConfigOptions options = new AirshipConfigOptions.Builder()
        ...
        .setEnabledFeatures(PrivacyManager.FEATURE_NONE)
        .build();
```

This will start the SDK in a completely opted out state.

## Enabling & Disabling Push Token Registration

When data collection was disabled, it was still possible to allow push registration with a separate
flag. With privacy manager, you can now just specify `FEATURE_PUSH` to continue to allow token
registration.

Enabling:

```java
  // Deprecated
  UAirship.shared().getPushManager().setPushTokenRegistrationEnabled(true);

  // Replacement
  UAirship.shared().getPrivacyManager().enable(PrivacyManager.FEATURE_PUSH);
```

Disabling:

```java
  // Deprecated
  UAirship.shared().getPushManager().setPushTokenRegistrationEnabled(false);

  // Replacement
  UAirship.shared().getPrivacyManager().disable(PrivacyManager.FEATURE_PUSH);
```

Checking if enabled:

```java
  // Deprecated
  UAirship.shared().getPushManager().isPushTokenRegistrationEnabled();

  // Replacement
  UAirship.shared().getPrivacyManager().isEnabled(PrivacyManager.FEATURE_PUSH);
```

## Enabling & Disabling Push

In addition to data collection and token registration, push could be enabled/disabling directly with
`setPushEnabled`. In SDK 14.5, this is also controlled with `FEATURE_PUSH` on the privacy manager.

Enabling:

```java
  // Deprecated
  UAirship.shared().getPushManager().setPushEnabled(true);

  // Replacement
  UAirship.shared().getPrivacyManager().enable(PrivacyManager.FEATURE_PUSH);
```

Disabling:

```java
  // Deprecated
  UAirship.shared().getPushManager().setPushEnabled(false);

  // Replacement
  UAirship.shared().getPrivacyManager().disable(PrivacyManager.FEATURE_PUSH);
```

Checking if enabled:

```java
  // Deprecated
  UAirship.shared().getPushManager().isPushEnabled();

  // Replacement
  UAirship.shared().getPrivacyManager().isEnabled(PrivacyManager.FEATURE_PUSH);
```

# Enabling Analytics

Analytics had additional enable flag that is now deprecated and replaced with `FEATURE_ANALYTICS`
on the privacy manager.

Enabling:

```java
  // Deprecated
  UAirship.shared().getAnalytics().setEnabled(true);

  // Replacement
  UAirship.shared().getPrivacyManager().enable(PrivacyManager.FEATURE_ANALYTICS);
```

Disabling:

```java
  // Deprecated
  UAirship.shared().getAnalytics().setEnabled(false);

  // Replacement
  UAirship.shared().getPrivacyManager().disable(PrivacyManager.FEATURE_ANALYTICS);
```

Checking if enabled:

```java
  // Deprecated
  UAirship.shared().getAnalytics().isEnabled();

  // Replacement
  UAirship.shared().getPrivacyManager().isEnabled(PrivacyManager.FEATURE_ANALYTICS);
```

Analytics can still be completely disabled through AirshipConfig. If disabled through config, `isEnabled()` will always return `false` regardless of privacy manager settings.
