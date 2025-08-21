package com.urbanairship.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RestrictTo

/**
 * Compat wrapper for [android.app.PendingIntent].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object PendingIntentCompat {

    /**
     * Compat version of `PendingIntent.FLAG_MUTABLE`, added in Android S.
     *
     * This flag indicates that the created `PendingIntent` should be mutable.
     * `FLAG_MUTABLE` should be used when some functionality relies on modifying the
     * underlying intent, like inline replies or bubbles.
     *
     * Note: `FLAG_MUTABLE` cannot be combined with `FLAG_IMMUTABLE`.
     */
    public const val FLAG_MUTABLE: Int = 0x02000000

    public fun getActivity(
        context: Context?, requestCode: Int, intent: Intent?, flags: Int
    ): PendingIntent {
        return PendingIntent.getActivity(
            context, requestCode, intent, ensureExplicitMutability(flags)
        )
    }

    public fun getService(
        context: Context?, requestCode: Int, intent: Intent, flags: Int
    ): PendingIntent {
        return PendingIntent.getService(
            context, requestCode, intent, ensureExplicitMutability(flags)
        )
    }

    public fun getBroadcast(
        context: Context?, requestCode: Int, intent: Intent, flags: Int
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            context, requestCode, intent, ensureExplicitMutability(flags)
        )
    }

    private fun ensureExplicitMutability(flags: Int): Int {
        // On M or above, add FLAG_IMMUTABLE unless FLAG_MUTABLE has already been set.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (flags and FLAG_MUTABLE) == 0) {
            return flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }
}
