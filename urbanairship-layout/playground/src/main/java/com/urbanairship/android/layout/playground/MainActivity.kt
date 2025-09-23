package com.urbanairship.android.layout.playground

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import com.urbanairship.UALog
import com.urbanairship.actions.Action
import com.urbanairship.actions.DefaultActionRunner
import com.urbanairship.actions.run
import com.urbanairship.android.layout.AirshipCustomViewManager
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.playground.customviews.CustomAdView
import com.urbanairship.android.layout.playground.customviews.CustomMapView
import com.urbanairship.android.layout.playground.customviews.CustomWeatherView
import com.urbanairship.android.layout.playground.customviews.CustomWeatherViewXml
import com.urbanairship.android.layout.playground.customviews.SceneControllerCustomView
import com.urbanairship.android.layout.playground.databinding.ActivityMainBinding
import com.urbanairship.android.layout.playground.embedded.EmbeddedActivity
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.embedded.EmbeddedViewManager
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.emptyJsonMap
import com.urbanairship.json.jsonMapOf

class MainActivity : AppCompatActivity() {

    companion object {

        private const val PREF_NAME = "layout-playground"
        private const val PREF_KEY = "selected-layout"
        private const val SAMPLE_LAYOUTS_PATH = "sample_layouts"
    }

    private lateinit var binding: ActivityMainBinding

    private val sharedPrefs by lazy {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
    }

    private val adapter by lazy {
        ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
    }

