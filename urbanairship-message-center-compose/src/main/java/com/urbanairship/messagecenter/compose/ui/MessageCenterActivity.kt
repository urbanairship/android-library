package com.urbanairship.messagecenter.compose.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.urbanairship.Airship
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.compose.MessageCenterNavigationHost
import com.urbanairship.messagecenter.compose.theme.MessageCenterListConfig
import com.urbanairship.messagecenter.compose.theme.MessageCenterListConfig.ListItemConfig
import com.urbanairship.messagecenter.compose.theme.MessageCenterTheme
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

        setContent {
            MessageCenterTheme {
                MessageCenterNavigationHost(
                    messageIdToDisplay = messageIdToOpen.asStateFlow(),
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
