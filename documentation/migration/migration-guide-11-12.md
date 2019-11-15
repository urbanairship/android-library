# Airship Android SDK Migration Guide

## UrbanAirship Library 11.x to 12.0.0

### Breaking Changes

The following classes have moved to a different package:

```
com.urbanairship.push.NamedUser => com.urbanairship.channel.NamedUser
com.urbanairship.push.TagEditor => com.urbanairship.channel.TagEditor
com.urbanairship.push.TagGroupsEditor => com.urbanairship.channel.TagGroupsEditor
```

### PushManager Deprecations

Channel functionality, such as setting tags, has been moved out of `PushManager` and into
its own class, `AirshipChannel`. The corresponding `PushManager` functionality has been deprecated

*Setting Tags*

```java
// 11.x Device Tags
UAirship.shared()
        .getPushManager()
        .editTags()
        .addTag("foo")
        .apply();

// 12.x Device Tags
UAirship.shared()
        .getChannel()
        .editTags()
        .addTag("foo")
        .apply();

// 11.x Tag Groups
UAirship.shared()
        .getPushManager()
        .editTagGroups()
        .addTag("foo", "bar")
        .apply();

// 12.x Tag Groups
UAirship.shared()
        .getChannel()
        .editTagGroups()
        .addTag("foo", "bar")
        .apply();
```

*Channel Registration*

```java
// 11.x
UAirship.shared().getPushManager().enableChannelCreation();
UAirship.shared().getPushManager().setChannelTagRegistrationEnabled(true);

// 12.x
UAirship.shared().getChannel().enableChannelCreation();
UAirship.shared().getChannel().setChannelTagRegistrationEnabled(true);
```

*Listening for registration*

`com.urbanairship.push.RegistrationListener` has been deprecated and replaced with
`com.urbanairship.push.PushTokenListener` and `com.urbanairship.channel.AirshipChannelListener`.

```java
// 11.x
UAirship.shared().getPushManager().addRegistrationListener(new RegistrationListener() {
    @Override
    public void onChannelCreated(@NonNull String channelId) {

    }

    @Override
    public void onChannelUpdated(@NonNull String channelId) {

    }

    @Override
    public void onPushTokenUpdated(@NonNull String token) {

    }
});

// 12.x
UAirship.shared().getChannel().addChannelListener(new AirshipChannelListener() {
    @Override
    public void onChannelCreated(@NonNull String channelId) {

    }

    @Override
    public void onChannelUpdated(@NonNull String channelId) {

    }
});

UAirship.shared().getPushManager().addPushTokenListener(token -> {

});
```


