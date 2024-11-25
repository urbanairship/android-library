package com.urbanairship.debug

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit

public class DebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val fragment = DebugFragment()
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(android.R.id.content, fragment)
            }
        }
    }
}
