package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme
import com.urbanairship.preferencecenter.compose.ui.theme.PreferenceCenterTheme
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Section

internal data class SectionBreakItem(
    val section: Section.SectionBreak
) : BasePrefCenterItem(TYPE_SECTION_BREAK) {

    override val conditions: Conditions = section.conditions
    val label: String? = section.display.name
}

@Composable
internal fun SectionBreakItem.Content() {
    if (label.isNullOrEmpty()) {
        return
    }

    SuggestionChip(
        modifier = Modifier.padding(PrefCenterTheme.dimens.itemPadding),
        onClick = { },
        enabled = false,
        colors = SuggestionChipDefaults.suggestionChipColors().copy(
            disabledContainerColor = PrefCenterTheme.colors.sectionLabelBackground
        ),
        shape = PrefCenterTheme.shapes.sectionLabel,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = PrefCenterTheme.typography.sectionLabel,
                color = PrefCenterTheme.colors.sectionLabelText
            )
        }
    )
}

@Preview
@Composable
private fun preview() {
    PreferenceCenterTheme {
        Surface {
            SectionBreakItem(
                Section.SectionBreak(
                    id = "id",
                    display = CommonDisplay(name = "SMS"),
                    conditions = emptyList()
                )
            ).Content()
        }
    }
}
