/* Copyright Airship and Contributors */
package com.urbanairship

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat

/**
 * Helper class to cache and resolve font families.
 */
public class Fonts private constructor(context: Context) {

    private val systemFonts: Set<String>
    private val fontCache = mutableMapOf<String, Typeface>()
    private val context = context.applicationContext

    init {
        val fonts = JELLY_BEAN_SYSTEM_FONT_FAMILIES.toMutableSet()
        fonts.addAll(LOLLIPOP_SYSTEM_FONT_FAMILIES)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fonts.addAll(MARSHMALLOW_SYSTEM_FONT_FAMILIES)
        }

        systemFonts = fonts.toSet()
    }

    /**
     * Adds a type face for a given font family.
     *
     * @param fontFamily The font family.
     * @param typeface The typeface.
     */
    @Synchronized
    public fun addFontFamily(fontFamily: String, typeface: Typeface) {
        fontCache[fontFamily] = typeface
    }

    /**
     * Gets the type face for a given font family.
     *
     * @param fontFamily The font family.
     * @return The type face or null if the font family is unavailable.
     */
    @Synchronized
    public fun getFontFamily(fontFamily: String): Typeface? {
        if (fontCache.containsKey(fontFamily)) {
            return fontCache[fontFamily]
        }

        val resourceId = context.resources.getIdentifier(fontFamily, "font", context.packageName)
        if (resourceId != 0) {
            try {
                val typeface = ResourcesCompat.getFont(context, resourceId)
                if (typeface != null) {
                    fontCache[fontFamily] = typeface
                    return typeface
                }
            } catch (e: Resources.NotFoundException) {
                UALog.e(e, "Unable to load font from resources: %s", fontFamily)
            }
        }

        if (isSystemFont(fontFamily)) {
            val typeface = Typeface.create(fontFamily, Typeface.NORMAL)
            fontCache[fontFamily] = typeface
            return typeface
        }

        return null
    }

    public fun isSystemFont(fontFamily: String): Boolean {
        return systemFonts.contains(fontFamily)
    }

    public companion object {

        private val JELLY_BEAN_SYSTEM_FONT_FAMILIES = arrayOf(
            "serif",
            "sans-serif",
            "sans-serif-light",
            "sans-serif-condensed",
            "sans-serif-thin",
            "sans-serif-medium"
        )
        private val LOLLIPOP_SYSTEM_FONT_FAMILIES =
            arrayOf(
                "sans-serif-medium",
                "sans-serif-black",
                "cursive",
                "casual"
            )
        private val MARSHMALLOW_SYSTEM_FONT_FAMILIES =
            arrayOf(
                "sans-serif-smallcaps",
                "serif-monospace",
                "monospace"
            )

        @SuppressLint("StaticFieldLeak")
        private var instance: Fonts? = null

        /**
         * Gets the shared fonts instance.
         *
         * @param context The application context.
         * @return The shared instance.
         */
        @JvmStatic
        public fun shared(context: Context): Fonts {
            synchronized(Fonts::class.java) {
                val result = instance ?: kotlin.run {
                    val created = Fonts(context)
                    instance = created
                    created
                }

                return result
            }
        }
    }
}
