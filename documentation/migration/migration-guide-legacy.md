# Urban Airship Android SDK Migration Guide (Legacy)

***See [current migration guide](migration-guide.md) for more recent migrations***

# UrbanAirship Library 6.4.x to 7.0.x

## AirshipConfigOptions

[AirshipConfigOptions](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/AirshipConfigOptions.html) is no longer mutable after being created. All the public fields are now final and can only be
set using the new `AirshipConfigOptions.Builder` class. This only affects classes that programmatically set the config.

```java
// Old
AirshipConfigOptions options = new AirshipConfigOptions();
options.inProduction = false;
options.developmentAppKey = "Your Development App Key";
options.developmentAppSecret "Your Development App Secret";

// New
AirshipConfigOptions options = new AirshipConfigOptions.Builder()
        .setInProduction(false)
        .setDevelopmentAppkey("Your Development App Key")
        .setDevelopmentAppSecret("Your Development App Secret")
        .build();
```

## Message Center

A Message Center is now included with the SDK. Applications that have built a custom
Message Center are still supported, but need to implement support for [Displaying the Message Center](https://docs.urbanairship.com/platform/android.md#android-rich-push-inbox-intents)
to override the SDK's Message Center.

The Message Center can be displayed with:

```java
UAirship.shared().getInbox().showInboxActivity();
```

## RichPushInbox

The inbox listener method `onUpdateInbox` has been renamed to `onInboxUpdated`:

```java
// Old
 public interface Listener {
    void onUpdateInbox();
}

// New
public interface Listener {

    /**
     * Called when the inbox is updated.
     */
    void onInboxUpdated();
}
```

## RichPushManager Removed

The inbox can now be accessed directly from [UAirship](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/UAirship.html):

```java
// Old
RichPushInbox inbox = UAirship.shared().getRichPushManager().getRichPushInbox();

// New
RichPushInbox inbox = UAirship.shared().getInbox();
```

The inbox user accessor has moved to [RichPushInbox](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/richpush/RichPushInbox.html):

```java
// Old
RichPushUser user = UAirship.shared().getRichPushManager().getRichPushUser();

// New
RichPushUser user = UAirship.shared().getInbox().getUser();
```

Forcing a message list update has also moved to the [RichPushInbox](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/richpush/RichPushInbox.html).

```java
// Old
RichPushUser user = UAirship.shared().getRichPushManager().refreshMessages();

// New
Cancelable cancelable = UAirship.shared().getInbox().fetchMessages();
```

A listener for fetch message requests has been removed. Instead, provide a callback when fetching messages:

```java
Cancellable cancellable = UAirship.shared().getInbox().fetchMessages(new RichPushInbox.FetchMessagesCallback() {
        @Override
        public void onFinished(boolean success) {
            // message request complete
        }
    });
```

A function that checked whether a message was a Rich Message has been removed. You can identify a message as a Rich Message by checking if the Rich Push Message ID is not null.

```java
boolean isRichPushMessage = !TextUtils.isEmpty(message.getRichPushMessageId());
```

## UALocationManager

The signature of requestSingleLocation changed to now accept an optional LocationCallback and return a Cancelable instance instead of a PendingResult.

```java
// Old
public PendingResult<Location> requestSingleLocation(@NonNull LocationRequestOptions requestOptions);
public PendingResult<Location> requestSingleLocation();

// New
public Cancelable requestSingleLocation(@Nullable LocationCallback locationCallback, @NonNull LocationRequestOptions requestOptions);
public Cancelable requestSingleLocation(@Nullable LocationCallback locationCallback);
public Cancelable requestSingleLocation();
```

## LocationEvent

LocationEvent now uses static constants instead of the UpdateType enum.

```java
// Old
public enum UpdateType {
    CONTINUOUS,
    SINGLE
}

// New
public final static int UPDATE_TYPE_CONTINUOUS = 0;
public final static int UPDATE_TYPE_SINGLE = 1;
```

## Action

Action now uses static constants instead of the Situation enum.

```java
// Old
public enum Situation {
    MANUAL_INVOCATION,
    PUSH_RECEIVED,
    PUSH_OPENED,
    WEB_VIEW_INVOCATION,
    FOREGROUND_NOTIFICATION_ACTION_BUTTON,
    BACKGROUND_NOTIFICATION_ACTION_BUTTON
 }

// New
public final static int SITUATION_MANUAL_INVOCATION = 0;
public final static int SITUATION_PUSH_RECEIVED = 1;
public final static int SITUATION_PUSH_OPENED = 2;
public final static int SITUATION_WEB_VIEW_INVOCATION = 3;
public final static int SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON = 4;
public final static int SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON = 5;
```

## ActionResult

ActionResult now uses static constants instead of the Status enum.

```java
// Old
public enum Status {
    COMPLETED,
    REJECTED_ARGUMENTS,
    ACTION_NOT_FOUND,
    EXECUTION_ERROR
}

// New
public static final int STATUS_COMPLETED = 1;
public static final int STATUS_REJECTED_ARGUMENTS = 2;
public static final int STATUS_ACTION_NOT_FOUND = 3;
public static final int STATUS_EXECUTION_ERROR = 4;
```

## NotificationIdGenerator

`NotificationIDGenerator` has been renamed to `NotificationIdGenerator`.

# UrbanAirship Library 6.1.x to 6.2.x

## AndroidManifest.xml

Added AndroidManifest.xml for automatic manifest merging. Existing integrations
will be required to remove Urban Airship manifest entries from their AndroidManifest.xml and
applications must define `applicationId` in the project's `build.gradle` file.
See [merge conflict docs](https://developer.android.com/tools/building/manifest-merge.html#markers-selectors)
for details on overriding manifest entries.

## GCM Registration

GCM registration now uses Instance ID tokens instead of registration IDs. Urban Airship
SDK will continue to register all sender IDs, but this has been deprecated and will be removed
in 7.0.0. Applications that use `additionalSenderIds` should now register directly with
GCM.

_As of Android SDK 7.2.0, our registration no longer conflicts with other providers. Upgrade to
minimum version 7.2.0 if you are using additional sender IDs._

## RichPushManager

The `updateUser()` and `updateUserIfNecessary()` methods have been deprecated and
marked to be removed in 7.0.0. Use `updateUser(boolean)` instead.

```java
// Deprecated method
public void updateUser();
public void updateUserIfNecessary();

// Added method
public void updateUser(boolean forcefully) ;
```

## PlayServicesUtils

The `isFusedLocationDepdendencyAvailable()` method has been deprecated and marked
to be removed in 7.0.0. It has been replaced with `isFusedLocationDependencyAvailable()`.

```java
// Deprecated method
public static boolean isFusedLocationDepdendencyAvailable()

// Added method
public static boolean isFusedLocationDependencyAvailable()
```

# UrbanAirship Library 6.0.x to 6.1.x

## Minimum SDK Version

Urban Airship now requires the minimum SDK version 8 (Froyo).

For Android Studio/Gradle projects, update the `build.gradle` file:

```groovy
android {

   defaultConfig {
      minSdkVersion 8
   }

}
```

If using Eclipse, you can set the `minSdkVersion` in the `AndroidManifest.xml` file:

```xml
<uses-sdk android:minSdkVersion="8" android:targetSdkVersion="22" />
```

## LandingPageWebView and RichPushMessageWebView

LandingPageWebView and RichPushMessageWebView have been deprecated and all functionality has been
merged into UAWebView. Replace any usages of the deprecated web views with the UAWebView.

## LandingPageActivity

The landing page activity is now able to display Rich Push Messages. Update the
AndroidManifest.xml entry to add "message" scheme for the intent filter:

```xml
<activity
    android:name="com.urbanairship.actions.LandingPageActivity"
    android:exported="false">

    <intent-filter>
        <action android:name="com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION" />

        <data android:scheme="http" />
        <data android:scheme="https" />
        <data android:scheme="message" />

        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

For custom landing page activities, the message ID can be accessed through the intent's data:

```java
Uri uri = intent.getData();
if (uri.getScheme().equalsIgnoreCase(RichPushInbox.MESSAGE_DATA_SCHEME)) {
    String messageId = uri.getSchemeSpecificPart();
    RichPushMessage message = UAirship.shared()
                                      .getRichPushManager()
                                      .getRichPushInbox()
                                      .getMessage(messageId);

    if (message != null) {
        uaWebView.loadRichPushMessage(message);
        message.markRead();
    } else {
        // message not found
    }
}
```

## Rich Push Behavior Changes

When a push message is opened that references a Rich Push Message, the [OpenRichPushInboxAction](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/actions/OpenRichPushInboxAction.html)
will automatically be triggered. The action will attempt to automatically deep link to an activity by starting
an intent with action `com.urbanairship.VIEW_RICH_PUSH_MESSAGE` to view the message. If the intent fails
to start an activity, the action will now default to displaying the message in the landing page activity.

Applications should either remove custom deep linking code for Rich Push Messages and implement
support for [Displaying the Message Center](https://docs.urbanairship.com/platform/android.md#android-rich-push-inbox-intents), or they should override [OpenRichPushInboxAction](https://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/actions/OpenRichPushInboxAction.html).

## Client-side Tags

The setter and getter for `deviceTagsEnabled` has been deprecated and replaced
with `channelTagRegistrationEnabled`.

```java
// Deprecated
public boolean getDeviceTagsEnabled();
public void setDeviceTagsEnabled(boolean enabled);

// Added
public boolean getChannelTagRegistrationEnabled();
public void setChannelTagRegistrationEnabled(boolean enabled);
```

# UrbanAirship Library 5.1.x to 6.0.x

## Dependencies

It is recommended to use the v7 CardView support library for in-app messaging to provide a consistent
style across all devices.

Using Android Studio:

```groovy
dependencies {
   `com.android.support:cardview-v7:22.0.0`

   ...
 }
```

Eclipse users should follow [Android Developer docs](http://developer.android.com/tools/projects/projects-eclipse.html#ReferencingLibraryProject).

## Actions

The action's registry name has been removed from any action methods. The action name is now part of
the action argument's metadata.

Example:

```java
@Override
public void onStart(ActionArguments arguments) {

    // Called before perform
}

@Override
public ActionResult perform(ActionArguments arguments) {
    String actionName = arguments.getMetadata()
                                 .getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA);

    ...
}

@Override
public void onFinish(ActionArguments arguments, ActionResult result) {

    // Called after perform
}
```

## ActionRunner

`ActionRunner` has been replaced with a new `ActionRunRequest` class that provides a fluent API
for running actions. The action run request takes in a value, situation, and metadata and constructs
the `ActionArgument` and passes it to the action to run.

Example:

```java
// Run the action "actionName" with value "actionValue"
ActionRunRequest.createRequest("actionName")
                .setSituation(Situation.MANUAL_INVOCATION)
                .setValue("actionValue")
                .run();
```

## ActionRegistry

The action registry is no longer a singleton and `shared` has been deprecated. The default registry can be accessed through `UAirship`.

Example:

```java
ActionRegistry registry = UAirship.shared().getActionRegistry();
```

## ActionArguments

Action arguments creation is no longer visible, and instead will be created by `ActionRunRequest` and
passed to the action.

Argument's value can now only contain an `ActionValue` instance. `ActionValue` is a new class that limits the type of values to those that can be JSON serializable. The `ActionValue` class contains getter methods
that automatically does type checking to parse value back into its original form.

Example:

```java
Action logAction = new Action() {
    @Override
    public ActionResult perform(ActionArguments arguments) {
        Log.i("TestAction", arguments.getValue().getString());

        return ActionResult.newResult(arguments.getValue());
    }
};

// Log "hello"
ActionRunRequest.createRequest(logAction)
        .setValue("hello")
        .run();
```

Argument's metadata is now stored and accessed through a `Bundle`. `RichPushMessages` are no longer available as metadata, instead the message's ID will be made available under the key `RICH_PUSH_ID_METADATA`.

Changes:

```java
// Added methods
Bundle getMetadata();
ActionValue getValue();

// Added fields
public static final String RICH_PUSH_ID_METADATA;

// Removed constructor
ActionArguments(Situation situation, Object value);

// Removed methods
<T> T getMetadata(String key);
Object getValue();

// Removed fields
public static final String RICH_PUSH_METADATA;

// Removed class
public class Builder;
```

## ActionResult

Action results are now restricted to an `ActionValue` for the result's value.

## ActionCompletionCallback

The action completion callback now calls `onFinish` with both the `ActionArguments` and `ActionResult`.

Changes:

```java
// Added methods
public void onFinish(ActionArguments arguments, ActionResult result);

// Removed
public void onFinish(ActionResult result);
```

## Rich Push

We introduced two intents that will allow deep linking for in-app messaging. See
[Displaying the Message Center](https://docs.urbanairship.com/platform/android.md#android-rich-push-inbox-intents) for more details.

## AutoPilot

AutoPilot no longer defines and calls `execute`. Instead, classes that implement AutoPilot are expected
to implement the interface `OnReadyCallback` to perform any setup after take off is complete. Optionally
AutoPilot classes can override `createAirshipConfigOptions` to provide custom config.

Example:

```java
public class ExampleAutoPilot extends Autopilot {

    @Override
    public AirshipConfigOptions createAirshipConfigOptions(Context context) {
        // Optionally return takeoff custom take off options or null
        // to asynchronously create the config from airshipconfig.properties
        return null;
    }

    @Override
    public void onAirshipReady(UAirship airship) {
        // Perform any airship setup here
        airship.getPushManager().setUserNotificationsEnabled(true);
    }
}
```

# UrbanAirship Library 5.0.x to 5.1.x

Major highlights of this release:

* Android Lollipop notification support.

* Advanced notification support through the Push API, including support for Android Wear and styles. See [Android](https://docs.urbanairship.com/api/ua.md#notification-payload-android) Push API for more details.

* Enhanced security when using the Urban Airship JavaScript bridge in custom web view implementations.

## Dependencies

The SDK now requires the latest Google Play Services (6.1 or greater) for Android and the latest support v4 library (revision 21 or greater). Gradle 0.13.3+ is required for Android Studio.

## JDK 7

To target and support the latest features in Android Lollipop, the Urban Airship SDK is now built with JDK 7. When using Android Studio, you may need
to update the JDK location to point to JDK 7 in file -> project settings -> SDK Location -> JDK Location.

## Whitelisting

The Urban Airship JavaScript bridge is now only injected in the UAWebViewClient if the URL is whitelisted. Urban Airship URLs are automatically whitelisted,
but if the UA JavaScript bridge is used outside of Urban Airship hosted Rich Application Pages, the hosting urls need to be whitelisted.

Whitelists rules can be defined in the airshipconfig.properties file:

```text
whitelist = *://*.mydomain.com, file:///android-asset/*
```

Whitelist rules can also be defined directly:

```java
airship.getWhitelist().addEntry("https://*.urbanairship.com");
```

See [Whitelist](http://docs.urbanairship.com/reference/libraries/android/latest/reference/com/urbanairship/js/Whitelist.html) for more details on creating
valid url patterns.

# UrbanAirship Library 4.x to 5.0.0

Major highlights of this release:

* The Airship Push Identifier (APID) has been replaced with the Channel Identifier (Channel ID). Existing
  APIDs will automatically migrate to Channel IDs. A channel will always be created and available. When
  push is disabled, instead of deleting the APID the channel will be updated to opted-out, allowing the
  device to remain addressable for other services, e.g., Rich App Pages.

* A new Location module that supports Google Play Services' Fused Location Provider when available.

* This library supports both Amazon Device Messaging (ADM) and Google Cloud Messaging (GCM).

For the last few versions we focused on providing a stable API. Given the major
improvements added in this release, it was necessary to redesign portions of the
library.

## Packaging

The Urban Airship library is now a library project with resources. Eclipse users
will import the library as a project and reference it. The library is
packaged as an `aar` file for Android Studio users. More details can be found in
[Android: Getting Started](http://docs.urbanairship.com/build/push/android.html#add-the-library-to-your-project).

## Google Play Services

The Urban Airship SDK now depends on Google Play Services for GCM registration. The
library is built against Google Play Services, but does not package it for applications.
Instead, any application that wants to support GCM needs to include Google Play Services
as a dependency. Set up instructions can be found [here](http://developer.android.com/google/play-services/setup.html#Install).

Urban Airship provides utility classes to simplify Google Play Services error
handling.

AndroidManifest.xml changes:

```xml
<!-- REQUIRED for PlayServiceUtils.handleAnyPlayServicesError to handle Google Play Services recoverable errors. -->
<activity android:name="com.urbanairship.google.PlayServicesErrorActivity" android:theme="@android:style/Theme.Translucent.NoTitleBar" />
```

Add this call to your main activity's onStart method:

```java
// Handle any Google Play Services errors
if (PlayServicesUtils.isGooglePlayStoreAvailable()) {
    PlayServicesUtils.handleAnyPlayServicesError(this);
}
```

This will automatically start a transparent activity that checks for Google Play
service errors. If any user recoverable errors are detected, the activity will
show an error dialog to the user to resolve the issue.

## Async TakeOff()

Calling `takeOff()` is now asynchronous. Calling shared() now blocks the first time
until airship is ready. A new shared method with a callback is now available to
prevent blocking the main thread.

Example:

```java
// Calling takeOff() with a callback
UAirship.takeOff(this, new UAirship.OnReadyCallback() {
    @Override
    public void onAirshipReady(UAirship airship) {
        airship.getPushManager().setPushEnabled(true);
    }
});

// Getting the UAirship instance asynchronously
UAirship.shared(new UAirship.OnReadyCallback() {
    @Override
    public void onAirshipReady(UAirship airship) {
        airship.getPushManager().setPushEnabled(true);
    }
});

// Getting the UAirship instance synchronously
UAirship airship = UAirship.shared();

// Checking if UAirship is ready
if (UAirship.isReady()) {
    // Airship is ready, shared() no longer blocks
}
```

## Manager access

The shared method on PushManager, RichPushManager, and LocationManager have been
deprecated and replaced with getters on the shared UAirship instance:

```java
// Deprecated
PushManager.shared();
RichPushManager.shared();
LocationManager.shared();

// Added
UAirship.shared().getPushManager();
UAirship.shared().getRichPushManager();
UAirship.shared().getLocationManager();
```

## AndroidManifest changes

The core receiver now depends on an intent filter and a priority being set, update
the entry with the following:

```xml
<!-- REQUIRED for Urban Airship Push. The priority is important to be set lower than the
application's push intent receiver in order for the push intent receiver to handle push intents
before the core receiver. This allows the application to launch any activities before Urban
Airship performs any actions or falls back to launching the application launch intent. -->
<receiver android:name="com.urbanairship.CoreReceiver"
          android:exported="false">

    <intent-filter android:priority="-999">
        <action android:name="com.urbanairship.push.OPENED" />

        <!-- MODIFICATION REQUIRED - Use your package name as the category -->
        <category android:name="${applicationId}" />
    </intent-filter>
</receiver>
```

A new core activity is now required for notification action buttons. Add the following
under the application entry:

```xml
<activity android:name="com.urbanairship.CoreActivity"/>
```

## AirshipConfigOptions

The `richPushEnabled` and `pushServiceEnabled` fields have been removed, thus Rich
Push and Push will now be initialized by default. The transport field is no longer
used. ADM and GCM will automatically be selected based on the detected platform.

The following changes have been made for AirshipConfigOptions:

```java
// Removed fields
public String transport;
public boolean richPushEnabled;
public boolean pushServiceEnabled;

// Added fields
public long backgroundReportingIntervalMS;

// Removed methods
public TransportType getTransport();
```

## Push Intent Receiver

Push registration intents have been updated for channels and notification action
buttons. A new abstract broadcast receiver 'BaseIntentReceiver' is now available
that can be extended to make parsing the intents easier.

```java
public class IntentReceiver extends BaseIntentReceiver {

    private static final String TAG = "IntentReceiver";

    @Override
    protected void onChannelRegistrationSucceeded(Context context, String channelId) {
        Log.i(TAG, "Channel registration updated. Channel Id:" + channelId + ".");
    }

    @Override
    protected void onChannelRegistrationFailed(Context context) {
        Log.i(TAG, "Channel registration failed.");
    }

    @Override
    protected void onPushReceived(Context context, PushMessage message, int notificationId) {
        Log.i(TAG, "Received push notification. Alert: " + message.getAlert() + ". Notification ID: " + notificationId);
    }

    @Override
    protected void onBackgroundPushReceived(Context context, PushMessage message) {
        Log.i(TAG, "Received background push message: " + message);
    }

    @Override
    protected boolean onNotificationOpened(Context context, PushMessage message, int notificationId) {
        Log.i(TAG, "User clicked notification. Alert: " + message.getAlert());
        return false;
    }

    @Override
    protected boolean onNotificationActionOpened(Context context, PushMessage message, int notificationId, String buttonId, boolean isForeground) {
        Log.i(TAG, "User clicked notification button. Button ID: " + buttonId + " Alert: " + message.getAlert());
        return false;
    }
}
```

The intent receiver is no longer set directly on PushManager. Instead, set an intent
filter in the receiver's intent filter.

```xml
<!-- OPTIONAL, if you want to receive push, push opened and registration completed intents -->
<!-- Replace the receiver below with your package and class name -->
<receiver android:name="com.urbanairship.push.sample.IntentReceiver"
          android:exported="false">

    <intent-filter>
        <action android:name="com.urbanairship.push.CHANNEL_UPDATED" />
        <action android:name="com.urbanairship.push.OPENED" />
        <action android:name="com.urbanairship.push.RECEIVED" />

        <!-- MODIFICATION REQUIRED - Use your package name as the category -->
        <category android:name="com.urbanairship.push.sample" />
    </intent-filter>
</receiver>
```

It is no longer necessary to set an intent receiver to only launch the application
when a notification is opened. Instead, the library supports launching the application's
launch intent if the application does not handle the intent or does not launch an
activity from the notification open broadcasts.

The intent receiver no longer needs to check for Urban Airship Action when determining
if the application needs to be launched. Actions now run after the application
is launched.

## PushManager

It is now possible to turn off just user notifications in order to send data-only
messages to the application when the user is opted out of notifications. User
notifications defaults to false, and push enabled now defaults to true. The
previous push enabled setting is automatically migrated to the new user
notification setting.

```java
// Removed methods
public static void enablePush();
public static void disablePush();

// Added method
public void setPushEnabled(boolean enabled);
public boolean isPushEnabled();
public void setUserNotificationsEnabled(boolean enabled);
public boolean getUserNotificationsEnabled();
```

Since APIDs have been replaced with channels, the `getAPID()` method has been
removed and replaced by `getChannelId()`.

```java
// Removed method
public String getAPID();

// Added method
public String getChannelId();
```

The `getPreferences()` method and PushPreferences have been removed. Please use
the preference getters and setters on PushManager instead:

```java
// Removed method
public PushPreferences getPreferences();

// Added methods
public boolean isPushEnabled();
public boolean isSoundEnabled();
public void setSoundEnabled(boolean enabled);
public boolean isVibrateEnabled();
public void setVibrateEnabled(boolean enabled);
public boolean isQuietTimeEnabled();
public void setQuietTimeEnabled(boolean enabled);
public boolean isInQuietTime();
public Date[] getQuietTimeInterval();
public String getLastReceivedSendId();
public void setQuietTimeInterval(Date startTime, Date endTime);
```

## Notifications

PushNotificationBuilder, BasicPushNotificationBuilder, and CustomPushNotificationBuilder
have been removed with a new properly named classes NotificationFactory,
SystemNotificationFactory, and CustomLayoutNotificationFactory.

A new factory, DefaultNotificationFactory, is now the default and automatically
applies big text style to all notifications. The SystemNotificationFactory can be
used instead if the big text style is undesirable:

```java
final SystemNotificationFactory notificationFactory = new SystemNotificationFactory(this);
UAirship.takeOff(this, new UAirship.OnReadyCallback() {
    @Override
    public void onAirshipReady(UAirship airship) {
        airship.getPushManager().setNotificationFactory(notificationFactory);
    }
});
```

Custom notification factories are still supported, but they need to now extend
NotificationFactory base class.

Example:

```java
public class CustomNotificationFactory extends NotificationFactory {

    public CustomNotificationFactory(Context context) {
        super(context);
    }

    @Override
    public Notification createNotification(PushMessage message, int notificationId) {
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setContentTitle("Notification title")
                .setContentText(message.getAlert())
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.notification_icon);


        // To support interactive notification buttons, extend the NotificationCompat.Builder
        builder.extend(createNotificationActionsExtender(message, notificationId));

        return builder.build();
    }

    @Override
    public int getNextId(PushMessage pushMessage) {
        return NotificationIDGenerator.nextID();
    }
}
```

## Analytics

The methods to report activity started and stopped are now static. This is to
prevent blocking on the shared UAirship instance when reporting the activity state.

```java
// Removed
public void activityStarted(Activity activity);
public void activityStopped(Activity activity);

