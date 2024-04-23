package com.urbanairship.automation.rewrite

import android.content.Context
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship

internal class InAppAutomationComponent(
    context: Context,
    dataStore: PreferenceDataStore,
    internal val automation: InAppAutomation,
) : AirshipComponent(context, dataStore) {

    override fun onAirshipReady(airship: UAirship) {
        super.onAirshipReady(airship)
        automation.airshipReady()
    }

    override fun tearDown() {
        super.tearDown()
        automation.tearDown()
    }
}
