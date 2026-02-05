package com.urbanairship.messagecenter.compose.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.urbanairship.Airship
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterTheme
import com.urbanairship.messagecenter.messageCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Activity to display a message center */
public class MessageCenterActivity: ComponentActivity() {

    private val messageIdToOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        Autopilot.automaticTakeOff(application)

        if (!Airship.isTakingOff && !Airship.isFlying) {
            UALog.e("MessageCenterActivity - unable to create activity, takeOff not called.")
            finish()
            return
        }

        messageIdToOpen.update { MessageCenter.parseMessageId(intent) }

        // Load the theme (or the default theme if none has been set)
        val theme = Airship.messageCenter.theme

        setContent {
            val messageId = messageIdToOpen.collectAsStateWithLifecycle()

            MessageCenterTheme(
                colors = if (isSystemInDarkTheme()) theme.darkColors else theme.lightColors,
                typography = theme.typography,
                dimens = theme.dimens,
                options = theme.options
            ) {
                MessageCenterScreen(
                    state = rememberMessageCenterState(messageId = messageId.value),
                    onNavigateUp = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        MessageCenter.parseMessageId(intent)?.let { id ->
            messageIdToOpen.update { id }
        }
    }
}
