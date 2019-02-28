/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.event

import android.os.Bundle
import android.support.annotation.RestrictTo
import android.view.MenuItem
import com.urbanairship.debug.R
import com.urbanairship.messagecenter.ThemedActivity

/**
 * Activity that shows event listing.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class EventActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, EventListFragment())
                    .commit()
        }

        setTitle(R.string.event_view_title)
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
