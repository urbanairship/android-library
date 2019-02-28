# Urban Airship Debug Library


## Setup

Add the debug library as a debug dependency:
```
dependencies {
    def airship_version = "9.7.0"
    implementation "com.urbanairship.android:urbanairship-fcm:$airship_version"
    debugImplementation "com.urbanairship.android:urbanairship-debug:$airship_version"
}
```

The debug library will automatically add a new launcher icon for the application with the app's icon,
but the label will be `Urban Airship Debug`. The new launcher will navigate directly to the DebugActivity.


## Adding Screens

Right now we are using Activities instead of the navigation architecture component for navigation
because we are currently not using AndroidX. To start, define the screen with an activity entry point. Add
the activity to the manifest. If you want the screen to be added to the top level debug screen,
update `res/xml/screens.xml` with the new screen. Example:
```
   <entry
        description="@string/event_view_description"
        activity="com.urbanairship.debug.event.EventActivity"
        title="@string/event_view_title" />
```

The DebugManager class is initialized during takeOff and has the same lifecycle calls as any other
AirshipComponent. If a debug screen needs any initialization done, use this class as the entry point.
