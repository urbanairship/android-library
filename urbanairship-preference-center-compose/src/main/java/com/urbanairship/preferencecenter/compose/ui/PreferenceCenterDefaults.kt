package com.urbanairship.preferencecenter.compose.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.urbanairship.R
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme

/* Contains defaults to be used with Preference Center composables. */
@Stable
public object PreferenceCenterDefaults {

    /**
     * Top bar for the preference center screens.
     *
     * @param title The title to display in the top bar.
     * @param navIcon The navigation icon to display. If null, no navigation icon is displayed.
     * @param navIconDescription The content description for the navigation icon.
     * @param onNavigateUp The callback to be invoked when the navigation icon is clicked.
     */
    @Composable
    public fun topBar(
        title: String,
        navIcon: Painter =
            painterResource(com.urbanairship.preferencecenter.core.R.drawable.ua_ic_preference_center_arrow_back),
        navIconDescription: String? = stringResource(R.string.ua_back),
        onNavigateUp: () -> Unit
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text(text = title) },
            navigationIcon = {
                navIcon?.let {
                    IconButton(onClick = onNavigateUp) {
                        Icon(navIcon, navIconDescription)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = PrefCenterTheme.colors.topBarBackground,
                titleContentColor = PrefCenterTheme.colors.topBarTitleText,
                navigationIconContentColor = PrefCenterTheme.colors.topBarIconTint
            ),
        )
    }
}