// Added
public static void activityStarted(Activity activity);
public static void activityStopped(Activity activity);
```

## Android Preferences

APID and Location preferences have been updated for channels and the new
location module:

```java
// Removed classes
public class ApidPreference;
public class LocationEnablePreference;
public class LocationForegroundEnablePreference;
public class LocationBackgroundEnablePreference;

// Added classes
public class ChannelIdPreference;
public class LocationUpdatesEnabledPreference;
public class LocationBackgroundUpdatesAllowedPreference;
```

## RichPushManager

The `getRichPushInbox()` has been added to get the RichPushInbox:

```java
// Added method
public synchronized RichPushInbox getRichPushInbox();
```

## RichPushInbox

The RichPushInbox `shared()` method has been deprecated. Please use
RichPushManager's `getRichPushInbox()` method instead:

```java
// Deprecated method
public synchronized static RichPushInbox shared();
```

## RichPushUser

The `getInbox()` method has been deprecated. Please use RichPushManager's
`getRichPushInbox()` method instead:

```java
// Deprecated method
public synchronized RichPushInbox getInbox();
```

With the removal of APIDs, the APID related methods have been removed:

```java
// Removed methods
public void setApids(HashSet<String> apids);
public void addApid(String apid);
public HashSet<String> getApids();
```

## GCMMessageHandler

The `GCMMessageHandler` have been removed. All GCM constants are now located in
`GCMConstants`.

## Location Changes

The new location module has been completely rewritten in the Urban
Airship SDK and is now compatible with Google Play Services' Fused Location
Provider. Fused Location will automatically be used if Google Play Services is
available on the device, is up to date, and the application was built with the
Google Play Services dependency.

### LocationOptions (location.properties file)

LocationOptions, including `locationServiceEnabled`, is no longer used for
Location and have been removed. The `backgroundReportingIntervalSeconds` field
has been renamed to `backgroundReportingIntervalMS` and moved to
AirshipConfigOptions.

### LocationRequestOptions

Location requests options are modeled after the Fused Location Provider by
specifying high level needs instead of low level criteria. The location request
options will automatically be converted to either criteria when using the standard
Android Location APIs or to a LocationRequest when using Fused Location Provider.

```java
LocationRequestOptions options = new LocationRequestOptions.Builder()
        .setPriority(LocationRequestOptions.PRIORITY_BALANCED_POWER_ACCURACY)
        .setMinDistance(800)
        .setMinTime(5, TimeUnit.MINUTES)
        .create();

