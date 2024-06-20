package com.urbanairship.preferencecenter.ui

import android.os.Bundle
import android.view.MenuItem
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.activity.ThemedActivity

/**
 * `Activity` that displays a Preference Center via the hosted [PreferenceCenterFragment].
 */
public class PreferenceCenterActivity : ThemedActivity() {

    public companion object {
        /**
         * Required `String` extra specifying the ID of the Preference Center to be displayed.
         */
        public const val EXTRA_ID: String = "com.urbanairship.preferencecenter.PREF_CENTER_ID"

        private const val FRAGMENT_TAG = "PREF_CENTER_FRAGMENT"
    }

    private lateinit var fragment: PreferenceCenterFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            UALog.e("PreferenceCenterActivity - unable to create activity, takeOff not called.")
            finish()
            return
        }

        setDisplayHomeAsUpEnabled(true)

        val id = intent.getStringExtra(EXTRA_ID)
            ?: throw IllegalArgumentException("Missing required extra: EXTRA_ID")

        if (savedInstanceState != null) {
            fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as PreferenceCenterFragment
        }

        if (!this::fragment.isInitialized) {
            fragment = PreferenceCenterFragment.create(preferenceCenterId = id)

            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment, FRAGMENT_TAG)
                .commitNow()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> false
        }
    }
}
