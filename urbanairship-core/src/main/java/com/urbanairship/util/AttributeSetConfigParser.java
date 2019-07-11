package com.urbanairship.util;

import android.content.Context;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;

/**
 * AttributeSet config parser.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AttributeSetConfigParser implements ConfigParser {

    private final Context context;
    private final AttributeSet attributeSet;

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param attributeSet The attribute set.
     */
    public AttributeSetConfigParser(@NonNull Context context, @NonNull AttributeSet attributeSet) {
        this.context = context;
        this.attributeSet = attributeSet;
    }

    @Override
    public int getCount() {
        return attributeSet.getAttributeCount();
    }

    @Override
    @Nullable
    public String getName(int index) {
        if (index >= getCount() || index < 0) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index + " count: " + getCount());
        }
        return attributeSet.getAttributeName(index);
    }

    @Override
    @Nullable
    public String getString(@NonNull String name) {
        int resourceId = attributeSet.getAttributeResourceValue(null, name, 0);
        if (resourceId != 0) {
            return context.getString(resourceId);
        }

        return attributeSet.getAttributeValue(null, name);
    }

    @Override
    @NonNull
    public String getString(@NonNull String name, @NonNull String defaultValue) {
        String value = getString(name);
        return value == null ? defaultValue : value;
    }

    @Override
    public boolean getBoolean(@NonNull String name, boolean defaultValue) {
        int resourceId = attributeSet.getAttributeResourceValue(null, name, 0);
        if (resourceId != 0) {
            return context.getResources().getBoolean(resourceId);
        }

        return attributeSet.getAttributeBooleanValue(null, name, defaultValue);
    }

    @Override
    @Nullable
    public String[] getStringArray(@NonNull String name) {
        int resourceId = attributeSet.getAttributeResourceValue(null, name, 0);
        if (resourceId != 0) {
            return context.getResources().getStringArray(resourceId);
        }

        return null;
    }


    @Override
    public int getDrawableResourceId(@NonNull String name) {
        int resourceValue = attributeSet.getAttributeResourceValue(null, name, 0);
        if (resourceValue != 0) {
            return resourceValue;
        }

        String resourceName = attributeSet.getAttributeValue(null, name);
        if (resourceName != null) {
            return context.getResources().getIdentifier(resourceName, "drawable", context.getPackageName());
        }

        return 0;
    }

    @Override
    @ColorInt
    public int getColor(@NonNull String name, @ColorInt int defaultColor) {
        int resourceId = attributeSet.getAttributeResourceValue(null, name, 0);
        if (resourceId != 0) {
            return ContextCompat.getColor(context, resourceId);
        }

        String value = getString(name);
        if (UAStringUtil.isEmpty(value)) {
            return defaultColor;
        }
        return Color.parseColor(value);
    }


    @Override
    public int getInt(@NonNull String name, int defaultValue) {
        String value = getString(name);
        if (UAStringUtil.isEmpty(value)) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }

    @Override
    public int getRawResourceId(@NonNull String name) {
        int resourceValue = attributeSet.getAttributeResourceValue(null, name, 0);
        if (resourceValue != 0) {
            return resourceValue;
        }

        String resourceName = attributeSet.getAttributeValue(null, name);
        if (resourceName != null) {
            return context.getResources().getIdentifier(resourceName, "raw", context.getPackageName());
        }

        return 0;
    }


    @Override
    public long getLong(@NonNull String name, long defaultValue) {
        String value = getString(name);
        if (UAStringUtil.isEmpty(value)) {
            return defaultValue;
        }

        return Long.parseLong(value);
    }

}
