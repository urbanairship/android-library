package com.urbanairship.preferencecenter.ui

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.preferencecenter.PreferenceCenter
import com.urbanairship.preferencecenter.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * `Activity` that displays a Preference Center via the hosted [PreferenceCenterFragment].
 */
public class PreferenceCenterActivity : FragmentActivity() {

    public companion object {
        private const val FRAGMENT_TAG = "PREF_CENTER_FRAGMENT"
    }

    private lateinit var fragment: PreferenceCenterFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge to edge on API 35 and up, which is the
        // first version  where edge to edge rendering is forced.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            enableEdgeToEdge()
            window.isNavigationBarContrastEnforced = false

            // enableEdgeToEdge picks status bar icon appearance from day/night mode,
            // ignoring the theme's windowLightStatusBar. Restore the theme attribute
            // so apps can keep icons legible over a custom toolbar color.
            val attrs = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowLightStatusBar))
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val lightStatusBar = attrs.getBoolean(0, !isNight)
            attrs.recycle()
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = lightStatusBar
        }
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (!Airship.isTakingOff && !Airship.isFlying) {
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

        if (Build.VERSION.SDK_INT >= 35) {
            // Pad the toolbar instead of the content root so the toolbar background
            // extends behind the transparent status bar.
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                topAppBar.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
                fragmentContainer.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
                insets
            }
            ViewCompat.requestApplyInsets(view)
        }
        if (savedInstanceState != null) {
            fragment = fragmentContainer.getFragment() as PreferenceCenterFragment
        }

        // Otherwise, create and add the fragment
        if (!this::fragment.isInitialized) {
            val id = PreferenceCenter.parsePreferenceCenterId(intent)
                ?: throw IllegalArgumentException("Missing required extra: EXTRA_ID")

            fragment = PreferenceCenterFragment.create(preferenceCenterId = id)

            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, fragment, FRAGMENT_TAG)
                .commitNow()
        }
    }
}