// Set the default continuous location request options
UAirship.shared().getLocationManager().setLocationRequestOptions(options);
```

### Enabling Location Updates

The settings for enabling location have been simplified. Location updates are
enabled or disabled by `setLocationUpdatesEnabled()`. Updates continuing in the
background is controlled by `setBackgroundLocationAllowed()`. Listening for
background locations now requires both location updates enabled and background
location allowed.

Changes in `UALocationManager`:

```java
// Removed methods
public static void enableLocation();
public static void disableLocation();
public static void enableBackgroundLocation();
public static void enableForegroundLocation();
public static void disableForegroundLocation();
public static void disableBackgroundLocation();
public LocationPreferences getPreferences();

// Added methods
public boolean isLocationUpdatesEnabled();
public void setLocationUpdatesEnabled(boolean enabled);
public boolean isBackgroundLocationAllowed();
public void setBackgroundLocationAllowed(boolean enabled);
```

### Listening for Location Updates

Listening for location updates is still supported but updates will no longer be
sent as a broadcast to a BroadcastReceiver. Instead, a location listener can be
added to UALocationManager to listen for continuous location updates.

Changes in `UALocationManager`:

```java
// Removed methods
public void setIntentReceiver(Class<? extends BroadcastReceiver> receiver);
public  Class<? extends BroadcastReceiver> getIntentReceiver();

