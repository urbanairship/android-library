package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.urbanairship.preferencecenter.compose.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Conditions
import com.urbanairship.preferencecenter.data.Section

internal data class SectionItem(
    val section: Section
) : BasePrefCenterItem(TYPE_SECTION) {

    override val id: String = section.id
    override val conditions: Conditions = section.conditions

    val title: String? = section.display.name
    val subtitle: String? = section.display.description

    override fun areItemsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass != otherItem.javaClass) return false
        otherItem as SectionItem
        return id == otherItem.id
    }

    override fun areContentsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as SectionItem

        if (title != otherItem.title) return false
        if (subtitle != otherItem.subtitle) return false

        return true
    }
}

@Composable
internal fun SectionItem.toView() {
    Column(Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        title?.let {
            Text(
                text = it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
        }

        subtitle?.let {
            Text(
                text = it,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp
            )
        }
    }
}

@Preview
@Composable
private fun preview() {
    SectionItem(
        Section.Common(
            id = "id",
            display = CommonDisplay(
                name = "Preview name",
                description = "Description"
            ),
            conditions = emptyList(),
            items = emptyList()
        )
    ).toView()
}
