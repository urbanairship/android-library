# Airship Android SDK Migration Guide

## UrbanAirship Library 12.x to 13.0.0

### Package Changes

`urbanairship-core` has been broken into separate feature modules:
- `urbanairship-automation`: Contains In-App Automation (InAppMessageManager, LegacyInAppMessageManager), Landing Pages, and Action Automation.
- `urbanairship-message-center`: Contains all Message Center and Inbox APIs
- `urbanairship-location` Contains location APIs.

In order to continue using Message Center, In-app Automation, or location, you will need to include the dependencies in the app's build.gradle file:

```
implementation "com.urbanairship.android:urbanairship-fcm:$airshipVersion"

implementation "com.urbanairship.android:urbanairship-message-center:$airshipVersion"
implementation "com.urbanairship.android:urbanairship-automation:$airshipVersion"
```

### UAWebView, UAWebViewClient, and UAWebViewChromeClient Removal

To support the modular SDK, `com.urbanairship.widget.UAWebView` has been removed and replaced with `com.urbanairship.webkit.AirshipWebView` and `com.urbanairship.messagecenter.webkit.MessageWebView`. Similarly,  `com.urbanairship.widget.UAWebViewClient` has been removed and replaced with `com.urbanairship.webkit.AirshipWebViewClient` and `com.urbanairship.messagecenter.webkit.MessageWebViewClient`. The MessageWebView/Client should be used for Message Center implementations.

To match new classes, `com.urbanairship.widget.UAWebViewChromeClient` has been moved to `com.urbanairship.webkit.AirshipWebViewChromeClient`.

### Message Center Changes

#### Accessor changes

The accessor for the Message Center singleton has been changed to
`MessageCenter#shared()`. The accessor for the inbox has been moved the
the MessageCenter instance `MessageCenter#getInbox()`.

```
// Removed UAirship.shared().getMessageCenter()
MessageCenter.shared();

// Removed UAirship.shared().getInbox()
MessageCenter.shared().getInbox();
```

#### Class changes

The following classes have been moved or renamed in order to support the modularized SDK and
to align class names with actual feature name (RichPush -> MessageCenter):

- `com.urbanairship.richpush.RichPushInbox` -> `com.urbanairship.messagecenter.Inbox`
- `com.urbanairship.richpush.RichPushUser` -> `com.urbanairship.messagecenter.User`
- `com.urbanairship.richpush.RichPushMessage` -> `com.urbanairship.messagecenter.Message`
- `com.urbanairship.richpush.RichPushInbox#Listener` -> `com.urbanairship.messagecenter.InboxListener`
- `com.urbanairship.actions.OpenRichPushInboxAction` -> `com.urbanairship.messagecenter.actions.MessageCenterAction`

#### Message Predicate Removal

The predicate `com.urbanairship.richpush.RichPushInbox#Predicate` has been removed and
replaced with the generic `com.urbanairship.Predicate` class.

#### MessageWebView & MessageWebViewClient

`MessageFragment` now expects a `MessageWebView` instead of a `UAWebView` and a proper JavaScript bridge for the `MessageWebViewClient` to be attached to the `MessageWebView` instead of the `UAWebViewClient`.

#### OverlayRichPushMessageAction Removal

The `OverlayRichPushMessageActionTest` was removed, instead messages will be displayed in the new `MessageCenterAction` action.

#### Deprecations Removed

The following deprecations have been removed:

- `RichPushInbox#startInboxActivity()`, use `MessageCenter#showMessageCenter()` instead.
- `RichPushInbox.VIEW_INBOX_INTENT_ACTION`, use `MessageCenter.VIEW_MESSAGE_CENTER_INTENT_ACTION` instead.
- `RichPushInbox.VIEW_MESSAGE_INTENT_ACTION`, use `MessageCenter.VIEW_MESSAGE_INTENT_ACTION` instead.
- `RichPushInbox.MESSAGE_DATA_SCHEME`, use `MessageCenter.MESSAGE_DATA_SCHEME` instead.

### In App Automation Changes

The accessor for In-App Automation (LegacyInAppMessageManager & InAppMessageManager) has been removed from `UAirship`.
Instead use the `shared()` class methods on each manager:

```
// Removed UAirship.shared().getInAppMessageManager()
InAppMessageManager.shared();

// Removed UAirship.shared().getLegacyInAppMessageManager()
LegacyInAppMessageManager.shared();
```

### Action Automation Changes

The `Automation` class has been renamed to `ActionAutomation` and the singleton accessor has been removed
from `UAirship#getAutomation()`. Instead use the `ActionAutomation#shared()` method:

```
// Removed UAirship.shared().getAutomation()
ActionAutomation.shared();
```

#### Class Changes

The following classes have been moved or renamed in order to support the modularized SDK:

- `com.urbanairship.automation.Automation` -> `com.urbanairship.automation.ActionAutomation`
- `com.urbanairship.actions.ScheduleAction` -> `com.urbanairship.automation.actions.ScheduleAction`
- `com.urbanairship.actions.CancelSchedulesAction` -> `com.urbanairship.automation.actions.CancelSchedulesAction`

### Location Changes

The `UALocationManager` class has been renamed to `AirshipLocationManager` and the singleton accessor has been removed
from `UAirship#getLocationManager()`. Instead use the `AirshipLocationManager#shared()` method:

```
// Removed UAirship.shared().getLocationManager()
AirshipLocationManager.shared();
```

#### Class Changes

The following classes have been moved or renamed in order to support the modularized SDK:

- `com.urbanairship.location.UALocationManager` -> `com.urbanairship.location.AirshipLocationManager`
- `com.urbanairship.location.RegionEvent` -> `com.urbanairship.analytics.location.RegionEvent`
- `com.urbanairship.location.CircularRegion` -> `com.urbanairship.analytics.location.CircularRegion`
- `com.urbanairship.location.ProximityRegion` -> `com.urbanairship.analytics.location.ProximityRegion`
- `com.urbanairship.analytics.LocationEvent` -> `com.urbanairship.analytics.location.LocationEvent`

### Landing Page Changes

The Landing Page Action has been moved from `com.urbanairship.actions.LandingPageAction` to  `com.urbanairship.iam.actions.LandingPageAction` and is now part of the `urbanairship-automation` package.

To continue to use Landing Pages, you will need to add the automation dependency:

```
implementation "com.urbanairship.android:urbanairship-automation:$airshipVersion"
```

### Analytic Changes

The `recordLocation` methods have been removed from Analytics. To record locations without the `AirshipLocationManager`, create
a `LocationEvent` directly and report it with `Analytics#addEvent(Event)`.
