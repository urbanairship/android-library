package com.urbanairship.util

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import kotlin.jvm.Throws

/**
 * AttributeSet config parser.
 *
 * @hide
 */
public open class AttributeSetConfigParser(
    private val context: Context,
    private val attributeSet: AttributeSet
) : ConfigParser {

    override val count: Int = attributeSet.attributeCount

    @Throws(IndexOutOfBoundsException::class)
    override fun getName(index: Int): String? {
        if (index >= count || index < 0) {
            throw IndexOutOfBoundsException("Index out of bounds: $index count: $count")
        }
        return attributeSet.getAttributeName(index)
    }

    override fun getString(name: String): String? {
        val resourceId = attributeSet.getAttributeResourceValue(null, name, 0)
        if (resourceId != 0) {
            return context.getString(resourceId)
        }

        return attributeSet.getAttributeValue(null, name)
    }

    override fun getString(name: String, defaultValue: String): String {
        return getString(name) ?: defaultValue
    }

    override fun getBoolean(name: String, defaultValue: Boolean): Boolean {
        val resourceId = attributeSet.getAttributeResourceValue(null, name, 0)
        if (resourceId != 0) {
            return context.resources.getBoolean(resourceId)
        }

        return attributeSet.getAttributeBooleanValue(null, name, defaultValue)
    }

    override fun getStringArray(name: String): Array<String>? {
        val resourceId = attributeSet.getAttributeResourceValue(null, name, 0)
        if (resourceId != 0) {
            return context.resources.getStringArray(resourceId)
        }

        val value = attributeSet.getAttributeValue(null, name) ?: return null

        return value
            .split("[, ]+".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
    }

    override fun getDrawableResourceId(name: String): Int {
        val resourceValue = attributeSet.getAttributeResourceValue(null, name, 0)
        if (resourceValue != 0) {
            return resourceValue
        }

        val resourceName = attributeSet.getAttributeValue(null, name) ?: return 0

        return context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    }

    @ColorInt
    override fun getColor(name: String, @ColorInt defaultValue: Int): Int {
        val resourceId = attributeSet.getAttributeResourceValue(null, name, 0)
        if (resourceId != 0) {
            return ContextCompat.getColor(context, resourceId)
        }

        val value = getString(name)
        if (value.isNullOrEmpty()) {
            return defaultValue
        }
        return Color.parseColor(value)
    }

    @Throws(NumberFormatException::class)
    override fun getInt(name: String, defaultValue: Int): Int {
        val value = getString(name)
        if (value.isNullOrEmpty()) {
            return defaultValue
        }

        return value.toInt()
    }

    override fun getRawResourceId(name: String): Int {
        val resourceValue = attributeSet.getAttributeResourceValue(null, name, 0)
        if (resourceValue != 0) {
            return resourceValue
        }

        val resourceName = attributeSet.getAttributeValue(null, name) ?: return 0

        return context.resources.getIdentifier(resourceName, "raw", context.packageName)
    }

    @Throws(NumberFormatException::class)
    override fun getLong(name: String, defaultValue: Long): Long {
        val value = getString(name)
        if (value.isNullOrEmpty()) {
            return defaultValue
        }

        return value.toLong()
    }
}
