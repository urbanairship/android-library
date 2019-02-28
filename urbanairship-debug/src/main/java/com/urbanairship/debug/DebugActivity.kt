package com.urbanairship.debug

import android.os.Bundle
import com.urbanairship.messagecenter.ThemedActivity

/**
 * Debug activity. The main entry point for the debug library.
 */
class DebugActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, DebugFragment())
                    .commit()
        }

        setTitle(R.string.debug_title)
    }

}
