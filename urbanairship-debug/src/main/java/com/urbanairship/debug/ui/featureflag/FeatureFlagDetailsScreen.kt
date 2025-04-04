/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.featureflag

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.JsonItem
import com.urbanairship.debug.ui.components.LoadingView
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.json.JsonSerializable
import DefaultFeatureFlagModel
import FeatureFlagViewModel
import kotlinx.coroutines.flow.map
import org.json.JSONObject

@Composable
internal fun FeatureFlagDetailsScreen(
    name: String,
    viewModel: FeatureFlagViewModel = viewModel<DefaultFeatureFlagModel>(),
    onNavigateUp: () -> Unit = {}
) {
    DebugScreen(
        title = name,
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        FeatureFlagDetailsScreenContent(viewModel = viewModel, name = name)
    }
}

@Composable
internal fun FeatureFlagDetailsScreenContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: FeatureFlagViewModel,
    name: String
) {
    val item = viewModel.featureFlags
        .map { it.firstOrNull { flag -> flag.name == name } }
        .collectAsState(initial = null)
        .value

    val flag = viewModel.evaluatedFlag.collectAsState(initial = null).value
    val context = LocalContext.current

    LaunchedEffect(key1 = name) {
        viewModel.evaluateFlag(name)
    }

    item?.let { entry ->
        LazyColumn {
            item {
                Section(title = "Details") {
                    HorizontalDivider()
                    RowItem(title = "Name", details = item.name)
                }
            }

            item {
                Spacer(modifier = Modifier.padding(bottom = 32.dp))
                Section(
                    title = "Result",
                    accessory = {
                        Row(
                            modifier = Modifier.height(20.dp)) {
                            IconButton(
                                enabled = flag != null,
                                onClick = {
                                    val content = flag?.toJsonValue() ?: return@IconButton
                                    viewModel.shareJson(context, content)
                                }) {

                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                            IconButton(
                                enabled = flag != null,
                                onClick = { flag?.name?.let(viewModel::evaluateFlag) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Re-evaluate")
                            }
                        }
                    }) {

                    if (flag != null) {
                        HorizontalDivider()
                        RowItem(title = "Eligible", details = "${flag.isEligible}")
                        RowItem(title = "Exists", details = "${flag.exists}")
                        flag.variables?.let { variables ->
                            RowItem(title = "Variables:", addDivider = false)
                            JsonItem(jsonSerializable = variables)
                            HorizontalDivider()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { viewModel.trackInteraction(flag) }) {
                                Text(text = "Track Interaction", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            LoadingView()
                        }
                    }

                }
            }

            items(entry.flags) { info ->
                Spacer(modifier = Modifier.padding(bottom = 32.dp))

                Section(
                    title = info.id,
                    accessory = {
                        IconButton(
                            modifier = Modifier.height(20.dp),
                            enabled = flag != null,
                            onClick = { viewModel.shareJson(context, info.payload) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    ) {
                    HorizontalDivider()
                    JsonItem(jsonSerializable = info.payload)
                    HorizontalDivider()
                }
            }
        }
    } ?: LoadingView()
}

internal fun JsonSerializable.toFormattedJsonString(): String {
    return JSONObject(this.toJsonValue().toString()).toString(4)
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        FeatureFlagDetailsScreen(
            name = "multiple",
            viewModel = FeatureFlagViewModel.forPreview()
        )
    }
}
