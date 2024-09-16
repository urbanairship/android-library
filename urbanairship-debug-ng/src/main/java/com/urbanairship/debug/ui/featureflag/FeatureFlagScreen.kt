import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.AirshipDispatchers
import com.urbanairship.debug.DebugManager
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.featureflag.FeatureFlagScreens
import com.urbanairship.debug.ui.featureflag.toFormattedJsonString
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.featureflag.FeatureFlag
import com.urbanairship.featureflag.FeatureFlagManager
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalMap
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
internal fun FeatureFlagScreen(
    viewModel: FeatureFlagViewModel = viewModel<DefaultFeatureFlagModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {

    DebugScreen(
        title = stringResource(id = FeatureFlagScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        FeatureFlagContent(viewModel = viewModel, onNavigate = onNavigate)
    }
}

@Composable
internal fun FeatureFlagContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: FeatureFlagViewModel,
    onNavigate: (String) -> Unit = {}
) {
    val items by viewModel.featureFlags.collectAsState(initial = listOf())

    LazyColumn(modifier = modifier) {
        items(
            count = items.size,
            key = { index -> items[index].name },
            itemContent = { index ->
                val item = items[index]

                ListItem(
                    modifier = modifier.clickable {
                        onNavigate("${FeatureFlagScreens.Details.route}/${item.name}")
                    },
                    headlineContent = {
                        Text(text = item.name, fontWeight = FontWeight.Medium)
                    }, trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Entries ${item.flags.size}",
                                fontWeight = FontWeight.Light)

                            Icon(Icons.Default.ChevronRight, contentDescription = "Show details")
                        }
                    }
                )

                HorizontalDivider()
            }
        )
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        FeatureFlagScreen(
            viewModel = FeatureFlagViewModel.forPreview()
        )
    }
}

internal interface FeatureFlagViewModel {
    val featureFlags: Flow<List<FeatureFlagEntry>>
    val evaluatedFlag: StateFlow<FeatureFlag?>
    val error: StateFlow<String?>

    fun evaluateFlag(name: String)
    fun trackInteraction(flag: FeatureFlag)
    fun shareJson(context: Context, content: JsonValue)

    companion object {
        internal fun forPreview(): FeatureFlagViewModel {
            return object : FeatureFlagViewModel {
                override val featureFlags: Flow<List<FeatureFlagEntry>> = MutableStateFlow(
                    listOf(
                        FeatureFlagEntry("single", listOf(
                            FeatureFlagEntry.Info("single-1", JsonValue.wrap("single value"))
                        )),
                        FeatureFlagEntry("multiple", listOf(
                            FeatureFlagEntry.Info("mul-1", JsonValue.wrap("multiple value")),
                            FeatureFlagEntry.Info("mul-2", JsonValue.wrap("multiple value"))
                        )),
                    )
                )

                override val evaluatedFlag: StateFlow<FeatureFlag?> = MutableStateFlow(
                    FeatureFlag(true, true, null)
                )

                override val error: StateFlow<String?> = MutableStateFlow(null)

                override fun evaluateFlag(name: String) { }
                override fun trackInteraction(flag: FeatureFlag) { }
                override fun shareJson(context: Context, content: JsonValue) { }
            }
        }
    }
}

internal class DefaultFeatureFlagModel(
    remoteData: RemoteData = DebugManager.shared().remoteData,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
): FeatureFlagViewModel, ViewModel() {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private var evaluatedFlagState = MutableStateFlow<FeatureFlag?>(null)
    private var errorsFlow = MutableStateFlow<String?>(null)

    override val evaluatedFlag: StateFlow<FeatureFlag?> = evaluatedFlagState
    override val error: StateFlow<String?> = errorsFlow

    override val featureFlags: Flow<List<FeatureFlagEntry>> = remoteData.payloadFlow(PAYLOAD_TYPE)
        .map { payloads ->
            payloads.map { payload ->
                val flags = payload.data.opt(PAYLOAD_TYPE)
                    .optList()
                    .mapNotNull { it.map }

                val namedFlags = flags
                    .groupBy { entry ->
                        entry.optionalMap("flag")?.get("name")?.getString(MISSING_NAME)
                            ?: MISSING_NAME
                    }

                namedFlags.map { entry ->
                    FeatureFlagEntry(
                        name = entry.key,
                        flags = entry.value.map { payload ->
                            FeatureFlagEntry.Info(
                                id = payload.opt("flag_id").getString(MISSING_ID),
                                payload = payload.toJsonValue()
                            )
                        }
                    )
                }
            }
                .flatten()
                .sortedBy { it.name }
        }

    companion object {
        private const val PAYLOAD_TYPE = "feature_flags"
        private const val MISSING_NAME = "MISSING_NAME"
        private const val MISSING_ID = "MISSING_ID"
    }

    override fun evaluateFlag(name: String) {
        evaluatedFlagState.update { null }
        errorsFlow.update { null }

        scope.launch {
            val result = FeatureFlagManager.shared().flag(name)
            evaluatedFlagState.update { result.getOrNull() }
            errorsFlow.update { result.exceptionOrNull()?.message }
        }
    }

    override fun trackInteraction(flag: FeatureFlag) {
        FeatureFlagManager.shared().trackInteraction(flag)
    }

    override fun shareJson(context: Context, content: JsonValue) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, content.toFormattedJsonString())

        ContextCompat.startActivity(
            context,
            Intent.createChooser(intent, "Share"),
            null
        )
    }
}

internal data class FeatureFlagEntry(
    val name: String,
    val flags: List<Info>
) {
    data class Info(
        val id: String,
        val payload: JsonValue
    )
}
