/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.event

import android.os.Bundle
import android.support.annotation.RestrictTo
import android.view.MenuItem
import com.urbanairship.debug.R
import com.urbanairship.messagecenter.ThemedActivity

/**
 * Activity that shows event details.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class EventDetailsActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setDisplayHomeAsUpEnabled(true)
        val eventId = intent?.data?.schemeSpecificPart
        if (eventId == null) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, EventDetailsFragment.newInstance(eventId))
                    .commit()
        }

        setTitle(R.string.event_details)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                this.finish()
                return true
            }
        }
        return false
    }
}
