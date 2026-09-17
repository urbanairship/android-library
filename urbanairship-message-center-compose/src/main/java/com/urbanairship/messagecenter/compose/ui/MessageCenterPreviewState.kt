/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter.compose.ui

import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.urbanairship.UALog
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.ui.ThomasLayoutViewFactory
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.emptyJsonMap
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.ui.MessageCenterMessageViewModel.State
import java.time.Instant

/**
 * Builds a [MessageCenterMessageState] that renders [layout] directly, without a backing
 * Message Center message.
 *
 * The state is inert: inbox actions (mark read, delete, refresh) are dropped, and no
 * analytics are reported. Intended for previewing a layout in the real message view.
 *
 * @param layout The layout to render.
 * @param title The title shown in the message view's top bar.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
public fun rememberMessageCenterPreviewState(
    layout: AirshipLayout,
    title: String = "Preview"
): MessageCenterMessageState = remember(layout, title) {
    MessageCenterMessageState(
        onAction = { UALog.d { "Preview state ignoring action: $it" } },
        makeAnalytics = { _, _ -> PreviewThomasListener },
        ai = null
    ).apply {
        viewState = State.MessageContent(
            message = previewMessage(title),
            content = State.MessageContent.Content.Native(
                layout = layout,
                // No inbox row to persist view state into.
                store = null
            )
        )
    }
}

/**
 * Stand-in for the inbox message the message view would normally be showing.
 *
 * The ID keys [ThomasLayoutViewFactory]'s cached `LayoutViewModel`, so it has to differ per
 * previewed layout or the second preview re-renders the first one's model.
 */
private fun previewMessage(title: String): Message = Message(
    id = "$PREVIEW_MESSAGE_ID-$title",
    title = title,
    bodyUrl = "",
    sentDate = Instant.now(),
    expirationDate = null,
    isUnread = false,
    extras = null,
    contentType = Message.ContentType.Native(version = 1),
    messageUrl = "",
    reporting = null,
    rawMessageJson = emptyJsonMap().toJsonValue(),
    isDeletedClient = false
)

private const val PREVIEW_MESSAGE_ID = "message-center-layout-preview"

private object PreviewThomasListener : ThomasListenerInterface {
    override fun onDismiss(cancel: Boolean) {
        UALog.d { "Preview onDismiss(cancel: $cancel)" }
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        UALog.d { "Preview onVisibilityChanged(isVisible: $isVisible, isForegrounded: $isForegrounded)" }
    }

    override fun onStateChanged(state: JsonSerializable) {
        UALog.d { "Preview onStateChanged(${state.toJsonValue()})" }
    }

    override fun onReportingEvent(event: ReportingEvent) {
        UALog.d { "Preview onReportingEvent($event)" }
    }
}
