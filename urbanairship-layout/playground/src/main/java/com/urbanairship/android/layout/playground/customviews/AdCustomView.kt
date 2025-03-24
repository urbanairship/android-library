package com.urbanairship.android.layout.playground.customviews

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.urbanairship.android.layout.AirshipCustomViewArguments
import com.urbanairship.android.layout.AirshipCustomViewHandler
import com.urbanairship.android.layout.playground.R
import com.urbanairship.json.JsonMap
import com.urbanairship.json.requireField

public class CustomAdView: AirshipCustomViewHandler {

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
