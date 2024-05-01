package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonInfo
import kotlinx.coroutines.flow.MutableStateFlow
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
public interface CustomDisplayAdapter {

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
    public class MessageTap(): CustomDisplayResolution()

    /**
     * User dismissed
     */
    public class UserDismissed(): CustomDisplayResolution()

    /**
     * Timed out
     */
    public class TimedOut(): CustomDisplayResolution()
}
