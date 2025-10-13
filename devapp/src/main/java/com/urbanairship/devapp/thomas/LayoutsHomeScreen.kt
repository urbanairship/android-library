package com.urbanairship.devapp.thomas

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.UALog
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.devapp.Destination
import com.urbanairship.devapp.R
import com.urbanairship.devapp.ui.theme.PreviewTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayoutsHomeScreen(
    onNavigate: (Destination) -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: LayoutsViewerViewModel = viewModel()
) {

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val recent = if (LocalInspectionMode.current) {
        LayoutsViewerViewModel.previewRecent()
    } else {
        viewModel.recent.collectAsState().value
    }

    val error = viewModel.error.collectAsState().value
    if (error != null) {
        LaunchedEffect(error) {
            scope.launch {
                snackbarHostState.showSnackbar(error)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.layout_viewer_title))
                },
                navigationIcon = {
                    IconButton(onNavigateUp) {
                        Image(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddings ->
        Surface(Modifier.padding(paddings)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {

                RecentFilesList(
                    layouts = recent,
                    onSelected = { item ->
                        viewModel.display(item, context)
                    }
                )

                Spacer(Modifier.height(16.dp))

                viewModel.sections.forEach { section ->
                    LayoutSectionView(
                        section = section,
                        onSelected = { category ->
                            onNavigate(ThomasLayoutNavigation.LayoutsList(category.layoutType) )
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RecentFilesList(
    layouts: List<ThomasLayout.LayoutFile>,
    onSelected: (ThomasLayout.LayoutFile) -> Unit
) {
    Column {
        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = stringResource(R.string.thomas_recent_title),
            style = MaterialTheme.typography.titleLarge
        )

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onPrimary)
        ) {
            layouts.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                }
                Row(
                    modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable { onSelected(item) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        text = item.filename,
                        style = MaterialTheme.typography.bodyLarge,
                        )
                }
            }
        }
    }
}

@Composable
private fun LayoutSectionView(
    section: LayoutsViewerViewModel.Section,
    onSelected: (LayoutsViewerViewModel.Category) -> Unit
) {

    Column {
        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = section.title,
            style = MaterialTheme.typography.titleLarge
        )

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onPrimary)
        ) {
            section.items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable(item.isEnabled) { onSelected(item) },
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        modifier = Modifier.padding(end = 4.dp, start = 12.dp),
                        painter = painterResource(item.icon),
                        contentDescription = item.name
                    )

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )

                    Spacer(Modifier.weight(1f))

                    Image(
                        modifier = Modifier.padding(end = 12.dp),
                        painter = painterResource(R.drawable.ic_chevron),
                        contentDescription = "show more"
                    )
                }
            }
        }
    }
}

internal class LayoutsViewerViewModel: ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val recent: StateFlow<List<ThomasLayout.LayoutFile>>
        get() = LayoutPreferenceManager.shared().recentFiles

    val sections: List<Section> = listOf(
        Section(
            title = "Scenes",
            items = listOf(
                Category(
                    name = "Embedded",
                    icon = R.drawable.ic_layout_embedded,
                    layoutType = ThomasLayout.Type.SCENE_EMBEDDED,
                    isEnabled = false
                ), Category(
                    name = "Modal",
                    icon = R.drawable.ic_layout_modal,
                    layoutType = ThomasLayout.Type.SCENE_MODALS
                ), Category(
                    name = "Banner",
                    icon = R.drawable.ic_layout_banner,
                    layoutType = ThomasLayout.Type.SCENE_BANNERS
                )
            )
        ),
        Section(
            title = "In-App Automations",
            items = listOf(
                Category(
                    name = "Modal",
                    icon = R.drawable.ic_layout_modal,
                    layoutType = ThomasLayout.Type.MESSAGE_MODAL
                ),
                Category(
                    name = "Banner",
                    icon = R.drawable.ic_layout_banner,
                    layoutType = ThomasLayout.Type.MESSAGE_BANNERS
                ),
                Category(
                    name = "Fullscreen",
                    icon = R.drawable.ic_layout_fullscreen,
                    layoutType = ThomasLayout.Type.MESSAGE_FULLSCREEN
                ),
                Category(
                    name = "HTML",
                    icon = R.drawable.ic_layout_html,
                    layoutType = ThomasLayout.Type.MESSAGE_HTML
                )
            )
        )
    )

    fun display(file: ThomasLayout.LayoutFile, context: Context) {
        _error.update { null }

        try {
            file.display(context)
        } catch (ex: IllegalArgumentException) {
            UALog.e(ex) { "Failed to open layout file ${file.filename}" }
            _error.update { "Invalid layout file: ${file.filename}" }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to display layout file ${file.filename}" }
            _error.update { "Failed to display layout file: ${file.filename}" }
        }
    }

    data class Category(
        val icon: Int,
        val name: String,
        val layoutType: ThomasLayout.Type,
        val isEnabled: Boolean = true
    )

    data class Section(
        val title: String,
        val items: List<Category>
    )

    companion object {
        fun previewRecent(): List<ThomasLayout.LayoutFile> {
            return listOf(
                ThomasLayout.LayoutFile(
                    assetsPath = "mock-1",
                    filename = "test-file.json",
                    type = ThomasLayout.Type.SCENE_EMBEDDED
                ),
                ThomasLayout.LayoutFile(
                    assetsPath = "mock-2",
                    filename = "test-file2.yaml",
                    type = ThomasLayout.Type.SCENE_EMBEDDED
                )
            )
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    PreviewTheme {
        LayoutsHomeScreen(
            onNavigate = { _ -> },
            onNavigateUp = {},
        )
    }
}
