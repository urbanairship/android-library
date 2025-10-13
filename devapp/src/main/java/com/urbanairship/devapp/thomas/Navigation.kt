package com.urbanairship.devapp.thomas

import androidx.navigation3.runtime.NavEntry
import com.urbanairship.devapp.Destination

data object ThomasLayoutNavigation {

    fun restore(value: String): Destination? {
        return if (value.startsWith(LayoutsList.NAME)) {
            LayoutsList.restore(value)
        } else {
            null
        }
    }

    internal data class LayoutsList(
        val type: ThomasLayout.Type
    ): Destination {

        companion object {
            const val NAME = "list"

            fun restore(value: String): LayoutsList? {
                val parts = value.split("/")
                if (parts.size != 2 || parts[0] != NAME) {
                    return null
                }

                val type = ThomasLayout.Type.entries.firstOrNull { it.name == parts[1] }
                    ?: return null

                return LayoutsList(type)
            }
        }

        override fun serialize(): String {
            return listOf(NAME, type.name).joinToString(separator = "/")
        }

        override fun navigationEntry(
            onNavigate: (Destination) -> Unit,
            onPopBackStack: () -> Unit
        ): NavEntry<Destination> {
            return NavEntry(this) {
                LayoutListScreen(
                    layoutType = type,
                    onNavigateUp = onPopBackStack
                )
            }
        }

    }
}