// Added methods
public void addLocationListener(LocationListener listener);
public void removeLocationListener(LocationListener listener);
```

Example:

```java
LocationListener locationListener = new LocationListener() {
    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "Received location: " + location);
    }
};

// Listening for updates
UAirship.shared().getLocationManager().addLocationListener(locationListener);

...

// Stop listening for updates when finished
UAirship.shared().getLocationManager().removeLocationListener(locationListener);
```

### Single Location Requests

Single location requests can be made at any time without checking if the service
is bound or not. Binding and unbinding to the location service is handled
internally in the UALocationManager.  A pending result will be returned when
requesting a single location, which allows canceling the request and allows
setting a callback when the location is ready.

Changes in `UALocationManager`:

```java
// Removed methods
public void recordCurrentLocation();
public void recordCurrentLocation(Criteria criteria);
public static boolean isServiceBound();
public static void bindService();
public static void unbindService();

// Added methods
public PendingResult requestSingleLocation();
public PendingResult requestSingleLocation(LocationRequestOptions requestOptions);
```

Example:

```java
UALocationManager locationManager = UAirship.shared().getLocationManager();

// Request a single location with the current location request options
PendingResult<Location> pendingRequest = locationManager.requestSingleLocation();

// Optionally, specify the location request options
// PendingResult<Location> pendingRequest = locationManager.requestSingleLocation(customRequestOptions);

