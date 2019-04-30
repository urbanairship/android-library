# Airship Debug Library


## Setup

Add the debug library as a debug dependency:
```
dependencies {
    def airship_version = <AIRSHIP_VERSION>
    implementation "com.urbanairship.android:urbanairship-fcm:$airship_version"
    debugImplementation "com.urbanairship.android:urbanairship-debug:$airship_version"
}
```

The debug library will automatically add a new launcher icon for the application with the app's icon,
but the label will be `Airship Debug`. The new launcher will navigate directly to the DebugActivity.


## Adding Screens

The debug library makes use of the Navigation Component. To add a new screen, define a fragment,
add the fragment to the navigation graph in `res/navigation/ua_debug.xml`, and add a new top level entry
to `res/xml/screens.xml`:

```
   <entry
        description="@string/new_description"
        title="@string/new_title"
        navigationId="@id/newFragment"/>
```


The DebugManager class is initialized during takeOff and has the same lifecycle calls as any other
AirshipComponent. If a debug screen needs any initialization done, use this class as the entry point.
