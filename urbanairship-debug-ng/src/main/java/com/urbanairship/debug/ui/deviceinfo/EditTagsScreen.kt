package com.urbanairship.debug.ui.deviceinfo

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.urbanairship.debug.AirshipDebug
import com.urbanairship.debug.ui.components.DebugCategoryHeader
import com.urbanairship.debug.ui.components.TopAppBar
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun EditTagsScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    onNavigateUp: () -> Unit = {}
) {

    Scaffold(
        topBar = {
            AirshipDebug.TopAppBar(
                title = stringResource(id = DeviceInfoScreens.EditTags.titleRes),
                onNavigateUp = onNavigateUp
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        EditTagScreenContent(
            modifier = modifier.padding(padding),
            onNavigateUp = onNavigateUp
        )
    }
}


@Composable
internal fun EditTagScreenContent(
    modifier: Modifier = Modifier,
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
