/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import android.content.Context
import android.view.View
import androidx.core.os.ConfigurationCompat
import com.urbanairship.android.layout.property.Orientation
import com.urbanairship.android.layout.property.WindowSize
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Locale

/**
 * The platform, window and frame a layout is shown in, as predicates read it at `$environment`.
 *
 * `window` is the window the layout's activity runs in, measured before the system bars' insets,
 * and `safe_window` the same window after them; every presentation sees the same numbers. `frame`
 * is the view the scene is presented in, before its placement and safe-area insets are applied.
 * Lengths are in dp.
 *
 * @param darkMode whether the system is in night mode, which is what color selectors match.
 * @param textScale the font scale applied to label text, 1 at the default size.
 * @param windowWidth the window's width.
 * @param windowHeight the window's height.
 * @param windowSize the window's size class, or null where the configuration doesn't give one.
 * @param safeWindowWidth the window's width less the system bars' insets.
 * @param safeWindowHeight the window's height less the system bars' insets.
 * @param frameWidth the frame's width, or null where the host lets the content decide it or the
 * frame hasn't been laid out.
 * @param frameHeight the frame's height, under the same rule as [frameWidth].
 * @param isRtl whether the layout direction is right to left.
 * @param locale the current locale.
 */
internal data class ThomasEnvironment(
    val darkMode: Boolean,
    val textScale: Double,
    val windowWidth: Double,
    val windowHeight: Double,
    val windowSize: WindowSize?,
    val safeWindowWidth: Double = windowWidth,
    val safeWindowHeight: Double = windowHeight,
    val frameWidth: Double? = null,
    val frameHeight: Double? = null,
    val isRtl: Boolean = false,
    val locale: Locale = Locale.US
) : JsonSerializable {

    /** Landscape when the window is wider than it is tall, and portrait otherwise. */
    val orientation: Orientation
        get() = if (windowWidth > windowHeight) Orientation.LANDSCAPE else Orientation.PORTRAIT

    override fun toJsonValue(): JsonValue = jsonMapOf(
        PLATFORM to PLATFORM_ANDROID,
        DARK_MODE to darkMode,
        TEXT_SCALE to textScale,
        WINDOW to jsonMapOf(
            WIDTH to windowWidth,
            HEIGHT to windowHeight,
            ORIENTATION to orientation.toString(),
            SIZE to windowSize?.toString()
        ),
        SAFE_WINDOW to jsonMapOf(
            WIDTH to safeWindowWidth,
            HEIGHT to safeWindowHeight
        ),
        FRAME to if (frameWidth != null || frameHeight != null) {
            jsonMapOf(WIDTH to frameWidth, HEIGHT to frameHeight)
        } else {
            null
        },
        LAYOUT_DIRECTION to if (isRtl) RTL else LTR,
        LOCALE to jsonMapOf(
            IDENTIFIER to locale.toLanguageTag(),
            LANGUAGE to locale.language.ifEmpty { null },
            REGION to locale.country.ifEmpty { null }
        )
    ).toJsonValue()

    /**
     * Whether this differs from [other] only in the window's, safe window's or frame's size.
     *
     * A change of orientation isn't one, although it is sizes swapping: it applies at once.
     */
    fun differsOnlyInSize(other: ThomasEnvironment): Boolean = orientation == other.orientation && copy(
        windowWidth = other.windowWidth,
        windowHeight = other.windowHeight,
        safeWindowWidth = other.safeWindowWidth,
        safeWindowHeight = other.safeWindowHeight,
        frameWidth = other.frameWidth,
        frameHeight = other.frameHeight
    ) == other

    companion object {
        private const val PLATFORM = "platform"
        private const val PLATFORM_ANDROID = "android"
        private const val DARK_MODE = "dark_mode"
        private const val TEXT_SCALE = "text_scale"
        private const val WINDOW = "window"
        private const val WIDTH = "width"
        private const val HEIGHT = "height"
        private const val ORIENTATION = "orientation"
        private const val SIZE = "size"
        private const val SAFE_WINDOW = "safe_window"
        private const val FRAME = "frame"
        private const val LAYOUT_DIRECTION = "layout_direction"
        private const val LTR = "ltr"
        private const val RTL = "rtl"
        private const val LOCALE = "locale"
        private const val IDENTIFIER = "identifier"
        private const val LANGUAGE = "language"
        private const val REGION = "region"

        /**
         * Reads the environment from [context]'s window and configuration, and [frame]'s size.
         *
         * @param context a context of the activity the layout is shown in, so the window is its own.
         * @param frame the frame the root view last reported, or null before it has been laid out.
         * @return the environment as it stands now.
         */
        fun from(context: Context, frame: EnvironmentFrame?): ThomasEnvironment {
            val density = context.resources.displayMetrics.density.toDouble()
            val configuration = context.resources.configuration
            return ThomasEnvironment(
                darkMode = ResourceUtils.isUiModeNight(context),
                // Through its decimal string, so a scale of 1.3f is 1.3 rather than 1.2999999523.
                textScale = configuration.fontScale.toString().toDouble(),
                windowWidth = ResourceUtils.getWindowWidthPixels(context, ignoreSafeArea = true) / density,
                windowHeight = ResourceUtils.getWindowHeightPixels(context, ignoreSafeArea = true) / density,
                windowSize = ResourceUtils.getWindowSize(context),
                safeWindowWidth = ResourceUtils.getWindowWidthPixels(context, ignoreSafeArea = false) / density,
                safeWindowHeight = ResourceUtils.getWindowHeightPixels(context, ignoreSafeArea = false) / density,
                frameWidth = frame?.width?.let { it / density },
                frameHeight = frame?.height?.let { it / density },
                isRtl = configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
                locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
            )
        }
    }
}

/**
 * The size a root view was laid out at, in pixels, on each axis its host fixed.
 *
 * An axis is null where the host let the scene's content decide it, which a root view sees as a
 * measure spec that isn't `EXACTLY`: an embedded view with an `auto` height, for one.
 *
 * @param width the laid-out width, or null where the content decides it.
 * @param height the laid-out height, or null where the content decides it.
 */
internal data class EnvironmentFrame(val width: Int?, val height: Int?)

/**
 * Tracks the frame a root view is presented in and reports it after a layout that changed it.
 *
 * A root view hands its measure specs to [onMeasure] and its laid-out size to [onLayout], and
 * calls [onConfigurationChanged] and then requests a layout when its configuration changes. That
 * way the window and the frame are read together, after the layout that settles both, and a
 * configuration change is one reading rather than a window followed by a frame.
 *
 * @param onReport called with the frame after the first layout, after any layout that changed it,
 * and after the first layout following a configuration change.
 */
internal class EnvironmentFrameTracker(private val onReport: (EnvironmentFrame) -> Unit) {

    private var fixesWidth = false
    private var fixesHeight = false
    private var configurationChanged = false

    /** The frame last reported, or null before the first layout. */
    var frame: EnvironmentFrame? = null
        private set

    fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fixesWidth = View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY
        fixesHeight = View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY
    }

    fun onLayout(width: Int, height: Int) {
        val laidOut = EnvironmentFrame(
            width = width.takeIf { fixesWidth },
            height = height.takeIf { fixesHeight }
        )
        if (laidOut == frame && !configurationChanged) return
        frame = laidOut
        configurationChanged = false
        onReport(laidOut)
    }

    /** Reports on the next layout even if the frame hasn't changed, so the new configuration is read. */
    fun onConfigurationChanged() {
        configurationChanged = true
    }
}
