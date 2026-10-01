/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.Dimension
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.urbanairship.android.layout.property.Orientation
import com.urbanairship.android.layout.property.WindowSize
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import java.io.IOException
import java.io.InputStream
import java.util.Scanner

public object ResourceUtils {

    @Throws(JsonException::class, IOException::class)
    public fun readJsonAsset(context: Context, fileName: String): JsonMap? {
        return JsonValue.parseString(readAsset(context, fileName)).map
    }

    public fun listJsonAssets(context: Context, path: String?): List<String> {
        val assetManager = context.resources.assets

        val assets = try {
            assetManager.list(path ?: "")?.toList() ?: emptyList()
        } catch (_: IOException) {
            return emptyList()
        }

        return assets.filter { it.endsWith(".json") }
    }

    @Throws(IOException::class)
    public fun readAsset(context: Context, fileName: String): String {
        return readStream(context.resources.assets.open(fileName))
    }

    @JvmStatic
    @Dimension
    public fun dpToPx(context: Context, @Dimension(unit = Dimension.Companion.DP) dp: Int): Float {
        val r = context.resources
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), r.displayMetrics
        )
    }

    @Dimension
    public fun spToPx(context: Context, @Dimension(unit = Dimension.Companion.SP) sp: Int): Float {
        val r = context.resources
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), r.displayMetrics
        )
    }

    public fun isUiModeNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        // If we're not in night mode, we're in light or unspecified mode, which we'll assume is not night.
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    public fun getOrientation(context: Context): Orientation? {
        when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> return Orientation.PORTRAIT
            Configuration.ORIENTATION_LANDSCAPE -> return Orientation.LANDSCAPE
        }
        return null
    }

    @JvmStatic
    public fun getWindowSize(context: Context): WindowSize? {
        val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return when (screenLayout) {
            Configuration.SCREENLAYOUT_SIZE_SMALL -> WindowSize.SMALL
            Configuration.SCREENLAYOUT_SIZE_NORMAL,
            Configuration.SCREENLAYOUT_SIZE_LARGE -> WindowSize.MEDIUM
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> WindowSize.LARGE
            else -> null
        }
    }

    public fun getDisplayWidthPixels(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    public fun getDisplayHeightPixels(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    /**
     * Returns the window's width in pixels, with or without the system bars.
     *
     * @param context a context of the activity whose window is measured.
     * @param ignoreSafeArea true for the whole window, false for the window less its system bars.
     * @param view a view in that window; below API 30 the window is read through it, see [getWindowPixels].
     * @return the width in pixels.
     */
    @JvmOverloads
    public fun getWindowWidthPixels(context: Context, ignoreSafeArea: Boolean, view: View? = null): Int =
        getWindowPixels(context, view).width(ignoreSafeArea)

    /**
     * Returns the window's height in pixels, with or without the system bars.
     *
     * @param context a context of the activity whose window is measured.
     * @param ignoreSafeArea true for the whole window, false for the window less its system bars.
     * @param view a view in that window; below API 30 the window is read through it, see [getWindowPixels].
     * @return the height in pixels.
     */
    @JvmOverloads
    public fun getWindowHeightPixels(context: Context, ignoreSafeArea: Boolean, view: View? = null): Int =
        getWindowPixels(context, view).height(ignoreSafeArea)

    /**
     * Returns the window's size in pixels, with and without its system bars, from one lookup.
     *
     * Below API 30 there's no window-metrics API, so the window is read through [view]: its root
     * view's size and its root insets. `displayMetrics` is no substitute there, as it already
     * leaves out the navigation bar, so subtracting the insets from it would count that twice.
     * Without an attached, laid-out [view], every value falls back to `displayMetrics`.
     *
     * @param context a context of the activity whose window is measured.
     * @param view a view in that window, or null where none is available.
     * @return the window's size with and without its system bars.
     */
    internal fun getWindowPixels(context: Context, view: View? = null): WindowPixels {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = windowManager.currentWindowMetrics
            val systemBars = metrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return WindowPixels.of(
                metrics.bounds.width(), metrics.bounds.height(), Insets.toCompatInsets(systemBars)
            )
        }

        val root = view?.takeIf { it.isAttachedToWindow }?.rootView
            ?.takeIf { it.width > 0 && it.height > 0 }
        val systemBars = view?.let(ViewCompat::getRootWindowInsets)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
        if (root == null || systemBars == null) {
            val metrics = context.resources.displayMetrics
            return WindowPixels.of(metrics.widthPixels, metrics.heightPixels, Insets.NONE)
        }
        return WindowPixels.of(root.width, root.height, systemBars)
    }

    private fun readStream(inputStream: InputStream): String {
        Scanner(inputStream, "UTF-8").useDelimiter("\\A").use { s ->
            return if (s.hasNext()) s.next() else ""
        }
    }
}
