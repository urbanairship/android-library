package com.urbanairship.preferencecenter.compose.ui.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.preferencecenter.data.Conditions
import java.util.UUID

internal data class DescriptionItem(
    val title: String?,
    val description: String?
) : BasePrefCenterItem(TYPE_DESCRIPTION) {

    override val id: String = UUID.randomUUID().toString()
    override val conditions: Conditions = emptyList()

    override fun areItemsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (this === otherItem) return true
        if (javaClass == otherItem.javaClass) return false
        // There should only be one description item, so two description items are always the same.
        return true
    }

    override fun areContentsTheSame(otherItem: BasePrefCenterItem): Boolean {
        if (javaClass != otherItem.javaClass) return false
        otherItem as DescriptionItem

        if (title == null && otherItem.title != null) return false
        if (description == null && otherItem.description != null) return false

        return true
    }
}

@Composable
internal fun DescriptionItem.toView() {
    Column(Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp)
        .padding(horizontal = 16.dp)
    ) {
        title?.let { values ->
            Text(
                text = values,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        }

        description?.let { values ->
            Text(
                text = values,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                fontSize = MaterialTheme.typography.titleMedium.fontSize
            )
        }
    }
}

@Preview
@Composable
private fun preview() {
    DescriptionItem(
        title = "title",
        description = "description"
    ).toView()
}
