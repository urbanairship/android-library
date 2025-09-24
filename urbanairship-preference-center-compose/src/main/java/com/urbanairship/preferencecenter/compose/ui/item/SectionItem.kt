package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Section

internal data class SectionItem(
    val section: Section
) : BasePrefCenterItem(TYPE_SECTION) {

    override val conditions: Conditions = section.conditions

    val title: String? = section.display.name
    val subtitle: String? = section.display.description
}

@Composable
internal fun SectionItem.Content() {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(PrefCenterTheme.dimens.itemPadding)
    ) {
        title?.let {
            Text(
                text = it,
                style = PrefCenterTheme.typography.itemTitle,
                color = PrefCenterTheme.colors.accent,
                modifier = Modifier.padding(PrefCenterTheme.dimens.itemTitlePadding)
            )
        }

        subtitle?.let {
            Text(
                text = it,
                style = PrefCenterTheme.typography.itemDescription,
                color = PrefCenterTheme.colors.itemDescriptionText,
                modifier = Modifier.padding(PrefCenterTheme.dimens.itemDescriptionPadding)
            )
        }
    }
}

@Preview
@Composable
private fun preview() {
    PreferenceCenterTheme {
        Surface {
            SectionItem(
                Section.Common(
                    id = "id", display = CommonDisplay(
                        name = "Preview name", description = "Description"
                    ), conditions = emptyList(), items = emptyList()
                )
            ).Content()
        }
    }
}