// Add a callback for when the location is ready
pendingRequest.onResult(new PendingResult.ResultCallback<Location>() {
    @Override
    public void onResult(Location result) {
        Log.i(TAG, "New location: " + result);
    }
});

...

// Optionally, cancel the request
pendingRequest.cancel();
```

### Recording Location

The methods for uploading location events through the `UALocationManager` have
been removed and replaced with Urban Airship Analytics' method `recordLocation()`.

Changes in `UALocationManager`:

```java
// Removed methods
public void recordLocation(Location newLocation);
public void recordLocation(Location newLocation, int accuracy, int minDistance);
```

When a new location becomes available, record the location:

```java
UAirship.shared().getAnalytics().recordLocation(location);
```

The location will be automatically batched and uploaded.

# UrbanAirship Library 4.0.1 to 4.0.2

## UALocationManager

Migration is only needed if your application is listening for location updates from the
UALocationManager. Location updates will no longer be broadcasted to the entire system with
an implicit intent, instead they will be broadcasted to only the receiver that is set
on UALocationManager.

The following changes have been made to UALocationManager because it is no longer
necessary to namespace the location broadcasts:

```java
// Removed constants
public static final String ACTION_SUFFIX_LOCATION_UPDATE;
public static final String ACTION_SUFFIX_LOCATION_SERVICE_BOUND;
public static final String ACTION_SUFFIX_LOCATION_SERVICE_UNBOUND;

