package com.urbanairship.devapp.thomas

import AirshipTheme
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.urbanairship.Airship
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.automation.compose.AirshipEmbeddedView
import com.urbanairship.devapp.tv.isTelevision
import com.urbanairship.devapp.ui.theme.AirshipTvTheme
import com.urbanairship.embedded.EmbeddedViewManager
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Displays a single Thomas scene, launched by intent so a layout can be previewed without
 * tapping through the app. Older in-app message payloads under `sample_layouts/Messages` are
 * rejected — they're still previewable by tapping through the layout list.
 *
 * ```
 * adb push scene.yml /sdcard/Android/data/com.urbanairship.sample/files/agent_layouts/scene.yml
 * adb shell am start -n com.urbanairship.sample/com.urbanairship.devapp.thomas.PreviewActivity \
 *     --es layout_path scene.yml
 * adb logcat -s ThomasPreview:I -d
 * ```
 *
 * `layout_path` is resolved against the pushed-layouts directory first and the bundled
 * `sample_layouts` assets second, so it also takes paths like `Messages/Modal/example.json`.
 */
internal class PreviewActivity : AppCompatActivity() {

    private val state = MutableStateFlow<PreviewState>(PreviewState.Loading)
    private var embeddedId: String? = null
    private var latestRequest = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTelevision = isTelevision()
        if (isTelevision) {
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        }
        super.onCreate(savedInstanceState)

        setContent {
            val content = @Composable { PreviewContent(state.collectAsState().value) }
            if (isTelevision) {
                AirshipTvTheme(content)
            } else {
                AirshipTheme(content = content)
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissEmbedded()
    }

    private fun handleIntent(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_LAYOUT_PATH)
        if (path.isNullOrEmpty()) {
            fail(EVENT_PARSE_ERROR, path, "Missing '$EXTRA_LAYOUT_PATH' intent extra")
            return
        }

        log(EVENT_LOADING, jsonMapOf(KEY_PATH to path))

        dismissEmbedded()
        state.value = PreviewState.Loading

        // Intents can arrive faster than takeoff completes, and every one of them queues its own
        // onReady callback. Only the newest should end up displayed.
        val request = ++latestRequest

        Airship.onReady {
            if (!isDestroyed && request == latestRequest) {
                display(path)
            }
        }
    }

    private fun display(path: String) {
        val payload = try {
            layoutFile(path).parse(this)
        } catch (ex: Exception) {
            fail(EVENT_PARSE_ERROR, path, ex.message ?: ex.toString())
            return
        }

        if (payload !is ThomasLayout.Payload.Scene) {
            fail(EVENT_UNSUPPORTED, path, "Only scenes can be previewed by intent")
            return
        }

        val info = payload.info
        val presentationType = info.presentationType()
        log(
            EVENT_PARSED, jsonMapOf(
                KEY_PATH to path,
                KEY_PRESENTATION_TYPE to presentationType,
                "embedded_id" to info.embeddedViewId
            )
        )

        if (info.presentation is BannerPresentation) {
            fail(EVENT_UNSUPPORTED, path, "Banner previews aren't supported yet")
            return
        }

        if (info.isEmbedded) {
            val id = info.embeddedViewId
            if (id == null) {
                fail(EVENT_PARSE_ERROR, path, "Embedded layout is missing an embedded_id")
                return
            }

            embeddedId = id
            state.value = PreviewState.Embedded(id)
        }

        try {
            DefaultThomasLayoutDisplay.shared.display(
                context = this,
                info = info,
                priority = payload.priority,
                listener = listener
            )
        } catch (ex: Exception) {
            fail(EVENT_DISPLAY_ERROR, path, ex.message ?: ex.toString())
            return
        }

        // Only means the display call was accepted. Embedded layouts confirm with a later
        // `visible` event; modals never report visibility at all.
        log(
            EVENT_DISPLAY_REQUESTED, jsonMapOf(
                KEY_PATH to path,
                KEY_PRESENTATION_TYPE to presentationType
            )
        )

        // Modals host themselves in their own activity, so there's nothing left for this one
        // to show.
        if (embeddedId == null) {
            finish()
        }
    }

