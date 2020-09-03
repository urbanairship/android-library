# Airship Android SDK 13.x to 14.0 Migration Guide

Airship SDK 14 is a major update that prepares our automation module to support future IAA enhancements,
revamps the Channel Capture tool, and provides other improvements. The majority of apps will only be
affected by the `Whitelist` to `UrlAllowList` behavior changes.

## Whitelist is now UrlAllowList

---
### WARNING (Behavior Change)
**The `enableUrlWhitelisting` config key has been removed. OpenURL-scoped URLs are now always verified.
This will likely cause the Open URL and Landing Page actions to no longer work if you are using the
default for that key (`false`). To fix this issue, add a wildcard (`*`) entry to the
new `urlAllowListScopeOpenURL` config option. This will allow any URLs for those actions.**
---

We have renamed `Whitelist` to `UrlAllowList`. In addition to the class, many properties, methods
and constants have been renamed.

There are now three UrlAllowList config keys that can be added to `AirshipConfig`:
- `urlAllowList`: The list used to validate which URLs can be opened or can load the JavaScript native bridge. This key was previously named `whitelist`.
- `urlAllowListScopeOpenURL`: The list used to validate which URLs can be opened.
- `urlAllowListScopeJavaScriptInterface`: The list used to validate which URLs can load the JavaScript native bridge.

```java
    // 13.x
    UAirship.shared().getWhitelist().addEntry("*");
    UAirship.shared().getWhitelist().setWhitelistCallback((url, scope) -> {
        return true;
    });

    // 14.x
    UAirship.shared().getUrlAllowList().addEntry("*");
    UAirship.shared().getUrlAllowList().setUrlAllowListCallback((url, scope) -> {
        return true;
    });
```

## In-App Automation Changes

Action Automation and In-App Messaging have been combined into `InAppAutomation`. `ActionScheduleInfo`, `ActionSchedule`,
`InAppMessageScheduleInfo`, and `InAppMessageSchedule` have been replaced with `Schedule<Actions>` and `Schedule<InAppMessage>`.
All scheduling is now done through the `InAppAutomation` instance. The `InAppMessageManager` instance accessor
has been moved to `InAppAutomation#getInAppMessageManager()`.

### In-App Message Audience

The audience is no longer defined on an `InAppMessage`, instead you can define it on the `Schedule`. The Audience class has been moved from
`com.urbanairship.iam.Audience` to `com.urbanairship.automation.Audience`.

### In-App Message Identifier

The `InAppMessage` class no longer defines an ID. Instead the ID for the schedule will be used as the message ID. You can now optionally define
the schedule ID when creating a `Schedule`. 

Note: The first time SDK 14 runs it will migrate existing in-app messages, setting the schedule identifier
of each existing schedule to the message's identifier, as long as the message identifiers are unique.
If your app's message identifiers are all unique, they can be used as the schedule identifiers when
accessing schedules after migration. If they are not unique, the original message ID is stored as metadata
for the schedule and it will be set as the schedule's group. If your message identifiers are not unique,
follow this process to map your existing message identifiers to the new schedule identifiers:

