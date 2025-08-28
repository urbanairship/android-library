package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.IconDisplay
import com.urbanairship.preferencecenter.data.Item
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage

internal data class AlertItem(
    val item: Item.Alert
): BasePrefCenterItem(TYPE_ALERT) {

    override val id: String = item.id
    override val conditions: Conditions = item.conditions

    val title = item.iconDisplay.name
    val description = item.iconDisplay.description
    val icon = item.iconDisplay.icon
    val button = item.button

    override fun areItemsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass != otherItem.javaClass) return false
        otherItem as AlertItem
        return id == otherItem.id
    }

    override fun areContentsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as AlertItem

        if (title != otherItem.title) return false
        if (description != otherItem.description) return false
        if (icon != otherItem.icon) return false
        if (button != otherItem.button) return false

        return true
    }
}

@Composable
internal fun AlertItem.toView(
    onClick: (actions: Map<String, JsonValue>) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        icon?.let { url ->
            GlideImage(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp)
                    .size(width = 72.dp, height = 72.dp),
                imageModel = { url },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Inside,
                    colorFilter = ColorFilter.tint(colorResource(R.color.ua_preference_center_divider_color))
                ),
            )
        }

        Column(Modifier.fillMaxWidth()) {
            title?.let { text ->
                Text(
                    modifier = Modifier.padding(bottom = 4.dp),
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            description?.let { text ->
                Text(
                    modifier = Modifier.padding(bottom = 4.dp),
                    text = text,
                    maxLines = 3,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            button?.let { button ->
                Button(
                    modifier = Modifier.semantics { contentDescription = button.contentDescription ?: "" },
                    onClick = { onClick(button.actions) }
                ) {
                    Text(
                        text = button.text,
                        color = colorResource(R.color.ua_preference_center_alert_button_text)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun preview() {
    AlertItem(
        item = Item.Alert(
            id = "id",
            iconDisplay = IconDisplay(
                name = "preview",
                description = "preview",
                icon = "preview"
            ),
            button = com.urbanairship.preferencecenter.data.Button(
                text = "action button",
                contentDescription = "preview",
                actions = mapOf()
            ),
            conditions = emptyList()
        )
    ).toView(
        onClick = { }
    )
}
