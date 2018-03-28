/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.res.ResourcesCompat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helper class to cache and resolve font families.
 */
public class Fonts {

    private static String[] JELLY_BEAN_SYSTEM_FONT_FAMILIES = new String[] { "serif", "sans-serif", "sans-serif-light", "sans-serif-condensed", "sans-serif-thin", "sans-serif-medium" };
    private static String[] LOLLIPOP_SYSTEM_FONT_FAMILIES = new String[] { "sans-serif-medium", "sans-serif-black", "cursive", "casual" };
    private static String[] MARSHMALLOW_SYSTEM_FONT_FAMILIES = new String[] { "sans-serif-smallcaps", "serif-monospace", "monospace" };

    private final Set<String> systemFonts = new HashSet<>();
    private final Map<String, Typeface> fontCache = new HashMap<>();
    private final Context context;

    @SuppressLint("StaticFieldLeak")
    private static Fonts instance;

    private Fonts(Context context) {
        this.context = context.getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Collections.addAll(systemFonts, JELLY_BEAN_SYSTEM_FONT_FAMILIES);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Collections.addAll(systemFonts, LOLLIPOP_SYSTEM_FONT_FAMILIES);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Collections.addAll(systemFonts, MARSHMALLOW_SYSTEM_FONT_FAMILIES);
        }
    }

    /**
     * Gets the shared fonts instance.
     *
     * @param context The application context.
     * @return The shared instance.
     */
    public static Fonts shared(@NonNull Context context) {
        synchronized (Fonts.class) {
            if (instance == null) {
                instance = new Fonts(context);
            }
        }

        return instance;
    }

    /**
     * Adds a type face for a given font family.
     *
     * @param fontFamily The font family.
     * @param typeface The typeface.
     */
    public synchronized void addFontFamily(String fontFamily, Typeface typeface) {
        fontCache.put(fontFamily, typeface);
    }

    /**
     * Gets the type face for a given font family.
     *
     * @param fontFamily The font family.
     * @return The type face or null if the font family is unavailable.
     */
    @Nullable
    public synchronized Typeface getFontFamily(String fontFamily) {
        if (fontCache.containsKey(fontFamily)) {
            return fontCache.get(fontFamily);
        }

        int resourceId = context.getResources().getIdentifier(fontFamily, "font", context.getPackageName());
        if (resourceId != 0) {
            try {
                Typeface typeface = ResourcesCompat.getFont(context, resourceId);
                if (typeface != null) {
                    fontCache.put(fontFamily, typeface);
                    return typeface;
                }
            } catch (Resources.NotFoundException e) {
                Logger.error("Unable to load font from resources: " + fontFamily, e);
            }
        }

        if (systemFonts.contains(fontFamily)) {
            Typeface typeface = Typeface.create(fontFamily, Typeface.NORMAL);
            fontCache.put(fontFamily, typeface);
            return typeface;
        }

        return null;
    }
}