// Removed method
public static String getLocationIntentAction(final String suffix);

// Added constants
public static final String ACTION_LOCATION_UPDATE;
public static final String ACTION_LOCATION_SERVICE_BOUND;
public static final String ACTION_LOCATION_SERVICE_UNBOUND;

// Added methods
public  Class<? extends BroadcastReceiver> getIntentReceiver();
public void setIntentReceiver(Class<? extends BroadcastReceiver> receiver);
```

Application code and design changes can be minimized by using the
[LocalBroadcastManager](http://developer.android.com/reference/android/support/v4/content/LocalBroadcastManager.html)
from the Android Support Library to securely broadcast location to the entire application.

**Example**

Create an intent receiver that locally broadcasts location intents:

```java
public class LocationIntentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
```

Set the intent receiver after takeoff:

```java
UAirship.takeOff(this);

UALocationManager.shared().setIntentReceiver(LocationIntentReceiver.class);
```

Add the intent receiver in the AndroidManifest.xml under the application element:

```xml
<receiver android:name=".LocationIntentReceiver" />
```

Register existing receivers using the LocalBroadcastReceiver:

```java
LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
```

# UrbanAirship Library 3.3.x to 4.0.x

## Actions

This release adds support for Urban Airship *Actions*.  In order to use Actions, you must make a few changes
to `AndroidManifest.xml` and possibly the `PushReceiver` that handles push notifications.

See [Android Actions](https://docs.urbanairship.com/platform/android.md#android-actions) for more information on *Actions*.

## JavaScript Namespace

The namespace `urbanairship` has been deprecated and replaced with `UAirship` which
is consistent between both iOS and Android.

## Rich Push JavaScript Interface

`RichPushMessageJavascriptInterface` and `RichPushMessageJavascript` have been replaced
by `com.urbanairship.js.UAJavascriptInterface`.

Support for these deprecated features will be removed after **October 2, 2014**.

## Custom JavaScript Interfaces

`RichPushManager` no longer allows setting a custom JavaScript interface and namespace.
This only affects apps that are using Rich App Pages that use JavaScript
with a custom namespace or a customized interface. Custom JavaScript Interfaces are still
supported but are encouraged to be used under a different namespace.

To add custom interfaces, create a new class that extends `RichPushMessageWebView` and override the
`populateCustomJavascriptInterfaces` method.

Example:

```java
public class CustomRichPushMessageWebView extends RichPushMessageWebView {

