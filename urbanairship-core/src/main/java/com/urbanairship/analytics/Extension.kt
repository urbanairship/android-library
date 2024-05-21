/* Copyright Airship and Contributors */

package com.urbanairship.analytics

/**
 * Used by Airship frameworks to track usage.
 * @hide
 * */
public enum class Extension(internal val extensionName: String) {
    CORDOVA("cordova"),
    FLUTTER("flutter"),
    REACT_NATIVE("react-native"),
    UNITY("unity"),
    XAMARIN("xamarin"),
    DOT_NET("dot-net"),
    TITANIUM("titanium"),
    CAPACITOR("capacitor");
}
