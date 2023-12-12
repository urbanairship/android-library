package com.urbanairship.android.layout.playground.embedded

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.android.layout.AirshipEmbeddedView
import com.urbanairship.android.layout.DefaultEmbeddedViewManager
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.playground.R
import com.urbanairship.android.layout.playground.databinding.FragmentEmbeddedStackedLayoutBinding
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.app.GlobalActivityMonitor

class EmbeddedStackedLayoutFragment : Fragment(R.layout.fragment_embedded_stacked_layout) {

    private val binding by lazy { FragmentEmbeddedStackedLayoutBinding.bind(requireView()) }
    private val layoutId = "stacked_layout"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            layoutContainer.listener = object : AirshipEmbeddedView.Listener {
                override fun onAvailable(): Boolean {
                    // Update buttons
                    showButton.isEnabled = false
                    dismissButton.isEnabled = true

                    // Always display layouts when available
                    return true
                }

                override fun onEmpty() {
                    // Update buttons
                    showButton.isEnabled = true
                    dismissButton.isEnabled = false
                }
            }

            showButton.setOnClickListener {
                if (layoutContainer.childCount > 0) return@setOnClickListener

                setupTestLayouts(layoutId)
            }

            dismissButton.setOnClickListener {
                DefaultEmbeddedViewManager.dismiss(layoutId)
            }
        }

        setupTestLayouts(layoutId)
    }

    @Suppress("SameParameterValue")
    private fun setupTestLayouts(embeddedViewId: String) {
        val manager = DefaultEmbeddedViewManager

        layouts.forEach { layoutFile ->
            val layoutInfo = loadLayoutInfo(layoutFile) ?: return
            val displayArgs = DisplayArgs(
                layoutInfo,
                EmbeddedLayoutListener(layoutFile),
                GlobalActivityMonitor.shared(UAirship.getApplicationContext()),
                null,
                null
            )

            manager.addPending(
                embeddedViewId = embeddedViewId,
                viewInstanceId = layoutInfo.hashCode().toString(),
                layoutInfoProvider = { layoutInfo },
                displayArgsProvider = { displayArgs }
            )
        }
    }

    private fun loadLayoutInfo(fileName: String): LayoutInfo? {
        UALog.v("Loading layout: $fileName")

        val jsonMap = try {
            ResourceUtils.readJsonAsset(requireContext(), "sample_layouts/$fileName.json")
        } catch (e: Exception) {
            UALog.e(
                e,
                "Failed to load embedded layout! Unable to read file: '$fileName.json'"
            )
            Toast.makeText(requireContext(), "Unable to read layout", Toast.LENGTH_LONG).show()
            return null
        }

        if (jsonMap == null) {
            UALog.e("Failed to load embedded layout! Not a valid JSON object: '$fileName.json'")
            Toast.makeText(requireContext(), "Not a valid JSON object", Toast.LENGTH_LONG).show()
            return null
        }

        return LayoutInfo(jsonMap)
    }

    companion object {
        private val layouts = listOf("embedded-stacked-1", "embedded-stacked-2", "embedded-stacked-3")
    }
}
