package com.urbanairship.devapp.home

import androidx.navigation3.runtime.NavEntry
import com.urbanairship.devapp.Destination
import com.urbanairship.devapp.thomas.LayoutsHomeScreen

data object QuickAccess {
    fun restore(value: String): Destination? {
        return when(value) {
            NamedUserScreen.NAME-> NamedUserScreen
            ThomasLayoutsHome.NAME -> ThomasLayoutsHome
            else -> null
        }
    }

    data object NamedUserScreen: Destination {
        const val NAME = "named_user"

        override fun serialize(): String = NAME

        override fun navigationEntry(
            onNavigate: (Destination) -> Unit,
            onPopBackStack: () -> Unit
        ): NavEntry<Destination> {
            return NavEntry(this) {
                NamedUserScreen(
                    onNavigateUp = onPopBackStack
                )
            }
        }
    }

    data object ThomasLayoutsHome: Destination {
        const val NAME = "thomas_layouts"

        override fun serialize(): String = NAME

        override fun navigationEntry(
            onNavigate: (Destination) -> Unit,
            onPopBackStack: () -> Unit
        ): NavEntry<Destination> {
            return NavEntry(this) {
                LayoutsHomeScreen(
                    onNavigate = onNavigate,
                    onNavigateUp = onPopBackStack
                )
            }
        }
    }
}