1. During the migration, the existing message identifier and schedule identifier will be added to the metadata for each schedule, under the keys `com.urbanairship.original_message_id` and `com.urbanairship.original_schedule_id`, respectively.
2. After the migration, your code can use the methods in the [Retrieving In-App Automation Schedules](#retrieving-in-app-automation-schedules) section below to loop through all schedules, mapping your app's previous message and schedule identifiers to the new schedule identifiers.

### InAppMessageManager

Setting display interval, listeners, display adapters, and other in-app message specific configurations
are still applied through the `InAppMessageManager`. The accessor moved to `InAppAutomation#getInAppMessageManager()`.

```java
    // 13.x
    InAppMessageManager.shared().setDisplayInterval(10, TimeUnit.SECONDS);

    // 14.x
    InAppAutomation.shared().getInAppMessageManager().setDisplayInterval(10, TimeUnit.SECONDS);
```

### Scheduling Automations

All scheduling from InAppMessageManager and ActionAutomation have been consolidated into `InAppAutomation`. Instead
of passing the info, you now pass it the actual `Schedule` instance. 

Actions:
```java
    // 13.x:
    ActionScheduleInfo actionSchedule = ActionScheduleInfo.newBuilder()
                                                          .addTrigger(trigger)
                                                          .addAction("sample_action", JsonValue.wrap("sample_action_value"))
                                                          .setGroup("group_name")
                                                          .build();
    ActionSchedule schedule = ActionAutomation.shared().schedule(actionSchedule).get();
    
    
    // 14.x:
    Schedule<Actions> actionsSchedule = Schedule.newBuilder(Actions.newBuilder()
                                                                   .addAction("sample_action", "sample_action_value")
                                                                   .build())
                                                .addTrigger(trigger)
                                                .setGroup("group_name")
                                                .build();
    
    InAppAutomation.shared().schedule(actionsSchedule);
```

In-App Message:
```java
    // 13.x
    InAppMessage message = InAppMessage.newBuilder()
                                       .setDisplayContent(displayContent)
                                       .setId("message id")
                                       .build();

    InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                    .setMessage(message)
                                                                    .addTrigger(trigger)
                                                                    .build();
    
    InAppMessageSchedule schedule = InAppMessageManager.shared().scheduleMessage(scheduleInfo).get();

    // 14.x
    InAppMessage message = InAppMessage.newBuilder()
                                       .setDisplayContent(displayContent)
                                       .build();

    Schedule<InAppMessage> messageSchedule = Schedule.newBuilder(message)
                                                     .addTrigger(trigger)
                                                     .setId("message id")
                                                     .setGroup("group_name")
                                                     .build();

    InAppAutomation.shared().schedule(messageSchedule);
```

### Cancelling In-App Automation Schedules
```java
    // 13.x
    ActionAutomation.shared().cancel("schedule ID");
    ActionAutomation.shared().cancelGroup("group");
    InAppMessageManager.shared().cancelSchedule("schedule ID");
    ActionAutomation.shared().cancelMessage("group");
    
    // 14.x
    InAppAutomation.shared().cancelSchedule("schedule ID");
    InAppAutomation.shared().cancelScheduleGroup("group");
```

### Retrieving In-App Automation Schedules

Retrieving a schedule by ID:

```java
    // 13.x
    PendingResult<ActionSchedule> actionPendingResult = ActionAutomation.shared().getSchedule(actionScheduleId);
    PendingResult<InAppMessageSchedule> messagePendingResult = InAppMessageManager.shared().getSchedule(messageScheduleId);
    
    // 14.x
    PendingResult<Schedule<Actions>> actionPendingResult = InAppAutomation.shared().getActionSchedule(actionScheduleId);
    PendingResult<Schedule<InAppMessage>> messagePendingResult = InAppAutomation.shared().getMessageSchedule(messageScheduleId);
```

Retrieving a schedule by group:

```java
    // 13.x
    PendingResult<Collection<ActionSchedule>> actionGroupResult = ActionAutomation.shared().ActionAutomation("action group");
    PendingResult<Collection<InAppMessageSchedule>> messageGroupResult = InAppMessageManager.shared().getSchedules("message group");
    
    
    // 14.x
    PendingResult<Collection<Schedule<Actions>>> actionGroupResult = InAppAutomation.shared().getActionScheduleGroup("action group");
    PendingResult<Collection<Schedule<InAppMessage>>> messageGroupResult = InAppAutomation.shared().getMessageScheduleGroup("message group");
```

### Legacy In-App Messages

The `ScheduleInfoBuilderExtender` has been replaced by `ScheduleBuilderExtender`:

```java
    // 13.x
    LegacyInAppMessageManager.shared().setScheduleBuilderExtender(new LegacyInAppMessageManager.ScheduleInfoBuilderExtender() {
        @NonNull
        @Override
        public InAppMessageScheduleInfo.Builder extend(@NonNull Context context, @NonNull InAppMessageScheduleInfo.Builder builder, @NonNull LegacyInAppMessage legacyMessage) {
            // modify builder
            return builder;
        }
    });

    // 14.x
    LegacyInAppMessageManager.shared().setScheduleBuilderExtender(new LegacyInAppMessageManager.ScheduleBuilderExtender() {
        @NonNull
        @Override
        public Schedule.Builder<InAppMessage> extend(@NonNull Context context, @NonNull Schedule.Builder<InAppMessage> builder, @NonNull LegacyInAppMessage legacyMessage) {
            // modify builder
            return builder;
        }
    });
```

### Landing Page Action

The method `LandingPageAction#extendSchedule(InAppMessageScheduleInfo.Builder)` has been replaced with
`LandingPageAction#extendSchedule(Schedule.Builder)`.

```java
    // 13.x
    @Override
    protected InAppMessageScheduleInfo.Builder extendSchedule(@NonNull InAppMessageScheduleInfo.Builder builder) {
        // modify builder
        return builder;
    }
    
    // 14.x
    @Override
    protected Schedule.Builder<InAppMessage> extendSchedule(@NonNull Schedule.Builder<InAppMessage> builder) {
        // modify builder
        return builder;
    }
```

## Deprecation Removals

We have removed all code that was deprecated and targeted for removal in SDK 14. The following sections 
list what was removed and include recommended replacement functionality.

### Custom Events

The method `CustomEvents#addProperty(String, Collection)` to add a list of strings as a property has been removed
and replaced with a less restrictive method `CustomEvent#addProperty(String, JsonSerializable)}` that allows
adding any JSON values as properties.

```java
    // 13.x
    CustomEvent event = CustomEvent.newBuilder("event name")
                    .addProperty("neat", strings)
                    .build();
    // 14.x
    CustomEvent event = CustomEvent.newBuilder("event name")
                    .addProperty("neat", JsonValue.wrapOpt(strings))
                    .build();
```

### Push Manager

Any channel related functionality has been deprecated on Push Manager for SDK 13, and now has been removed.

Replacements:
  - `PushManager#getChannelTagRegistrationEnabled()` -> `AirshipChannel#getChannelTagRegistrationEnabled()`
  - `PushManager#editTagGroups()` -> `AirshipChannel#editTagGroups()`
  - `PushManager#editTags()` -> `AirshipChannel#editTags()`
  - `PushManager#getChannelId()` -> `AirshipChannel#getId()`
  - `PushManager#getRegistrationToken()` -> `AirshipChannel#getPushToken()`
