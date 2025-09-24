package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterOptions
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.data.Conditions

internal data class DescriptionItem(
    val title: String?,
    val description: String?
) : BasePrefCenterItem(TYPE_DESCRIPTION) {

    override val conditions: Conditions = emptyList()
}

@Composable
internal fun DescriptionItem.Content() {
    Column(
        Modifier.fillMaxWidth().padding(PrefCenterTheme.dimens.itemPadding)
    ) {
        // We don't always show the title because it can be displayed in the title bar
        if (PrefCenterTheme.options.showTitleItem) {
            title?.let { values ->
                Text(
                    text = values,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = PrefCenterTheme.typography.title,
                    color = PrefCenterTheme.colors.titleText,
                    modifier = Modifier.padding(PrefCenterTheme.dimens.titlePadding)
                )
            }
        }

        description?.let { values ->
            Text(
                text = values,
                style = PrefCenterTheme.typography.description,
                color = PrefCenterTheme.colors.titleText,
                modifier = Modifier.padding(PrefCenterTheme.dimens.descriptionPadding)
            )
        }
    }
}

@Preview
@Composable
private fun preview() {
    PreferenceCenterTheme(
        options = PreferenceCenterOptions(showTitleItem = true)
    ) {
        Surface {
            DescriptionItem(title = "title", description = "description").Content()
        }
    }
}

@Preview
@Composable
private fun previewNoTitle() {
    PreferenceCenterTheme {
        Surface {
            DescriptionItem(title = "title", description = "description").Content()
        }
    }
}