    public CustomRichPushMessageWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CustomRichPushMessageWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomRichPushMessageWebView(Context context) {
        super(context);
    }

    @Override
    protected void populateCustomJavascriptInterfaces() {
        addJavascriptInterface(new CustomJavascriptInterface(), "CustomNameSpace");
    }

    class CustomJavascriptInterface {
        @JavascriptInterface
        public String myMethod() {
            return "hello";
        }
    }
}
```

If a customized version of the Urban Airship JavaScript Interface is required to be used under
the `urbanairship` namespace, create a new class that extends `RichPushMessageWebView` and override
the `loadUrbanAirshipJavascriptInterface` method.  Make sure to still load the unmodified version
of the `RichPushMessageJavascriptInterface` under the `UAirship` namespace in order to use actions
through JavaScript.

```java
public class CustomRichPushMessageWebView extends RichPushMessageWebView {

    public CustomRichPushMessageWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CustomRichPushMessageWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomRichPushMessageWebView(Context context) {
        super(context);
    }

    @Override
    protected void populateCustomJavascriptInterfaces() {
        // Add any custom JavaScript interfaces.  The interfaces will be called
        // when RichPushMessageWebView is constructed.
        addJavascriptInterface(new CustomJavascriptInterface(), "CustomNameSpace");
    }

