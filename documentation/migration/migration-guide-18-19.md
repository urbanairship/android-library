# Airship Android SDK 18.x to 19.0 Migration Guide

## Compile and Target SDK Versions

Urban Airship now requires `compileSdk` version 35 (Android 15) or higher, and `minSdkVersion` 23 (Android 6.0) or higher.

Please update the `build.gradle` file:

###### Groovy
```groovy
android {
    compileSdk 35

    defaultConfig {
        minSdk 23
        targetSdk 35
        // ...
    }
}
```

## Privacy Manager

Privacy Manager has been updated to support management of Airship Feature Flags via the new `Feature` value: `PrivacyManager.Feature.FEATURE_FLAGS`

## Message Center

Several Message Center APIs have been changed to asynchronous access.

### Inbox API Changes

| SDK 18.x                                           | SDK 19.0                                                                                                                                                                                       |
|----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MessageCenter.shared().inbox.getMessages()`       | `MessageCenter.shared().inbox.getMessages()` (suspending)<br/>`MessageCenter.shared().inbox.getMessagesFlow()`<br/>`MessageCenter.shared().inbox.getMessagesPendingResult()`                   |
| `MessageCenter.shared().inbox.getUnreadMessages()` | `MessageCenter.shared().inbox.getUnreadMessages()` (suspending)<br/>`MessageCenter.shared().inbox.getUnreadMessagesFlow()`<br/>`MessageCenter.shared().inbox.getUnreadMessagesPendingResult()` |
| `MessageCenter.shared().inbox.getReadMessages()`   | `MessageCenter.shared().inbox.getReadMessages()` (suspending)<br/>`MessageCenter.shared().inbox.getReadMessagesFlow()`<br/>`MessageCenter.shared().inbox.getReadMessagesPendingResult()`       |
| `MessageCenter.shared().inbox.fetchMessages()`     | `MessageCenter.shared().inbox.fetchMessages()` (suspending)<br/>`MessageCenter.shared().inbox.fetchMessages(FetchMessagesCallback)`                                                            |                                                           |

Methods to mark messages as read/unread and delete messages are unchanged.

#### Example: Get Inbox Messages

```kotlin
// SDK 18.x
val messages = MessageCenter.shared().inbox.messages

// SDK 19.0 (Suspend API)
scope.launch {
    val messages = MessagesCenter.shared().inbox.getMessages()
}

// SDK 19.0 (Flow API)
scope.launch {
    // Collect the messages flow, which emits a new list whenever the inbox is updated
    MessagesCenter.shared().inbox.getMessagesFlow().collect { messages ->
        // ...
    }
}

// SDK 19.0 (Callback API)
val result = MessagesCenter.shared().inbox.getMessagesPendingResult()
result.addResultCallback { messages ->
    // ...
}
```

#### Example: Refresh Inbox Messages

```kotlin
// SDK 18.x
MessageCenter.shared().inbox.fetchMessages { success ->
    // ...
}

// SDK 19.0 (Suspend API)
scope.launch {
    MessageCenter.shared().inbox.fetchMessages()
}
```

#### Example: Message Counts

###### Kotlin

```kotlin
// SDK 18.x
val totalCount = MessageCenter.shared().inbox.count
val unreadCount = MessageCenter.shared().inbox.unreadCount
val readCount = MessageCenter.shared().inbox.readCount

// SDK 19.0 (Suspend API)
scope.launch {
    val totalCount = MessageCenter.shared().inbox.getCount()
    val unreadCount = MessageCenter.shared().inbox.getUnreadCount()
    val readCount = MessageMessageCenter.shared().inbox.getReadCount()
}
```

###### Java

```java
// SDK 18.x
int totalCount = MessageCenter.shared().getInbox().getCount();
int unreadCount = MessageCenter.shared().getInbox().getUnreadCount();
int readCount = MessageCenter.shared().getInbox().getReadCount();

// SDK 19.0
PendingResult<List<Message>> totalResult = MessageCenter.shared().getInbox().getMessagesPendingResult();
totalResult.addResultCallback(messages -> {
    int totalCount = messages.size();
    // ...
});

PendingResult<List<Message>> unreadResult = MessageCenter.shared().getInbox().getUnreadMessagesPendingResult();
unreadResult.addResultCallback(messages -> {
    int unreadCount = messages.size();
    // ...
});

