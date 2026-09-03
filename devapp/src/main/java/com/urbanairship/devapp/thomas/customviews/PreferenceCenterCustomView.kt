package com.urbanairship.devapp.thomas.customviews

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.urbanairship.android.layout.AirshipCustomViewArguments
import com.urbanairship.android.layout.AirshipCustomViewHandler
import com.urbanairship.json.requireField
import com.urbanairship.preferencecenter.compose.ui.PreferenceCenterContent
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterColors
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme

class PreferenceCenterCustomView : AirshipCustomViewHandler {

    override fun onCreateView(context: Context, args: AirshipCustomViewArguments): View =
        ComposeView(context).apply {
            setContent { PrefCenterEmbed(args) }
        }
}

@Composable
private fun PrefCenterEmbed(args: AirshipCustomViewArguments) {
    val id: String = args.properties.requireField("pref_center_id")

    PreferenceCenterTheme(
        colors = if (isSystemInDarkTheme()) {
            PreferenceCenterColors.darkDefaults()
        } else {
            PreferenceCenterColors.lightDefaults()
        }
    ) {
        PreferenceCenterContent(identifier = id, modifier = Modifier.fillMaxWidth())
    }
}
