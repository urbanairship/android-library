package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.R
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.IconDisplay
import com.urbanairship.preferencecenter.data.Item
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.glide.GlideImage

internal data class AlertItem(
    val item: Item.Alert
): BasePrefCenterItem(TYPE_ALERT) {

    override val conditions: Conditions = item.conditions

    val title = item.iconDisplay.name
    val description = item.iconDisplay.description
    val icon = item.iconDisplay.icon
    val button = item.button
}

@Composable
internal fun AlertItem.Content(
    onClick: (actions: Map<String, JsonValue>) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(PrefCenterTheme.dimens.itemPadding),
    ) {

        icon?.let { url ->
            GlideImage(
                imageModel = { url },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Inside,
                    colorFilter = ColorFilter.tint(PrefCenterTheme.colors.alertIconTint)
                ),
                previewPlaceholder = painterResource(R.drawable.ua_ic_notification_button_info),
                modifier = Modifier.padding(PrefCenterTheme.dimens.alertIconPadding)
                    .size(PrefCenterTheme.dimens.alertIconSize),
            )
        }

        Column(Modifier.fillMaxWidth()) {
            title?.let { text ->
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = PrefCenterTheme.typography.alertTitle,
                    color = PrefCenterTheme.colors.alertTitleText,
                    modifier = Modifier.padding(PrefCenterTheme.dimens.alertItemTitlePadding),
                )
            }

            description?.let { text ->
                Text(
                    text = text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = PrefCenterTheme.typography.alertDescription,
                    color = PrefCenterTheme.colors.alertDescriptionText,
                    modifier = Modifier.padding(PrefCenterTheme.dimens.alertItemDescriptionPadding),
                )
            }

            button?.let { button ->
                Button(
                    onClick = { onClick(button.actions) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrefCenterTheme.colors.alertButtonBackground
                    ),
                    modifier = Modifier.padding(PrefCenterTheme.dimens.alertItemButtonPadding)
                        .semantics { contentDescription = button.contentDescription ?: button.text },
                ) {
                    Text(
                        text = button.text,
                        style = PrefCenterTheme.typography.alertButtonLabel,
                        color = PrefCenterTheme.colors.alertButtonText
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun preview() {
    PreferenceCenterTheme {
        Surface {
            AlertItem(
                item = Item.Alert(
                    id = "id",
                    iconDisplay = IconDisplay(
                        name = "Something went wrong!",
                        description = "Please try again later.",
                        icon = "preview"
                    ),
                    button = com.urbanairship.preferencecenter.data.Button(
                        text = "Retry", contentDescription = null, actions = mapOf()
                    ),
                    conditions = emptyList()
                )
            ).Content(onClick = { })
        }
    }
}
