/* Copyright Airship and Contributors */
package com.urbanairship.devapp.embedded

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.urbanairship.UALog
import com.urbanairship.devapp.R
import com.urbanairship.devapp.thomas.ThomasLayout
import com.urbanairship.automation.compose.AirshipEmbeddedCarousel
import com.urbanairship.automation.compose.AirshipEmbeddedCarouselDefaults
import com.urbanairship.automation.compose.AirshipEmbeddedView
import com.urbanairship.devapp.ai.DevAI
import com.urbanairship.embedded.AirshipEmbeddedFilter
import com.urbanairship.embedded.AirshipEmbeddedInfo
import com.urbanairship.embedded.AirshipEmbeddedSelection
import com.urbanairship.embedded.EmbeddedViewManager

/** The embedded ID every `ai-*.yml` fixture targets. */
internal const val AI_DEMO_EMBEDDED_ID: String = "ai_demo"

private const val PROMPT =
    "Show the offer that best matches the user's interests and recent behaviour."

private const val USAGE = "embedded_selection"

/**
 * Exercises [AirshipEmbeddedSelection.ByAi] against the `ai-*.yml` samples.
 *
 * Candidates are queued from this screen rather than the layout viewer, so the view stays
 * composed while the pending set changes. The test plan behind the top bar's button covers
 * what to try.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmbeddedAiScreen(onNavigateUp: () -> Unit) {
    var useAi by remember { mutableStateOf(true) }
    var allowInterruptions by remember { mutableStateOf(false) }
    var excludeDogs by remember { mutableStateOf(false) }
    var priorityFirst by remember { mutableStateOf(false) }
    var showCarousel by remember { mutableStateOf(false) }
    var unstableFilter by remember { mutableStateOf(false) }
    var recomposeCount by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val evaluations by DevAI.evaluations.collectAsState()
    val rankCount = evaluations[USAGE] ?: 0

    // Remembered, so it is the same instance across recompositions. A selection that changed
    // identity every frame would restart collection and, for ByAi, discard the ranking with it.
    val selection = remember(useAi, allowInterruptions, priorityFirst) {
        if (!useAi) {
            AirshipEmbeddedSelection.Priority
        } else {
            AirshipEmbeddedSelection.ByAi(
                config = AirshipEmbeddedSelection.ByAi.Config(
                    prompt = PROMPT,
                    strategy = if (priorityFirst) {
                        AirshipEmbeddedSelection.ByAi.Strategy.PRIORITY_THEN_SCORE
                    } else {
                        AirshipEmbeddedSelection.ByAi.Strategy.SCORE_THEN_PRIORITY
                    },
                    allowDisplayInterruptions = allowInterruptions
                ),
                fallback = AirshipEmbeddedSelection.ByAi.Fallback.Priority
            )
        }
    }

    // Two filters that behave identically but differ in stability, which is the whole point of
    // the "unstable filter" switch: the unremembered one is a new lambda on every
    // recomposition, so it restarts the collection underneath the view. With the session held
    // by the state holder rather than the flow, that restart should cost nothing visible —
    // no placeholder, and no new evaluation.
    val stableFilter: AirshipEmbeddedFilter? = remember(excludeDogs) {
        if (excludeDogs) {
            { info: AirshipEmbeddedInfo -> !info.mentionsDogs }
        } else {
            null
        }
    }
    val filter: AirshipEmbeddedFilter? = if (unstableFilter) {
        // Deliberately not remembered. Referencing the counter keeps it from being hoisted.
        { info: AirshipEmbeddedInfo ->
            recomposeCount >= 0 && (!excludeDogs || !info.mentionsDogs)
        }
    } else {
        stableFilter
    }

    var showTestPlan by remember { mutableStateOf(false) }
    if (showTestPlan) {
        EmbeddedAiTestPlanSheet(onDismiss = { showTestPlan = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.embedded_ai_title))
                },
                navigationIcon = {
                    IconButton(onNavigateUp) {
                        Image(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showTestPlan = true }) { Text("Test plan") }
                }
            )
        }
    ) { paddings ->
        Column(
            Modifier
                .padding(paddings)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Queue two or more candidates below, then watch the model rank them. " +
                        "Dog clearance has the best priority and the worst match, so it should " +
                        "only lead when the model was never asked.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Single view", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    AirshipEmbeddedView(
                        embeddedId = AI_DEMO_EMBEDDED_ID,
                        selection = selection,
                        filterInstances = filter,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "placeholder — nothing selected",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    )
                }
            }

            if (showCarousel) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Carousel — renders the whole ordered list",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        AirshipEmbeddedCarousel(
                            embeddedId = AI_DEMO_EMBEDDED_ID,
                            selection = selection,
                            filterInstances = filter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            previousArrow = AirshipEmbeddedCarouselDefaults.previousArrow(),
                            nextArrow = AirshipEmbeddedCarouselDefaults.nextArrow(),
                            indicator = AirshipEmbeddedCarouselDefaults.dotsIndicator,
                            placeholder = null
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Queued from here rather than the layout viewer so the view above stays composed
            // while the pending set changes. Navigating away to queue would dispose the state
            // holder, and with it the session, which is the thing under test.
            Text("Queue a candidate", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AI_SAMPLES.forEach { sample ->
                    OutlinedButton(
                        onClick = { sample.queue(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(sample.label)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Evaluations: $rankCount", style = MaterialTheme.typography.titleMedium)
            Text(
                "Recompositions forced: $recomposeCount",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { recomposeCount += 1 }) { Text("Recompose") }
                OutlinedButton(onClick = { DevAI.resetEvaluationCounts() }) { Text("Reset count") }
                OutlinedButton(
                    onClick = { EmbeddedViewManager.dismissAll(AI_DEMO_EMBEDDED_ID) }
                ) { Text("Dismiss all") }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            SettingSwitch("Use ByAi", "Off falls back to Priority", useAi) { useAi = it }
            SettingSwitch(
                "allowDisplayInterruptions",
                "Off (default): a chosen instance holds until dismissed",
                allowInterruptions
            ) { allowInterruptions = it }
            SettingSwitch(
                "PRIORITY_THEN_SCORE",
                "Off: SCORE_THEN_PRIORITY",
                priorityFirst
            ) { priorityFirst = it }
            SettingSwitch(
                "Filter out dog content",
                "Excluded instances shouldn't display, page, or reach the prompt",
                excludeDogs
            ) { excludeDogs = it }
            SettingSwitch(
                "Unstable filter lambda",
                "A new lambda each recomposition — tap Recompose and watch the count",
                unstableFilter
            ) { unstableFilter = it }
            SettingSwitch("Show carousel", "Adds a second surface on this ID", showCarousel) {
                showCarousel = it
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The manual test plan, next to the controls it talks about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmbeddedAiTestPlanSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text("Manual test plan", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(TEST_PLAN_PREAMBLE, style = MaterialTheme.typography.bodyMedium)

            EMBEDDED_AI_TEST_PLAN.forEach { case ->
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    "${case.id} · ${case.title}",
                    style = MaterialTheme.typography.titleMedium
                )
                case.covers?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                case.setup?.let {
                    Spacer(Modifier.height(8.dp))
                    TestPlanBlock("Setup", it)
                }

                Spacer(Modifier.height(8.dp))
                case.steps.forEachIndexed { index, step ->
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))
                TestPlanBlock("Pass", case.pass)
                case.fail?.let {
                    Spacer(Modifier.height(6.dp))
                    TestPlanBlock("Fail", it)
                }
                case.note?.let {
                    Spacer(Modifier.height(6.dp))
                    TestPlanBlock("Note", it)
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(TEST_PLAN_FOOTNOTE, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TestPlanBlock(label: String, body: String) {
    Column {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Matches the dog sample, which is the competing item against DevAI's cat-leaning context. */
