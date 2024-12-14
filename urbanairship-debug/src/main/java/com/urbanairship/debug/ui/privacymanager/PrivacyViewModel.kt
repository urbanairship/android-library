/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.privacymanager

import androidx.lifecycle.ViewModel
import com.urbanairship.PrivacyManager
import com.urbanairship.UAirship
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

internal interface PrivacyViewModel {

    val features: Flow<List<PrivacyFeature>>
    fun toggle(feature: PrivacyFeature)

    companion object {
        internal fun forPreview(): PrivacyViewModel {
            return object : PrivacyViewModel {
                override val features: Flow<List<PrivacyFeature>>
                    get() = flowOf(
                        listOf(
                            PrivacyFeature(
                                name = "Test Enabled",
                                isEnabled = true,
                                feature = PrivacyManager.Feature.ALL
                            ),
                            PrivacyFeature(
                                name = "Test Disabled",
                                isEnabled = false,
                                feature = PrivacyManager.Feature.NONE
                            )
                        )
                    )

                override fun toggle(feature: PrivacyFeature) { }
            }
        }
    }
}

internal class DefaultPrivacyViewModel(
    private val privacyManager: PrivacyManager = UAirship.shared().privacyManager
): PrivacyViewModel, ViewModel() {

    override val features: Flow<List<PrivacyFeature>> = callbackFlow {
        trySend(mapCurrentFeatures())

        val callback = object : PrivacyManager.Listener {
            override fun onEnabledFeaturesChanged() {
                trySendBlocking(mapCurrentFeatures())
            }
        }

        privacyManager.addListener(callback)

        awaitClose { privacyManager.removeListener(callback) }
    }

    override fun toggle(feature: PrivacyFeature) {
        if (!UAirship.isFlying()) {
            return
        }

        if (privacyManager.isEnabled(feature.feature)) {
            privacyManager.disable(feature.feature)
        } else {
            privacyManager.enable(feature.feature)
        }
    }

    private fun mapCurrentFeatures(): List<PrivacyFeature> {
        return FEATURE_TO_NAME.map {
            PrivacyFeature(
                name = it.value,
                isEnabled = privacyManager.isEnabled(it.key),
                feature = it.key
            )
        }.sortedBy { it.name }
    }

    private companion object {
        private val FEATURE_TO_NAME = mapOf(
            PrivacyManager.Feature.ANALYTICS to "Analytics",
            PrivacyManager.Feature.CONTACTS to "Contacts",
            PrivacyManager.Feature.FEATURE_FLAGS to "Feature Flags",
            PrivacyManager.Feature.IN_APP_AUTOMATION to "In App Automation",
            PrivacyManager.Feature.MESSAGE_CENTER to "Message Center",
            PrivacyManager.Feature.PUSH to "Push",
            PrivacyManager.Feature.TAGS_AND_ATTRIBUTES to "Tags & Attributes"
        )
    }
}

internal data class PrivacyFeature(
    val name: String,
    val isEnabled: Boolean,
    val feature: PrivacyManager.Feature
)
