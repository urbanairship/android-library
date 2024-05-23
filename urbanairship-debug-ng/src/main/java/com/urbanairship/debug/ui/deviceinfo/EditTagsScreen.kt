package com.urbanairship.debug.ui.deviceinfo

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.debug.ui.components.DebugCategoryHeader
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun EditTagsScreen(
    onNavigateUp: () -> Unit = {}
) {
    DebugScreen(
        title = stringResource(id = DeviceInfoScreens.EditTags.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        EditTagScreenContent(
            onNavigateUp = onNavigateUp
        )
    }
}


@Composable
internal fun EditTagScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    onNavigateUp: () -> Unit
) {
    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        modifier = modifier
    ) {
        item {
            DebugCategoryHeader("Edit Tags Features")
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditTagsScreenPreview() {
    AirshipDebugTheme {
        EditTagsScreen()
    }
}
