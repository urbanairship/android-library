/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.MainThread
import com.urbanairship.UALog
import com.urbanairship.iam.info.InAppMessageButtonInfo
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.StateFlow

/**
 * Custom display adapter types
 */
public enum class CustomDisplayAdapterType {

    /**
     * HTML adapter
     */
    HTML,
    /**
     * Modal adapter
     */
    MODAL,
    /**
     * Fullscreen adapter
     */
    FULLSCREEN,
    /**
     * Banner adapter
     */
    BANNER,
    /**
     * Custom adapter
     */
    CUSTOM;
}

/**
 * Custom display adapter
 */
public sealed interface CustomDisplayAdapter {

    /**
     * Suspending display adapter
     */
    public interface SuspendingAdapter : CustomDisplayAdapter {

        /**
         *  Used before display to wait for the adapter to be ready.
         */
        public val isReady: StateFlow<Boolean>

        /**
         * Called to display the message on the main dispatcher
         * @param context: The display context
         * @return a [CustomDisplayResolution]
         */
        public suspend fun display(context: Context): CustomDisplayResolution
    }

    /**
     * Callback adapter
     */
    public interface CallbackAdapter: CustomDisplayAdapter {
        /**
         * Called to display the message on the main dispatcher
         * @param context: The display context
         * @param callback: The display finished callback. Must be called on the same process.
         * @return a [CustomDisplayResolution]
         */
        @MainThread
        public fun display(context: Context, callback: DisplayFinishedCallback)
    }
}

/**
 * Display finished callback. Call finished once the message is finished displaying.
 * The callback is only available in the same process as it was created on.
 */
public class DisplayFinishedCallback private constructor(
    private val id: String?
) : Parcelable {

    /**
     * Checks if the callback is still valid.
     */
    public var isValid: Boolean = lock.withLock {
        cached[id] != null
    }

    /**
     * Finishes the display.
     * @param result The resolution.
     */
    public fun finished(result: CustomDisplayResolution) {
        val callback = lock.withLock {
            val callback = cached[id]
            cached.remove(id)
            callback
        }

        if (callback == null) {
            UALog.e { "Unable to process result. Either the app was killed, the callback was called multiple times, or its being called on a different process." }
        } else {
            callback.invoke(result)
        }
    }

    private constructor(parcel: Parcel): this(parcel.readString())

    public override fun describeContents(): Int = 0

    public override fun writeToParcel(dest: Parcel, flags: Int): Unit = dest.writeString(id)

    public companion object {
        private val lock = ReentrantLock()

        private val cached: MutableMap<String, (CustomDisplayResolution) -> Unit> = mutableMapOf()

        @JvmField
        public val CREATOR: Parcelable.Creator<DisplayFinishedCallback> =
            object : Parcelable.Creator<DisplayFinishedCallback> {
                override fun createFromParcel(parcel: Parcel): DisplayFinishedCallback {
                    return DisplayFinishedCallback(parcel)
                }

                override fun newArray(size: Int): Array<DisplayFinishedCallback?> {
                    return arrayOfNulls(size)
                }
            }


        internal fun newCallback(callback: (CustomDisplayResolution) -> Unit): DisplayFinishedCallback {
            val loaderId = UUID.randomUUID().toString()
            lock.withLock {
                cached[loaderId] = callback
            }
            return DisplayFinishedCallback(loaderId)
        }
    }
}

/**
 * Resolution data
 */
public sealed class CustomDisplayResolution {
    /**
     * Button tap
     */
    public class ButtonTap(public val info: InAppMessageButtonInfo): CustomDisplayResolution() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ButtonTap

            return info == other.info
        }

        override fun hashCode(): Int {
            return info.hashCode()
        }
    }

    /**
     * Message tap
     */
    public data object MessageTap: CustomDisplayResolution()

    /**
     * User dismissed
     */
    public data object UserDismissed: CustomDisplayResolution()

    /**
     * Timed out
     */
    public data object TimedOut: CustomDisplayResolution()
}
