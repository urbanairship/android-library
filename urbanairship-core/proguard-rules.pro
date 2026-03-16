## Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

## Descriptor classes
-keep public class com.urbanairship.Airship { <init>(); }
-keep public class com.urbanairship.AirshipConfigOptions { <init>(); }
-keep public class com.urbanairship.push.PushMessage { <init>(); }

## Autopilot
-keep public class com.urbanairship.Autopilot { <init>(); }
-keep public class * extends com.urbanairship.Autopilot { <init>(); }

## Push Providers
-keep public class com.urbanairship.push.PushProvider { <init>(); }
-keep public class * implements com.urbanairship.push.PushProvider { <init>(); }
-keepclassmembernames class * implements com.urbanairship.push.PushProvider {
  public <methods>;
  public <fields>;
}

## Modules
-keep public class com.urbanairship.modules.**  { <init>(); }
-keep public class * implements com.urbanairship.modules.** { <init>(); }
-keepclassmembernames class * implements com.urbanairship.modules.** {
  public <methods>;
  public <fields>;
}

## Airship Version Info
-keep public class com.urbanairship.AirshipVersionInfo { <init>(); }
-keepclassmembers class com.urbanairship.AirshipVersionInfo {
  public <methods>;
  public <fields>;
}

## Actions
-keep public class * extends com.urbanairship.actions.Action { <init>(); }
-keep public class * implements com.urbanairship.actions.ActionPredicate { <init>(); }

## Views that contain descriptor classes
-keep public class com.urbanairship.iam.view.BannerDismissLayout$Listener
-keep public class com.urbanairship.iam.view.InAppButtonLayout$ButtonClickListener

## Optional
-dontwarn com.urbanairship.location.FusedLocationAdapter*
-dontwarn com.urbanairship.activity.ThemedActivity*
-dontnote com.urbanairship.google.PlayServicesUtils
