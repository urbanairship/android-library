package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.preferencecenter.core.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Section

internal data class SectionBreakItem(
    val section: Section.SectionBreak
) : BasePrefCenterItem(TYPE_SECTION_BREAK) {

    override val id: String = section.id
    override val conditions: Conditions = section.conditions
    val label: String? = section.display.name

    override fun areItemsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass != otherItem.javaClass) return false
        otherItem as SectionBreakItem
        return id == otherItem.id
    }

    override fun areContentsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as SectionBreakItem

        if (label != otherItem.label) return false

        return true
    }
}

@Composable
internal fun SectionBreakItem.toView() {
    SuggestionChip(
        modifier = Modifier
            .padding(start = 16.dp, top = 10.dp, end = 16.dp),
        onClick = { },
        enabled = false,
        colors = SuggestionChipDefaults.suggestionChipColors().copy(
            disabledContainerColor = colorResource(R.color.ua_preference_center_section_break_label_background),
            labelColor = Color.White,
        ),
        shape = MaterialTheme.shapes.large,
        label = {
            Text(
                text = label ?: "",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Preview
@Composable
private fun preview() {
    SectionBreakItem(
        section = Section.SectionBreak(
            id = "id",
            display = CommonDisplay(name = "name"),
            conditions = emptyList()
        )
    ).toView()
}
