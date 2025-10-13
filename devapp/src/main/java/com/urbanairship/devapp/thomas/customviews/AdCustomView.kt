package com.urbanairship.devapp.thomas.customviews

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.urbanairship.android.layout.AirshipCustomViewArguments
import com.urbanairship.android.layout.AirshipCustomViewHandler
import com.urbanairship.json.requireField

class CustomAdView: AirshipCustomViewHandler {

    override fun onCreateView(context: Context, args: AirshipCustomViewArguments): View =
        ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    ComposeAdView(args)
                }
            }
        }
}

@Composable
fun ComposeAdView(args: AirshipCustomViewArguments) {
    val adType: String = args.properties.requireField("ad_type")

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "[AD] $adType", style = MaterialTheme.typography.displaySmall)
    }
}
