/* Copyright Airship and Contributors */

package com.urbanairship.automation

import android.content.Context
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore

internal class InAppAutomationComponent(
    context: Context,
    dataStore: PreferenceDataStore,
    internal val automation: InAppAutomation,
) : AirshipComponent(context, dataStore) {

    override fun onAirshipReady() {
        automation.airshipReady()
    }

    override fun tearDown() {
        super.tearDown()
        automation.tearDown()
    }
}
