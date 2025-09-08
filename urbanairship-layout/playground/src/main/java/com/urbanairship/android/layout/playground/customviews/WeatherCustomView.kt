package com.urbanairship.android.layout.playground.customviews

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
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
import com.urbanairship.json.requireField

/** XML-based weather view. */
class CustomWeatherViewXml(context: Context) : FrameLayout(context) {
    init {
        inflate(context, R.layout.custom_view_weather, this)
    }

    fun bind(args: AirshipCustomViewArguments) {
        val condition = findViewById<TextView>(R.id.condition)
        condition.text = args.properties.requireField("weather_type")
    }
}

/** Wrapper for [ComposeWeatherView]. */
class CustomWeatherView: AirshipCustomViewHandler {

    override fun onCreateView(context: Context, args: AirshipCustomViewArguments): View = ComposeView(context).apply {
        setContent {
            MaterialTheme {
                ComposeWeatherView(args)
            }
        }
    }
}

/** Compose weather view. */
@Composable
fun ComposeWeatherView(args: AirshipCustomViewArguments) {
    val condition: String = args.properties.requireField("weather_type")

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Portland, OR", style = MaterialTheme.typography.displaySmall)

        Row(modifier = Modifier.fillMaxWidth()) {

            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "70°F", style = MaterialTheme.typography.headlineMedium)
                Text(text = "63°F", style = MaterialTheme.typography.headlineSmall)
            }

            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_thunderstorm_24),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Text(text = condition, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }

}
