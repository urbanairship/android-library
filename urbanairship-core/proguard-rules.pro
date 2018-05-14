## Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

## Autopilot
-keep class com.urbanairship.Autopilot
-keep public class * extends com.urbanairship.Autopilot

## Push Providers
-keep public class * extends com.urbanairship.push.PushProvider
-keepclassmembernames class * extends com.urbanairship.push.PushProvider {
  public <methods>;
  public <fields>;
}

## Airship Version Info
-keep public class * extends com.urbanairship.AirshipVersionInfo
-keepclassmembers class * extends com.urbanairship.AirshipVersionInfo {
  public <methods>;
  public <fields>;
}

## Actions
-keep public class * extends com.urbanairship.actions.Action
-keep public class * implements com.urbanairship.actions.ActionRegistry$Predicate

## Optional
-dontwarn com.urbanairship.location.FusedLocationAdapter*
-dontwarn com.urbanairship.push.iam.view.BannerCardView*
-dontwarn com.urbanairship.messagecenter.ThemedActivity*
