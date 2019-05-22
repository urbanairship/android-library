## Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

## Descriptor classes
-keep public class com.urbanairship.UAirship
-keep public class com.urbanairship.AirshipConfigOptions
-keep public class com.urbanairship.push.PushMessage

## Autopilot
-keep public class com.urbanairship.Autopilot
-keep public class * extends com.urbanairship.Autopilot

## Push Providers
-keep public class com.urbanairship.push.PushProvider
-keep public class * implements com.urbanairship.push.PushProvider
-keepclassmembernames class * implements com.urbanairship.push.PushProvider {
  public <methods>;
  public <fields>;
}

## Airship Version Info
-keep public class com.urbanairship.AirshipVersionInfo
-keepclassmembers class com.urbanairship.AirshipVersionInfo {
  public <methods>;
  public <fields>;
}

## Actions
-keep public class * extends com.urbanairship.actions.Action
-keep public class * implements com.urbanairship.actions.ActionRegistry$Predicate

## Views that contain descriptor classes
-keep public class com.urbanairship.iam.banner.BannerDismissLayout$Listener
-keep public class com.urbanairship.iam.view.InAppButtonLayout$ButtonClickListener

## Optional
-dontwarn com.urbanairship.location.FusedLocationAdapter*
-dontwarn com.urbanairship.messagecenter.ThemedActivity*
-dontnote com.urbanairship.google.PlayServicesUtils
