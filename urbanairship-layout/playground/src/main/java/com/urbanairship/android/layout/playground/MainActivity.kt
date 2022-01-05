package com.urbanairship.android.layout.playground

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.urbanairship.Logger
import com.urbanairship.android.layout.BasePayload
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.ThomasListener
import com.urbanairship.android.layout.playground.databinding.ActivityMainBinding
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.iam.InAppActionUtils

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREF_NAME = "layout-playground"
        private const val PREF_KEY = "selected-layout"
        private const val SAMPLE_LAYOUTS_PATH = "sample_layouts"
    }

    private lateinit var binding: ActivityMainBinding

    private val sharedPrefs by lazy {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        initViews(binding)

        // Enable webview debugging via Chrome for debug builds.
        if (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    override fun onResume() {
        super.onResume()

        if (binding.layoutSpinnerText.adapter != adapter) {
            binding.layoutSpinnerText.setAdapter(adapter)
        }
    }

    private fun initViews(binding: ActivityMainBinding) {
        val layoutFiles = ResourceUtils.listJsonAssets(this, SAMPLE_LAYOUTS_PATH)
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

        binding.showModal.setOnClickListener {
            displayLayout(binding.layoutSpinnerText.text.toString())
        }
    }

    private fun displayLayout(fileName: String) {
        try {
            val jsonMap = ResourceUtils.readJsonAsset(this, "sample_layouts/$fileName")
            if (jsonMap == null) {
                Logger.error("Failed to display layout! Not a valid JSON object: '$fileName'")
                Toast.makeText(this, "Not a valid JSON object", Toast.LENGTH_LONG).show()
                return
            }
            val payload = BasePayload.fromJson(jsonMap)
            Thomas.prepareDisplay(payload)
                .setListener(thomasListener)
                .setActionsRunner(InAppActionUtils::runActions)
                .display(this)
        } catch (e: Exception) {
            Logger.error(e)
            Toast.makeText(this, "Error trying to display layout", Toast.LENGTH_LONG).show()
        }
    }

    private val thomasListener = object : ThomasListener {
        override fun onPageView(pagerData: PagerData, state: LayoutData?, displayedAt: Long) {
            Logger.debug("onPageView(pagerData: $pagerData, state: $state, displayedAt: $displayedAt)")
        }

        override fun onPageSwipe(pagerData: PagerData, toPageIndex: Int, toPageId: String, fromPageIndex: Int, fromPageId: String, state: LayoutData?) {
            Logger.debug("onPageSwipe(pagerData: $pagerData, toPageIndex: $toPageIndex, toPageId: $toPageId, fromPageIndex: $fromPageIndex, fromPageId: $fromPageId, state: $state)")
        }

        override fun onButtonTap(buttonId: String, state: LayoutData?) {
            Logger.debug("onButtonTap(buttonId: $buttonId, state: $state)")
        }

        override fun onDismiss(displayTime: Long) {
            Logger.debug("onDismiss(displayTime: $displayTime)")
        }

        override fun onDismiss(buttonId: String, buttonDescription: String?, cancel: Boolean, displayTime: Long, state: LayoutData?) {
            Logger.debug("onDismiss(buttonId: $buttonId, buttonDescription: $buttonDescription, cancel: $cancel, displayTime: $displayTime, state: $state")
        }

        override fun onFormResult(formData: FormData<*>, state: LayoutData?) {
            Logger.debug("onFormResult(formData: ${formData.toJsonValue()}, state: $state)")
        }

        override fun onFormDisplay(formId: String, state: LayoutData?) {
            Logger.debug("onFormDisplay(formId: $formId, state: $state)")
        }
    }
}
