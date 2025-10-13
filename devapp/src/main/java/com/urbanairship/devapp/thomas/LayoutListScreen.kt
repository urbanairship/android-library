package com.urbanairship.devapp.thomas

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.UALog
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.devapp.R
import com.urbanairship.devapp.ui.theme.PreviewTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayoutListScreen(
    layoutType: ThomasLayout.Type,
    onNavigateUp: () -> Unit,
    viewModel: LayoutListScreenViewModel = viewModel {
        DefaultLayoutListScreenViewModel(layoutType)
    }
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val state = viewModel.state.collectAsState().value

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
                    Text(text = layoutType.title())
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
            when(state) {
                is LayoutListScreenViewModel.State.Error -> ErrorView(state.message) {
                    viewModel.loadLayouts(context)
                }
                is LayoutListScreenViewModel.State.Loaded ->
                    ContentView(
                        items = state.layouts,
                        onSelected = { viewModel.display(context, it) }
                    )
                LayoutListScreenViewModel.State.Loading -> {
                    viewModel.loadLayouts(context)
                    LoadingView()
                }
            }
        }
    }
}

@Composable
private fun ContentView(
    items: List<ThomasLayout.LayoutFile>,
    onSelected: (ThomasLayout.LayoutFile) -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onPrimary)
    ) {
        items.forEachIndexed { index, file ->
            if (index > 0) {
                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
            }

            Row(Modifier.fillMaxWidth().clickable { onSelected(file) }) {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = file.filename
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text =message,
            style = MaterialTheme.typography.titleMedium
        )

        Button(onRetry) {
            Text(
                text ="Retry",
            )
        }
    }
}

private fun ThomasLayout.Type.title(): String {
    return when(this) {
        ThomasLayout.Type.SCENE_BANNERS -> "Banners"
        ThomasLayout.Type.SCENE_EMBEDDED -> "Embedded"
        ThomasLayout.Type.SCENE_MODALS -> "Modals"
        ThomasLayout.Type.MESSAGE_BANNERS -> "Banners"
        ThomasLayout.Type.MESSAGE_FULLSCREEN -> "Fullscreen"
        ThomasLayout.Type.MESSAGE_HTML -> "HTML"
        ThomasLayout.Type.MESSAGE_MODAL -> "Modal"
    }
}

internal interface LayoutListScreenViewModel {

    val state: StateFlow<State>
    val error: StateFlow<String?>
    sealed class State {
        data object Loading: State()
        data class Error(val message: String): State()
        data class Loaded(val layouts: List<ThomasLayout.LayoutFile>): State()
    }

    fun display(context: Context, layout: ThomasLayout.LayoutFile)
    fun loadLayouts(context: Context)
}

internal class DefaultLayoutListScreenViewModel(
    val layoutType: ThomasLayout.Type
): LayoutListScreenViewModel, ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val _state = MutableStateFlow<LayoutListScreenViewModel.State>(LayoutListScreenViewModel.State.Loading)
    override val state: StateFlow<LayoutListScreenViewModel.State> = _state.asStateFlow()

    override fun display(context: Context, layout: ThomasLayout.LayoutFile) {
        _error.update { null }

        try {
            layout.display(context)
            LayoutPreferenceManager.shared().addToRecent(layout)
        } catch (ex: IllegalArgumentException) {
            UALog.e(ex) { "Failed to open layout file ${layout.filename}" }
            _error.update { "Invalid layout file: ${layout.filename}" }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to display layout file ${layout.filename}" }
            _error.update { "Failed to display layout file: ${layout.filename}" }
        }
    }

    override fun loadLayouts(context: Context) {
        try {
            val content = ThomasLayoutLoader.shared.load(layoutType, context)
            _state.update { LayoutListScreenViewModel.State.Loaded(content) }
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to load layouts" }
            _state.update {
                LayoutListScreenViewModel.State.Error("Failed to load layouts")
            }
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    PreviewTheme {
        LayoutListScreen(
            layoutType = ThomasLayout.Type.SCENE_MODALS,
            onNavigateUp = {},
            viewModel = object : LayoutListScreenViewModel {
                override val state: StateFlow<LayoutListScreenViewModel.State> = MutableStateFlow(
                    LayoutListScreenViewModel.State.Loaded(listOf(
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
                    )))
                override val error: StateFlow<String?> = MutableStateFlow(null)

                override fun display(context: Context, layout: ThomasLayout.LayoutFile) { }

                override fun loadLayouts(context: Context) { }
            }
        )
    }
}

@Preview("Loading - Light")
@Preview("Loading - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenLoadingPreview() {
    PreviewTheme {
        LayoutListScreen(
            layoutType = ThomasLayout.Type.SCENE_MODALS,
            onNavigateUp = {},
        )
    }
}

@Preview("Error - Light")
@Preview("Error - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenErrorPreview() {
    PreviewTheme {
        LayoutListScreen(
            layoutType = ThomasLayout.Type.SCENE_MODALS,
            onNavigateUp = {},
            viewModel = object : LayoutListScreenViewModel {
                override val state: StateFlow<LayoutListScreenViewModel.State> = MutableStateFlow(
                    LayoutListScreenViewModel.State.Error("Preview error")
                )
                override val error: StateFlow<String?> = MutableStateFlow(null)

                override fun display(context: Context, layout: ThomasLayout.LayoutFile) { }

                override fun loadLayouts(context: Context) { }
            }
        )
    }
}