private val AirshipEmbeddedInfo.mentionsDogs: Boolean
    get() = contentDescription?.contains("dog", ignoreCase = true) == true

/** One of the `ai-*.yml` fixtures, queueable without leaving this screen. */
private class AiSample(val label: String, private val file: String) {

    fun queue(context: Context) {
        try {
            ThomasLayout.LayoutFile(
                assetsPath = "sample_layouts/Scenes/Embedded/$file",
                filename = file,
                type = ThomasLayout.Type.SCENE_EMBEDDED
            ).display(context)
        } catch (e: Exception) {
            // Most likely the fixture isn't on this device: Scenes/ is git-ignored and
            // `fetchLayouts` clears it.
            UALog.e(e) { "Failed to queue $file" }
            Toast.makeText(context, "Missing or invalid: $file", Toast.LENGTH_LONG).show()
        }
    }
}

private val AI_SAMPLES = listOf(
    AiSample("Cat sale · priority 20 · best match", "ai-1-cat-sale.yml"),
    AiSample("Dog clearance · priority 0 · worst match", "ai-2-dog-sale.yml"),
    AiSample("Loyalty · priority 10 · mid", "ai-3-loyalty.yml"),
    AiSample("Free shipping · priority 30 · malformed context", "ai-4-malformed-context.yml")
)

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
