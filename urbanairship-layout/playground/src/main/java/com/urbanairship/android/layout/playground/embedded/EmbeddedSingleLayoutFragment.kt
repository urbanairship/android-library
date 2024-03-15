package com.urbanairship.android.layout.playground.embedded

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.playground.R
import com.urbanairship.android.layout.playground.databinding.FragmentEmbeddedSingleLayoutBinding
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.embedded.AirshipEmbeddedView

class EmbeddedSingleLayoutFragment : Fragment(R.layout.fragment_embedded_single_layout) {

    private val binding by lazy { FragmentEmbeddedSingleLayoutBinding.bind(requireView()) }
    private val layoutId = "single_layout"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            layoutContainer.listener = object : AirshipEmbeddedView.Listener {
                override fun onAvailable(): Boolean {
                    // Update buttons
                    showButton.isEnabled = false
                    dismissButton.isEnabled = true
                    layoutContainer.isGone = false

                    // Always display layouts when available
                    return true
                }

                override fun onEmpty() {
                    // Update buttons
                    showButton.isEnabled = true
                    dismissButton.isEnabled = false
                    layoutContainer.isGone = true
                }
            }

            showButton.setOnClickListener {
                if (layoutContainer.childCount > 0) return@setOnClickListener
                setupTestLayouts(layoutId)
            }

            dismissButton.setOnClickListener {
                InAppAutomation.shared().embeddedViewManager.dismiss(layoutId)
            }
        }

        setupTestLayouts(layoutId)
    }

    @Suppress("SameParameterValue")
    private fun setupTestLayouts(embeddedViewId: String) {
        val manager = InAppAutomation.shared().embeddedViewManager

        singleLayouts.forEach { layoutFile ->
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
            UALog.e(e, "Failed to load embedded layout file: '$fileName.json'")
            Toast.makeText(requireContext(), "Failed to load layout!", LENGTH_LONG).show()
            return null
        }

        if (jsonMap == null) {
            UALog.e("Failed to load embedded layout JSON: '$fileName.json'")
            Toast.makeText(requireContext(), "Invalid JSON object!", LENGTH_LONG).show()
            return null
        }

        return LayoutInfo(jsonMap)
    }

    companion object {
        private val singleLayouts = listOf("embedded-single")
    }
}