PendingResult<List<Message>> readResult = MessageCenter.shared().getInbox().getReadMessagesPendingResult();
readResult.addResultCallback(messages -> {
    int readCount = messages.size();
    // ...
});
```

### Message Center Customization

#### Activity and Fragments

Message Center Activity and Fragments have been rewritten and moved to the `ui` package:

| SDK 18.x                                               | SDK 19.0                                                        |
|--------------------------------------------------------|-----------------------------------------------------------------|
| `com.urbanairship.messagecenter.MessageCenterActivity` | `com.urbanairship.messagecenter.ui.MessageCenterActivity`       | 
| `com.urbanairship.messagecenter.MessageActivity`       | `com.urbanairship.messagecenter.ui.MessageActivity`             | 
| `com.urbanairship.messagecenter.MessageCenterFragment` | `com.urbanairship.messagecenter.ui.MessageCenterFragment`       | 
| `com.urbanairship.messagecenter.MessageListFragment`   | `com.urbanairship.messagecenter.ui.MessageCenteListFragment`    | 
| `com.urbanairship.messagecenter.MessageFragment`       | `com.urbanairship.messagecenter.ui.MessageCenteMessageFragment` | 

#### Listening for Inbox Updates

The `InboxListener` remains available in SDK 19.0, but there are now additional Flow-based APIs that may be more convenient for certain use cases implemented in Kotlin. See above for more info.

```
// SDK 18.x (and SDK 19.0)
MessageCenter.shared().inbox.addListener(object: InboxListener {
    override fun onInboxUpdated() {
        // ...
    }
})

// SDK 19.0 (Flow APIs)
scope.launch {
    MessagesCenter.shared().inbox.getMessagesFlow().collect { messages ->
        // ...
    }
}
```

#### Styling the Message Center

Custom styling for Message Center is now done primarily via XML resource overrides, with the exception of a few theme attributes:

| SDK 18.x                           | SDK 19.0                              | Description                                                | 
|------------------------------------|---------------------------------------|------------------------------------------------------------|
| `messageCenterItemIconEnabled`     | `messageCenterItemIconsEnabled`       | Whether to display message thumbnails in the message list. |
| `messageCenterItemIconPlaceholder` | `messageCenterPlaceholderIcon`        | Drawable to display when a message does not have an icon.  |
| `messageCenterDividerColor`        | `dividerColor` set via Material Theme | Divider color, if dividers are enabled.                    |
| n/a                                | `messageCenterItemDividersEnabled`    | Whether to display dividers between messages.              |
| n/a                                | `messageCenterItemDividerInsetStart`  | Divider start inset, if dividers are enabled.              |
| n/a                                | `messageCenterItemDividerInsetEnd`    | Divider end inset, if dividers are enabled.                |
| n/a                                | `messageCenterToolbarTitle`           | Title for the Message Center toolbar.                      |

The following SDK 18.x theme attributes can be replaced with XML resource overrides for SDK 19.0:

| SDK 18.x                                  | SDK 19.0                                                                                                                                                                                                  | Description                                     |
|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| `messageCenterItemBackground`             | `UrbanAirship.MessageCenter.Item.Content`                                                                                                                                                                 | Background drawable for each message list item. |
| `messageCenterItemTitleTextAppearance`    | `UrbanAirship.MessageCenter.TextAppearance.MessageTitle`<br/>`UrbanAirship.MessageCenter.TextAppearance.MessageTitle.Unread`<br/>`UrbanAirship.MessageCenter.TextAppearance.MessageTitle.Read`            | Text appearance for the message title.          |
| `messageCenterItemDateTextAppearance`     | `UrbanAirship.MessageCenter.TextAppearance.MessageSentDate`<br/>`UrbanAirship.MessageCenter.TextAppearance.MessageSentDate.Unread`<br/>`UrbanAirship.MessageCenter.TextAppearance.MessageSentDate.Unread` | Text appearance for the message date.           |
| `messageNotSelectedTextAppearance`        | `UrbanAirship.MessageCenter.Empty`                                                                                                                                                                        | Text appearance for message view empty text.    |
| `messageNotSelectedText`                  | `UrbanAirship.MessageCenter.Empty`                                                                                                                                                                        | String for message view empty text.             |
| `messageCenterEmptyMessageTextAppearance` | `UrbanAirship.MessageCenter.Empty`                                                                                                                                                                        | Text appearance for message list empty text.    |
| `messageCenterEmptyMessageText`           | `UrbanAirship.MessageCenter.Empty`                                                                                                                                                                        | String for message list empty text.             |

The top level `messageCenterStyle` attribute has also been removed.

```xml
<!-- SDK 18.x -->
<style name="AppTheme" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- ... -->
    <item name="messageCenterStyle">@style/AppTheme.MessageCenter</item>
