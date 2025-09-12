package com.urbanairship.messagecenter.compose

internal enum class MessageCenterScreen(val route: String) {
    Root("/"),
    MessageList("/message-list"),
    ShowMessage("/message/{messageId}");

    fun createRoute(targetId: String): String {
        return when(this) {
            ShowMessage -> route.replace("{messageId}", targetId)
            else -> route
        }
    }
}
