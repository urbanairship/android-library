/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.Dimension
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

    public fun getWindowWidthPixels(context: Context, ignoreSafeArea: Boolean): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics

            if (ignoreSafeArea) {
                return windowMetrics.bounds.width()
            }

            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            // This won't be the exact window width and we can't handle ignoreSafeArea
            // but this is the closest value we can get for API<30.
            // getMetrics() doesn't work well with the insets.
            // getRealMetrics() would be better but there can be cases it won't work.
            return displayMetrics.widthPixels
        }
    }

    public fun getWindowHeightPixels(context: Context, ignoreSafeArea: Boolean): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics

            if (ignoreSafeArea) {
                return windowMetrics.bounds.height()
            }

            val insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return windowMetrics.bounds.height() - insets.top - insets.bottom
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            // This won't be the exact window height and we can't handle ignoreSafeArea
            // but this is the closest value we can get for API<30.
            // getMetrics() doesn't work well with the insets.
            // getRealMetrics() would be better but it won't work for split screens for example.
            return displayMetrics.heightPixels
        }
    }

    private fun readStream(inputStream: InputStream): String {
        Scanner(inputStream, "UTF-8").useDelimiter("\\A").use { s ->
            return if (s.hasNext()) s.next() else ""
        }
    }
}
