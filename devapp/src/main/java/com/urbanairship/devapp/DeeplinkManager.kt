package com.urbanairship.devapp

import android.content.Intent
import com.urbanairship.UALog
import java.lang.ref.WeakReference

internal class DeeplinkManager {

    private var routerRef: WeakReference<AppRouterViewModel>? = null
    private var pendingIntent: Intent? = null

    fun setAppRouter(router: AppRouterViewModel) {
        this.routerRef = WeakReference(router)
        pendingIntent?.let {
            pendingIntent = null
            handleDeeplink(it)
        }
    }

    fun handleDeeplink(intent: Intent) {
        val data = intent.data ?: return

        val router = routerRef?.get()
        if (router == null) {
            pendingIntent = intent
            return
        }

        if (!data.scheme.equals(SCHEME, ignoreCase = true) ||
            !data.host.equals(HOST, ignoreCase = true) ) {
            return
        }

        val path = data.path?.split("/")?.toMutableList() ?: return

        val stack = mutableListOf<Destination>()
        while (path.isNotEmpty()) {
            val segment = path.removeAt(0)
            when(segment) {
                "inbox" -> {
                    val rest = path.joinToString("/")
                    // A bare ".../inbox" opens the message center; ".../inbox/message/<id>"
                    // opens a specific message.
                    val restored = if (rest.isEmpty()) {
                        AppRouterViewModel.TopLevelDestination.Message()
                    } else {
                        AppRouterViewModel.TopLevelDestination.restore(rest)
                    }
                    if (restored != null) {
                        stack.add(restored)
                        break
                    } else {
                        UALog.w { "Unrecognized inbox deep link path: $rest" }
                    }
                }
                else -> {}
            }
        }

        if (stack.isNotEmpty()) {
            router.navigateStack(stack)
        }
    }


    companion object {
        private const val SCHEME = "vnd.urbanairship.sample"
        private const val HOST = "deepLink"

        val shared = DeeplinkManager()
    }
}
