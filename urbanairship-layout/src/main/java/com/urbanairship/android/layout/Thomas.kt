/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import android.app.ActivityOptions
import android.app.ActivityOptions.makeSceneTransitionAnimation
import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.android.layout.display.DisplayArgs
import com.urbanairship.android.layout.display.DisplayArgsLoader
import com.urbanairship.android.layout.display.DisplayException
import com.urbanairship.android.layout.display.DisplayRequest
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.ui.BannerLayout
import com.urbanairship.android.layout.ui.ModalActivity
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.json.JsonMap
import com.urbanairship.webkit.AirshipWebViewClient
import com.google.android.material.internal.ContextUtils.getActivity

/**
 * Entry point and related helper methods for rendering layouts based on our internal DSL.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object Thomas {

    @VisibleForTesting
    public val MAX_SUPPORTED_VERSION: Int = 2

    @VisibleForTesting
    public val MIN_SUPPORTED_VERSION: Int = 1

    /**
     * Validates that a payload can be displayed.
     * @param payload The payload.
     * @return `true` if valid, otherwise `false`.
     */
    @JvmStatic
    public fun isValid(payload: LayoutInfo): Boolean {
        if (payload.version !in MIN_SUPPORTED_VERSION..MAX_SUPPORTED_VERSION) {
            return false
        }
        return when (payload.presentation) {
            is ModalPresentation -> true
            is BannerPresentation -> true
            is EmbeddedPresentation -> true
            else -> false
        }
    }

    @JvmStatic
    @Throws(DisplayException::class)
    public fun prepareDisplay(
        payload: LayoutInfo,
        priority: Int,
        extras: JsonMap,
        activityMonitor: ActivityMonitor,
        listener: ThomasListenerInterface,
        actionRunner: ThomasActionRunner,
        imageCache: ImageCache? = null,
        webViewClientFactory: Factory<AirshipWebViewClient>? = null,
        embeddedViewManager: AirshipEmbeddedViewManager,
    ): DisplayRequest {
        if (!isValid(payload)) {
            throw DisplayException("Payload is not valid: " + payload.presentation)
        }

        val callback = when (payload.presentation) {
            is ModalPresentation -> {
                { context: Context, args: DisplayArgs ->
                    val intent = Intent(context, ModalActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(
                            ModalActivity.EXTRA_DISPLAY_ARGS_LOADER,
                            DisplayArgsLoader.newLoader(args)
                        )
                    val activityContext = activityMonitor.resumedActivities.lastOrNull()
                    if (activityContext != null) {
                        activityContext.startActivity(
                            intent,
                            makeSceneTransitionAnimation(activityContext).toBundle()
                        )
                    } else {
                        context.startActivity(intent)
                    }
                }
            }
            is BannerPresentation -> {
                { context: Context, args: DisplayArgs ->
                    val layoutBanner = BannerLayout(context, args)
                    layoutBanner.display()
                }
            }
            is EmbeddedPresentation -> {
                { _: Context, args: DisplayArgs ->
                    embeddedViewManager.addPending(args, priority, extras)
                }
            }
            else -> {
                throw DisplayException("Presentation not supported: " + payload.presentation)
            }
        }

        return DisplayRequest(
            payload = payload,
            activityMonitor = activityMonitor,
            listener = listener,
            actionRunner = actionRunner,
            imageCache = imageCache,
            webViewClientFactory = webViewClientFactory,
            onDisplay = callback
        )
    }
}
