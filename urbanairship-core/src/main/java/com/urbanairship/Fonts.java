/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

/**
 * Helper class to cache and resolve font families.
 */
public class Fonts {

    private static final String[] JELLY_BEAN_SYSTEM_FONT_FAMILIES = new String[] { "serif", "sans-serif", "sans-serif-light", "sans-serif-condensed", "sans-serif-thin", "sans-serif-medium" };
    private static final String[] LOLLIPOP_SYSTEM_FONT_FAMILIES = new String[] { "sans-serif-medium", "sans-serif-black", "cursive", "casual" };
    private static final String[] MARSHMALLOW_SYSTEM_FONT_FAMILIES = new String[] { "sans-serif-smallcaps", "serif-monospace", "monospace" };

    private final Set<String> systemFonts = new HashSet<>();
    private final Map<String, Typeface> fontCache = new HashMap<>();
    private final Context context;

    @SuppressLint("StaticFieldLeak")
    private static Fonts instance;

    private Fonts(@NonNull Context context) {
        this.context = context.getApplicationContext();
        Collections.addAll(systemFonts, JELLY_BEAN_SYSTEM_FONT_FAMILIES);
        Collections.addAll(systemFonts, LOLLIPOP_SYSTEM_FONT_FAMILIES);

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
    @NonNull
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
    public synchronized void addFontFamily(@NonNull String fontFamily, @NonNull Typeface typeface) {
        fontCache.put(fontFamily, typeface);
    }

    /**
     * Gets the type face for a given font family.
     *
     * @param fontFamily The font family.
     * @return The type face or null if the font family is unavailable.
     */
    @Nullable
    public synchronized Typeface getFontFamily(@NonNull String fontFamily) {
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
                Logger.error(e, "Unable to load font from resources: %s", fontFamily);
            }
        }

        if (isSystemFont(fontFamily)) {
            Typeface typeface = Typeface.create(fontFamily, Typeface.NORMAL);
            fontCache.put(fontFamily, typeface);
            return typeface;
        }

        return null;
    }

    public boolean isSystemFont(@NonNull String fontFamily) {
        return systemFonts.contains(fontFamily);
    }
}
