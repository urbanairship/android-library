package com.urbanairship.debug.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.jsonMapOf

@Composable
internal fun JsonItem(
    jsonSerializable: JsonSerializable,
    modifier: Modifier = Modifier
) {
    val json = jsonSerializable.toJsonValue()
    if (json.isJsonMap) {
        Column(
            modifier = modifier
        ) {
            var isExpanded by rememberSaveable { mutableStateOf(false) }

            json.requireMap().map.forEach {
                if (it.value.isJsonMap || it.value.isJsonList) {
                    ListItem(
                        headlineContent = {
                            Text(text = it.key, fontWeight = FontWeight.Medium)
                        },
                        trailingContent = {
                            IconButton(onClick = { isExpanded = !isExpanded }) {
                                val icon = if (isExpanded) {
                                    Icons.Outlined.ArrowDropDown
                                } else {
                                    Icons.Outlined.ArrowDropUp
                                }
                                Icon(
                                    imageVector = icon, contentDescription = null
                                )
                            }
                        }
                    )

                    if (isExpanded) {
                        Box(modifier = modifier.padding(start = 8.dp)) {
                            JsonItem(jsonSerializable = it.value)
                        }
                    }
                } else {
                    ListItem(
                        headlineContent = {
                            Text(text = "${it.key}: ${it.value.toNiceString()}", modifier, fontWeight = FontWeight.Medium)
                        }
                    )
                }
            }

        }
    } else if (json.isJsonList) {
        Column(modifier = modifier) {
            json.requireList().forEach {
                JsonItem(jsonSerializable = it)
            }
        }
    } else {
        ListItem(
            headlineContent = {
                Text(text = json.toNiceString(), modifier, fontWeight = FontWeight.Medium)
            }
        )
    }
}

private fun JsonSerializable.toNiceString() : String {
    val json = this.toJsonValue()
    if (json.isString) {
        return json.toString()
    }

    if (json.isBoolean) {
        return if (json.getBoolean(false)) { "true" } else { "false" }
    }

    return json.toString()
}


@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JsonItemPreview() {
    AirshipDebugTheme {
        DebugScreen("JSON") {
            JsonItem(
                jsonSerializable = jsonMapOf(
                    "string" to "bar",
                    "boolean" to true,
                    "number" to 1,
                    "object" to jsonMapOf("foo" to "bar", "cool" to "story"),
                    "array" to listOf("1", "2", "3")
                )
            )
        }
    }

}
