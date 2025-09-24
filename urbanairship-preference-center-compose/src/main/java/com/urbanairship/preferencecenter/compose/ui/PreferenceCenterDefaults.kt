package com.urbanairship.preferencecenter.compose.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.urbanairship.R
import com.urbanairship.preferencecenter.compose.ui.theme.PrefCenterTheme

/* Contains defaults to be used with Preference Center composables. */
@Stable
public object PreferenceCenterDefaults {
    @Composable
    public fun topBar(
        title: String,
        navIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
        navIconDescription: String = stringResource(R.string.ua_back),
        onNavigateUp: () -> Unit
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text(text = title) },
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(navIcon, navIconDescription)
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
