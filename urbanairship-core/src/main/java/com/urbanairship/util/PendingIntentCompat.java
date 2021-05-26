package com.urbanairship.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RestrictTo;

/**
 * Compat wrapper for {@link android.app.PendingIntent}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PendingIntentCompat {
    /**
     * Compat version of {@code PendingIntent.FLAG_MUTABLE}, added in Android S.
     *
     * This flag indicates that the created {@code PendingIntent} should be mutable.
     * {@code FLAG_MUTABLE} should be used when some functionality relies on modifying the
     * underlying intent, like inline replies or bubbles.
     *
     * Note: {@code FLAG_MUTABLE} cannot be combined with {@code FLAG_IMMUTABLE}.
     */
    public static final int FLAG_MUTABLE = 0x02000000;

    private PendingIntentCompat() {}

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) {
        return PendingIntent.getActivity(context, requestCode, intent, ensureExplicitMutability(flags));
    }

    public static PendingIntent getService(Context context, int requestCode, Intent intent, int flags) {
        return PendingIntent.getService(context, requestCode, intent, ensureExplicitMutability(flags));
    }

    public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags) {
        return PendingIntent.getBroadcast(context, requestCode, intent, ensureExplicitMutability(flags));
    }

    private static int ensureExplicitMutability(int flags) {
        // On M or above, add FLAG_IMMUTABLE unless FLAG_MUTABLE has already been set.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (flags & FLAG_MUTABLE) == 0) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