    private fun layoutFile(path: String) = ThomasLayout.LayoutFile(
        assetsPath = "${ThomasLayoutLoader.LAYOUTS_ROOT_FOLDER}/$path",
        filename = path.substringAfterLast('/')
    )

    private fun dismissEmbedded() {
        embeddedId?.let { EmbeddedViewManager.dismissAll(it) }
        embeddedId = null
    }

    private fun fail(event: String, path: String?, message: String) {
        log(event, jsonMapOf(KEY_PATH to path, "error" to message))
        state.value = PreviewState.Failed(message)
    }

    private fun log(event: String, fields: JsonSerializable) {
        val json = JsonMap.newBuilder()
            .put(KEY_EVENT, event)
            .putAll(fields.toJsonValue().optMap())
            .build()

        Log.i(TAG, json.toJsonValue().toString())
    }

    private val listener = object : ThomasListenerInterface {
        override fun onStateChanged(state: JsonSerializable) { }

        override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
            log(
                if (isVisible) EVENT_VISIBLE else EVENT_HIDDEN,
                jsonMapOf("is_foregrounded" to isForegrounded)
            )
        }

        override fun onDismiss(cancel: Boolean) {
            log("dismissed", jsonMapOf("cancel" to cancel))
        }

        override fun onReportingEvent(event: ReportingEvent) {
            val (name, data) = event.summarize()
            log("reporting", jsonMapOf("name" to name, "data" to data))
        }
    }

    companion object {
        const val EXTRA_LAYOUT_PATH = "layout_path"

        private const val TAG = "ThomasPreview"

        private const val KEY_EVENT = "event"
        private const val KEY_PATH = "path"
        private const val KEY_PRESENTATION_TYPE = "presentation_type"

        private const val EVENT_LOADING = "loading"
        private const val EVENT_PARSED = "parsed"
        private const val EVENT_PARSE_ERROR = "parse_error"
        private const val EVENT_DISPLAY_REQUESTED = "display_requested"
        private const val EVENT_DISPLAY_ERROR = "display_error"
        private const val EVENT_UNSUPPORTED = "unsupported"
        private const val EVENT_VISIBLE = "visible"
        private const val EVENT_HIDDEN = "hidden"
    }
}

private sealed interface PreviewState {
    data object Loading : PreviewState

    data class Embedded(val embeddedId: String) : PreviewState

    data class Failed(val message: String) : PreviewState
}

@Composable
private fun PreviewContent(state: PreviewState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is PreviewState.Loading -> CircularProgressIndicator()

            is PreviewState.Embedded -> AirshipEmbeddedView(
                embeddedId = state.embeddedId,
                modifier = Modifier.fillMaxSize()
            )

            is PreviewState.Failed -> Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

private fun LayoutInfo.presentationType(): String = when (presentation) {
    is BannerPresentation -> "banner"
    is EmbeddedPresentation -> "embedded"
    is ModalPresentation -> "modal"
    else -> "unknown"
}

private fun ReportingEvent.summarize(): Pair<String, JsonSerializable> = when (this) {
    is ReportingEvent.ButtonTap -> "button_tap" to data
    is ReportingEvent.FormDisplay -> "form_display" to data
    is ReportingEvent.FormResult -> "form_result" to data
    is ReportingEvent.Gesture -> "gesture" to data
    is ReportingEvent.PageAction -> "page_action" to data
    is ReportingEvent.PageSwipe -> "page_swipe" to data
    is ReportingEvent.PageView -> "page_view" to data
    is ReportingEvent.PagerComplete -> "pager_complete" to data
    is ReportingEvent.PagerSummary -> "pager_summary" to data
    is ReportingEvent.Dismiss -> "dismiss" to when (val dismissData = data) {
        is ReportingEvent.DismissData.ButtonTapped -> jsonMapOf(
            "button_identifier" to dismissData.identifier,
            "button_description" to dismissData.description
        )
        ReportingEvent.DismissData.TimedOut -> JsonValue.wrap("timedOut")
        ReportingEvent.DismissData.UserDismissed -> JsonValue.wrap("userDismissed")
    }
}
