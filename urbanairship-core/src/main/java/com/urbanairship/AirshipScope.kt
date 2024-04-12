package com.urbanairship

import androidx.annotation.RestrictTo
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlin.coroutines.CoroutineContext

/**
 * Airship Coroutine Scopes
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object AirshipScopes {

    /**
     * CoroutineScope that is active when the application is in the foreground.
     * When the app moves to the background, all children of this scope will be cancelled.
     *
     * Usage of this scope assumes that the app is already in the foreground, and it is intended for
     * use in situations where we need a scope that is tied to the lifetime of the session, but
     * don't have a proper lifecycle scope available.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public object AppForegroundScope : CoroutineScope {

        private val job = SupervisorJob()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main + job

        private val appListener = object : ApplicationListener {
            override fun onForeground(milliseconds: Long) {
            }

            override fun onBackground(milliseconds: Long) {
                job.cancelChildren()
            }
        }

        init {
            UAirship.shared {
                val context = UAirship.getApplicationContext()
                GlobalActivityMonitor.shared(context).apply {
                    addApplicationListener(appListener)
                }
            }
        }
    }
}