</style>
    
<!-- Custom Message Center style -->
<style name="AppTheme.MessageCenter" parent="MessageCenter">
    <item name="messageCenterItemTitleTextAppearance">@style/AppTheme.MessageCenter.TitleTextAppearance</item>
    <item name="messageCenterItemDateTextAppearance">@style/AppTheme.MessageCenter.DateTextAppearance</item>
    <item name="messageCenterItemIconEnabled">true</item>
</style>

<!-- Custom message title text style -->
<style name="AppTheme.MessageCenter.TitleTextAppearance" parent="TextAppearance.MaterialComponents.Subtitle1" />

<!-- Custom message date text style -->
<style name="AppTheme.MessageCenter.DateTextAppearance" parent="TextAppearance.MaterialComponents.Body2" />

<!-- SDK 19.0 -->
<style name="AppTheme" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- ... -->
</style>

<!-- Custom Message Center style (using AppTheme colors as a base) -->
<style name="UrbanAirship.MessageCenter" parent="AppTheme">
    <!-- Set custom toolbar title "Inbox" -->
    <item name="messageCenterToolbarTitle">@string/inbox</item>
    <!-- Show message thumbnails in the message list -->
    <item name="messageCenterIconsEnabled">true</item>
</style>

<!-- Custom message title text appearance -->
<style name="UrbanAirship.MessageCenter.TextAppearance.MessageTitle" parent="TextAppearance.Material3.TitleMedium">
    <item name="android:textStyle">italic</item>
</style>

<!-- Custom message date text appearance -->
<style name="UrbanAirship.MessageCenter.TextAppearance.MessageSentDate" parent="TextAppearance.Material3.BodySmall">
    <item name="android:textStyle">italic</item>
    <item name="android:textColor">?android:textColorSecondary</item>
</style>
```

#### Embedding Message Center

##### Embedding `MessageCenterFragment`

The default Message Center UI can be embedded in any `FragmentActivity` or `Fragment` via `MessageCenterFragment`, which contains both the list and message views and maintains its own `Toolbar`.

```kotlin
class InboxFragment : MessageCenterFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Optional: Set up the toolbar, if desired.
        listFragment?.toolbar?.let {
            setupWithNavController(it, findNavController(view))
        }
    }
}
```

##### Embedding `MessageCenterListFragment` and `MessageCenterMessageFragment`

For more control over the UI, `MessageCenterListFragment` and `MessageCenterMessageFragment` can be used to embed the list and message views separately, each maintaining its own `Toolbar`.

This example assumes that Jetpack Navigation is being used to navigate between the list and message views, but any navigation method can be used.

```kotlin
class InboxListFragment() : MessageCenterListFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(messageCenterR.menu.ua_message_center_list_pane_menu)

        // Set up the toolbar, if desired.
        setupWithNavController(toolbar, findNavController())

        onMessageClickListener = OnMessageClickListener {
            // Handle message clicks by navigating to the message fragment 
            // (or replace with custom navigation logic).
            findNavController().navigate(
                R.id.action_messageCenterFragment_to_messageFragment, bundleOf(
                    MessageCenterMessageFragment.ARG_MESSAGE_ID to it.id,
                    MessageCenterMessageFragment.ARG_MESSAGE_TITLE to it.title
                )
            )
        }
    }
}
```

```kotlin
class InboxMessageFragment : MessageCenterMessageFragment(R.layout.fragment_inbox_message) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar?.run {
            // Inflate the default menu
            inflateMenu(messageCenterR.menu.ua_message_center_message_pane_menu)

            // Set up the toolbar, if desired.
            setupWithNavController(toolbar, findNavController(view))
        }

        // Handle message deletion from the message view
        onMessageDeletedListener = OnMessageDeletedListener {
            // Handle message deletion by navigating back to the message list fragment 
            // (or replace with custom navigation logic).
            findNavController().popBackStack()

            // Optionally show a toast confirmation message
            context?.run {
                val msg = getQuantityString(messageCenterR.plurals.ua_mc_description_deleted, 1, 1)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
```

## Preference Center

The Preference Center UI has been updated to use Material 3 as the base theme, and to add support edge-to-edge display on Android 15 (API 35).

If you have customized the Preference Center UI on SDK 18.x, it is strongly recommended to verify that your customizations are working as expected on SDK 19.0.
In most cases, no updates should be needed in your custom styles, but if any overrides reference `MaterialComponents` style or text appearance directly as their `parent`, these should be updated to point to an equivalent `Material3` style / text appearance.