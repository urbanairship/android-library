package com.urbanairship.preferencecenter.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.preferencecenter.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * `Activity` that displays a Preference Center via the hosted [PreferenceCenterFragment].
 */
public class PreferenceCenterActivity : FragmentActivity() {

    public companion object {
        /**
         * Required `String` extra specifying the ID of the Preference Center to be displayed.
         */
        public const val EXTRA_ID: String = "com.urbanairship.preferencecenter.PREF_CENTER_ID"

        private const val FRAGMENT_TAG = "PREF_CENTER_FRAGMENT"
    }

    private lateinit var fragment: PreferenceCenterFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge to edge on API 35 and up, which is the
        // first version  where edge to edge rendering is forced.
        // TODO: replace with Build.VERSION_CODES.VANILLA_ICE_CREAM
        // once we've bumped our compile/target SDK to 35.
        if (Build.VERSION.SDK_INT >= 35) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!UAirship.isTakingOff && !UAirship.isFlying) {
            UALog.e("PreferenceCenterActivity - unable to create activity, takeOff not called.")
            finish()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.ua_activity_preference_center, null, false)
        setContentView(view)

        // Setup the top app bar
        val topAppBar = findViewById<MaterialToolbar>(R.id.toolbar)
        topAppBar.setNavigationOnClickListener {
            finish()
        }

        // Restore the fragment, if we can
        val fragmentContainer = findViewById<FragmentContainerView>(R.id.fragment_container)
        if (savedInstanceState != null) {
            fragment = fragmentContainer.getFragment() as PreferenceCenterFragment
        }

        // Otherwise, create and add the fragment
        if (!this::fragment.isInitialized) {
            val id = intent.getStringExtra(EXTRA_ID)
                ?: throw IllegalArgumentException("Missing required extra: EXTRA_ID")

            fragment = PreferenceCenterFragment.create(preferenceCenterId = id)

            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, fragment, FRAGMENT_TAG)
                .commitNow()
        }
    }
}
