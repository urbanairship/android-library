/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.event

import android.os.Bundle
import android.support.annotation.RestrictTo
import android.view.MenuItem
import com.urbanairship.debug.DebugFragment
import com.urbanairship.debug.R
import com.urbanairship.messagecenter.ThemedActivity

/**
 * Activity that shows the debug screen listing.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DebugActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, DebugFragment())
                    .commit()
        }

        setTitle(R.string.debug_title)
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
