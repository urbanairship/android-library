package com.urbanairship.android.layout.playground.embedded

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.urbanairship.UALog
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.EmbeddedPlacement
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.automation.compose.AirshipEmbeddedView
import com.urbanairship.embedded.AirshipEmbeddedObserver
import com.urbanairship.embedded.EmbeddedViewManager
import com.urbanairship.json.emptyJsonMap
import kotlinx.coroutines.launch

class EmbeddedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
          EmbeddedPlaygroundContent(
              onNavigateUp = { finish() },
              modifier = Modifier.fillMaxSize()
          )
        }

        val observer = AirshipEmbeddedObserver("playground")
        observer.listener = AirshipEmbeddedObserver.Listener {
            UALog.v("LISTENER - Embedded view info updated: $it")
        }

        lifecycleScope.launch {
            observer.embeddedViewInfoFlow.collect {
                UALog.v("FLOW - Embedded view info updated: $it")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedPlaygroundContent(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmbeddedViewLoader()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Embedded Playground") },
                navigationIcon = {
                    IconButton(onClick = { onNavigateUp() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "close")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val (embeddedView, button) = createRefs()

            AirshipEmbeddedView(
                embeddedId = "playground",
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .fillMaxWidth(0.9f)
                    .constrainAs(embeddedView) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(button.top)
                    }
                    .padding(16.dp)
            )

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            Button(
                onClick = {
                    scope.launch {
                        displayLayout(context, randomLayout(context), "playground")
                    }
                },
                modifier = Modifier
                    .constrainAs(button) {
                        top.linkTo(embeddedView.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom, margin = 16.dp)
                    }
            ) {
                Text("Enqueue Random Layout")
            }
        }
    }
}

@Composable
fun EmbeddedViewLoader(
    filename: String = randomLayout(LocalContext.current),
    embeddedId: String = "playground"
) {
    val context = LocalContext.current
    LaunchedEffect(key1 = filename) {
        displayLayout(context, filename, embeddedId)
    }
}

fun randomLayout(context: Context): String {
    val layouts = try {
        context.assets.list("sample_layouts")
    } catch (e: java.io.IOException) {
        UALog.e(e, "Failed to list embedded layouts!")
        null
    }
    return layouts?.random() ?: "embedded-single.json"
}

private fun displayLayout(context: Context, fileName: String, embeddedId: String = "playground") {
    val listener = EmbeddedLayoutListener(fileName)
    try {
        val jsonMap = ResourceUtils.readJsonAsset(context, "sample_layouts/$fileName")
        if (jsonMap == null) {
            UALog.e("Failed to display layout! Not a valid JSON object: '$fileName'")
            Toast.makeText(context, "Not a valid JSON object", Toast.LENGTH_LONG).show()
            return
        }
        val payload = LayoutInfo(jsonMap).copy(
            // Hack presentation to embedded
            presentation = EmbeddedPresentation(
                embeddedId = embeddedId,
                defaultPlacement = EmbeddedPlacement(
                    size = ConstrainedSize("100%", "100%", null, null, null, null),
                    margin = null,
                    border = Border(0, 2, Color(android.graphics.Color.RED, emptyList())),
                    backgroundColor = null
                ),
                placementSelectors = null
            ),
        )

        val manager = EmbeddedViewManager

        manager.dismissAll(embeddedId)

        Thomas.prepareDisplay(payload, emptyJsonMap(), manager)
            .setInAppActivityMonitor(GlobalActivityMonitor.shared(context.applicationContext))
            .setListener(listener)
            .display(context)
    } catch (e: Exception) {
        UALog.e(e)
        Toast.makeText(context, "Error trying to display layout", Toast.LENGTH_LONG).show()
    }
}