    private var previousSelection: String?
        get() = sharedPrefs.getString(PREF_KEY, null)
        set(value) = sharedPrefs.edit(commit = true) {
            putString(PREF_KEY, value)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = ResourcesCompat.getColor(resources, R.color.primaryDarkColor, theme),
            )
        )
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        initViews(binding)

        // Enable webview debugging via Chrome for debug builds.
        if (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Register custom XML views
        AirshipCustomViewManager.register("weather_custom_view_xml") { data ->
            CustomWeatherViewXml(this).apply {
                bind(data)
            }
        }

        // Register custom composable views
        AirshipCustomViewManager.register("weather_custom_view", CustomWeatherView())
        AirshipCustomViewManager.register("ad_custom_view", CustomAdView())
        AirshipCustomViewManager.register("map_custom_view", CustomMapView())
        AirshipCustomViewManager.register("scene_controller_test", SceneControllerCustomView())
    }

    override fun onResume() {
        super.onResume()

        if (binding.layoutSpinnerText.adapter != adapter) {
            binding.layoutSpinnerText.setAdapter(adapter)
        }
    }

    private fun initViews(binding: ActivityMainBinding) {
        val layoutFiles = ResourceUtils.listJsonAssets(this, SAMPLE_LAYOUTS_PATH)
            .filterNot { it.startsWith("embedded") }
        adapter.addAll(layoutFiles)

        previousSelection?.let { selected ->
            if (adapter.getPosition(selected) > -1) {
                binding.layoutSpinnerText.setText(selected, false)
            }
        }

        binding.layoutSpinnerText.setOnItemClickListener { _, _, position, _ ->
            if (position == ListView.INVALID_POSITION) return@setOnItemClickListener
            previousSelection = adapter.getItem(position)
        }

        binding.showModal.setOnClickListener { v ->
            v.isEnabled = false
            displayLayout(binding.layoutSpinnerText.text.toString())
            v.postDelayed({ v.isEnabled = true }, 150)
        }

        binding.showNext.setOnClickListener {
            val nextPosition = adapter.getPosition(binding.layoutSpinnerText.text.toString()) + 1
            if (nextPosition < adapter.count) {
                val next = adapter.getItem(nextPosition) ?: return@setOnClickListener
                binding.layoutSpinnerText.setText(next, false)
                previousSelection = next

                displayLayout(next)
            }
        }

        binding.startEmbeddedActivity.setOnClickListener {
            startActivity(Intent(this, EmbeddedActivity::class.java))
        }

        binding.startAndroidActivity.setOnClickListener {
            startActivity(Intent(this, OtherAndroidActivity::class.java))
        }

        binding.startAppcompatActivity.setOnClickListener {
            startActivity(Intent(this, OtherAppCompatActivity::class.java))
        }
    }

    private fun displayLayout(fileName: String) {
        try {
            val jsonMap = ResourceUtils.readJsonAsset(this, "sample_layouts/$fileName")
            if (jsonMap == null) {
                UALog.e("Failed to display layout! Not a valid JSON object: '$fileName'")
                Toast.makeText(this, "Not a valid JSON object", Toast.LENGTH_LONG).show()
                return
            }
            val payload = LayoutInfo(jsonMap)
            Thomas.prepareDisplay(
                payload = payload,
                priority = 0,
                extras = emptyJsonMap(),
                activityMonitor = GlobalActivityMonitor.shared(applicationContext),
                listener = thomasListener,
                actionRunner = actionRunner,
                embeddedViewManager = EmbeddedViewManager
            ).display(this)
        } catch (e: Exception) {
            UALog.e(e)
            Toast.makeText(this, "Error trying to display layout", Toast.LENGTH_LONG).show()
        }
    }

    private val thomasListener = object : ThomasListenerInterface {
        private val events = mutableListOf<String>()

        override fun onStateChanged(state: JsonSerializable) { }

        override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
            "onVisibilityChanged(isVisible: $isVisible, isForegrounded: $isForegrounded)".let {
                events.add(it)
                UALog.d(it)
            }
        }

        override fun onDismiss(cancel: Boolean) {
            "onDismiss(cancel: $cancel".let {
                events.add(it)
                UALog.d(it)
            }
        }

        @Throws(JsonException::class)
        override fun onReportingEvent(event: ReportingEvent) {
            when (event) {
                is ReportingEvent.ButtonTap -> logEvent("button_tap", event.data, event.context)

                is ReportingEvent.FormDisplay -> logEvent("form_display", event.data, event.context)

                is ReportingEvent.FormResult -> logEvent("form_result", event.data, event.context)

                is ReportingEvent.Gesture -> logEvent("gesture", event.data, event.context)

                is ReportingEvent.PageAction -> logEvent("page_action", event.data, event.context)

                is ReportingEvent.PageSwipe -> logEvent("page_swipe", event.data, event.context)

                is ReportingEvent.PageView -> logEvent("page_view", event.data, event.context)

                is ReportingEvent.PagerComplete -> logEvent("pager_complete", event.data, event.context)

                is ReportingEvent.PagerSummary -> logEvent("pager_summary", event.data, event.context)

                is ReportingEvent.Dismiss -> when (val eventData = event.data) {
                    is ReportingEvent.DismissData.ButtonTapped -> {
                        logEvent(
                            name = "dismiss",
                            event = jsonMapOf(
                                "button_identifier" to eventData.identifier,
                                "button_description" to eventData.description
                            ),
                            context = event.context
                        )
                    }

                    ReportingEvent.DismissData.TimedOut ->
                        logEvent("dismiss", JsonValue.wrap("timedOut"), event.context)

                    ReportingEvent.DismissData.UserDismissed ->
                        logEvent("dismiss", JsonValue.wrap("userDismissed"), event.context)
                }.also { dumpEvents() }
            }
        }

        private fun logEvent(name: String, event: JsonSerializable, context: LayoutData) {
            "$name - event: ${event.toJsonValue()}, context: ${context.toJsonValue()}".let {
                events.add(it)
                UALog.d(it)
            }
        }

        private fun dumpEvents() {
            UALog.d("\n")
            UALog.d("---- LAYOUT EVENTS ----")
            events.forEachIndexed { index, s -> UALog.d("$index: $s") }
            UALog.d("-----------------------")
            UALog.d("\n")
            events.clear()
        }
    }

    private val actionRunner: ThomasActionRunner = object: ThomasActionRunner {
        override fun run(actions: Map<String, JsonValue>, state: LayoutData) {
            DefaultActionRunner.run(actions, Action.Situation.AUTOMATION)
        }
    }
}
