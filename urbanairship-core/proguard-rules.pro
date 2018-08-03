## Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

## Autopilot
-keep, includedescriptorclasses public class com.urbanairship.Autopilot
-keep public class * extends com.urbanairship.Autopilot

## Push Providers
-keep, includedescriptorclasses public class * extends com.urbanairship.push.PushProvider
-keepclassmembernames, includedescriptorclasses class * extends com.urbanairship.push.PushProvider {
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

## ActionService contains a test constructor that contains a descriptor class
-dontnote com.urbanairship.actions.ActionService

## Views that contain descriptor classes
-keep public class com.urbanairship.iam.banner.BannerDismissLayout$Listener
-keep public class com.urbanairship.iam.view.InAppButtonLayout$ButtonClickListener


## Optional
-dontwarn com.urbanairship.location.FusedLocationAdapter*
-dontwarn com.urbanairship.push.iam.view.BannerCardView*
-dontwarn com.urbanairship.messagecenter.ThemedActivity*
-dontnote com.urbanairship.google.PlayServicesUtils

## Optional dependency on AdId
-dontwarn com.urbanairship.analytics.AnalyticsJobHandler