    @Override
    protected void loadJavascriptInterface(RichPushMessage message) {
        // Load unmodified version of the UAJavascriptInterface
        UAJavascriptInterface js = new UAJavascriptInterface(this, message);
        addJavascriptInterface(js, UAJavascriptInterface.JAVASCRIPT_IDENTIFIER);

        // Load the extended version of the interface under the "uairship" namespace
        CustomJavascriptInterface js = new CustomUAJavascriptInterface(this, message);
        addJavascriptInterface(js, UAJavascriptInterface.DEPRECATED_JAVASCRIPT_IDENTIFIER);
    }

    public static class CustomUAJavascriptInterface extends UAJavascriptInterface {

        public CustomJavascriptInterface(WebView webView, RichPushMessage message) {
            super(webView, message);
        }

        // Do any customizations
    }

    public static class CustomJavascriptInterface {
        @JavascriptInterface
        public String myMethod() {
            return "hello";
        }
    }
}
```

Update any uses of `RichPushMessageWebView` to `CustomRichPushMessageWebView` so the custom
JavaScript interfaces are used.

## Rich Push Message View

`RichPushMessageView` has been renamed `RichPushMessageWebView`. When setting
a custom web view client on the view, the client should extend the `UAWebViewClient`
and any methods that are overridden should call through to the super class implementations.

# UrbanAirship Library 3.2.x to 3.3.x

## RichPushManager

RichPushManager.Listener no longer defines public void onRetrieveMessage(boolean success, String messageId).
The callback was used for internal purposes only and is no longer needed.  If any class implements this callback,
it is safe to just remove it.

# UrbanAirship Library 3.1.x to 3.2.x

## RichPushInbox

The batch operations for marking messages read, marking messages unread, and
deleting messages no longer block on database operations, but they also no longer
return the number of items successfully changed.

```java
// Old calls that returned an int
public int markMessagesRead(final Set<String> messageIds)
public int markMessagesUnread(final Set<String> messageIds)
public int deleteMessages(final Set<String> messageIds)

// New calls that return void
public void markMessagesRead(final Set<String> messageIds)
public void markMessagesUnread(final Set<String> messageIds)
public void deleteMessages(final Set<String> messageIds)
```

# UrbanAirship Library 2.1.4 to 3.0.x

## Helium Removed

Helium and Hybrid transport type is no longer supported.  Please use GCM instead
[GCM Setup](https://docs.urbanairship.com/platform/push-providers/fcm.md).

## Moved Constants

Part of some internal restructuring, the following constants have moved:

```java
GCMMessageHandler.ACTION_GCM_RECEIVE            // moved from PushManager.ACTION_GCM_RECEIVE
GCMMessageHandler.ACTION_GCM_DELETED_MESSAGES   // moved from PushManager.ACTION_GCM_DELETED_MESSAGES
GCMMessageHandler.EXTRA_GCM_MESSAGE_TYPE        // moved from PushManager.EXTRA_GCM_MESSAGE_TYPE
GCMMessageHandler.EXTRA_GCM_TOTAL_DELETED       // moved from PushManager.EXTRA_GCM_TOTAL_DELETED
GCMMessageHandler.GCM_DELETED_MESSAGES_VALUE    // moved from PushManager.GCM_DELETED_MESSAGES_VALUE

GCMRegistrar.EXTRA_GCM_REGISTRATION_ID          // moved from PushManager.EXTRA_GCM_REGISTRATION_ID
```

## RichPushInbox

RichPushInbox no longer extends the Cursor interface. It is no longer required to
use a cursor adapter, instead use a list adapter. A full example can be found
here [Android Message Center](https://docs.urbanairship.com/guides/android-rp-tutorial.md).

## PushWorkerService

Push Worker Service is no longer used and needs to be removed from the AndroidManifest.xml.

Remove this line from the AndroidManifest.xml file:

```xml
<service android:name="com.urbanairship.push.PushWorkerService" android:label="Push Notification Worker Service"/>
```
