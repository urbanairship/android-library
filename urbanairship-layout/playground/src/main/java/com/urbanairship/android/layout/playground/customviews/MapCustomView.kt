package com.urbanairship.android.layout.playground.customviews

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

public class CustomMapView: AirshipCustomViewHandler {

    override fun onCreateView(context: Context, args: AirshipCustomViewArguments): View =
        ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    CustomMapView(args)
                }
            }
        }
}

@Composable
fun CustomMapView(args: AirshipCustomViewArguments) {
    val mapType: String = args.properties.requireField("map_type")

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "[MAP] $mapType", style = MaterialTheme.typography.displaySmall)
    }
}
