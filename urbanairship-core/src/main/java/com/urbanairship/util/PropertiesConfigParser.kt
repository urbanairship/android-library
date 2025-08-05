/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import com.urbanairship.UALog
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Properties

/**
 * Properties file config parser.
 * @hide
 */
internal class PropertiesConfigParser private constructor(
    private val context: Context,
    private val propertyNames: List<String>,
    private val propertyValues: Map<String, String>
) : ConfigParser {

    override val count: Int = propertyNames.size
    override fun getName(index: Int): String? = propertyNames[index]
    override fun getString(name: String): String? = propertyValues[name]

    override fun getString(name: String, defaultValue: String): String {
        return getString(name) ?: defaultValue
    }

    override fun getBoolean(name: String, defaultValue: Boolean): Boolean {
        val value = getString(name)
        if (value.isNullOrEmpty()) {
            return defaultValue
        }

        return value.toBoolean()
    }

    override fun getStringArray(name: String): Array<String>? {
        return propertyValues[name]
            ?.split("[, ]+".toRegex())
            ?.dropLastWhile { it.isEmpty() }
            ?.toTypedArray()
    }

    override fun getDrawableResourceId(name: String): Int {
        return context.resources.getIdentifier(getString(name), "drawable", context.packageName)
    }

    override fun getRawResourceId(name: String): Int {
        return context.resources.getIdentifier(getString(name), "raw", context.packageName)
    }

    override fun getLong(name: String, defaultValue: Long): Long {
        val value = getString(name)
        if (value.isNullOrEmpty()) {
            return defaultValue
        }

        return value.toLong()
    }

    override fun getInt(name: String, defaultValue: Int): Int {
        val value = getString(name)
        if (value.isNullOrEmpty()) {
            return defaultValue
        }

        return value.toInt()
    }

    @ColorInt
    override fun getColor(name: String, @ColorInt defaultValue: Int): Int {
        val value = getString(name)
        if (value.isNullOrEmpty()) {
            return defaultValue
        }

        return Color.parseColor(value)
    }

     companion object {

        /**
         * Factory method to create a config parser from a file in the assets directory.
         *
         * @param context The application context.
         * @param propertiesFile The properties file.
         * @return A PropertiesConfigParser instance.
         * @throws IOException if properties file cannot be found.
         */
        @Throws(IOException::class)
        fun fromAssets(context: Context, propertiesFile: String): PropertiesConfigParser {
            val resources = context.resources
            val assetManager = resources.assets

            val assets = assetManager.list("")
            //bail if the properties file can't be found
            if (assets?.contains(propertiesFile) != true) {
                throw FileNotFoundException("Unable to find properties file: $propertiesFile")
            }

            val properties = Properties()
            var inStream: InputStream? = null

            try {
                inStream = assetManager.open(propertiesFile)
                properties.load(inStream)
                return fromProperties(context, properties)
            } finally {
                if (inStream != null) {
                    try {
                        inStream.close()
                    } catch (e: IOException) {
                        UALog.d(e, "Failed to close input stream.")
                    }
                }
            }
        }

        /**
         * Factory method to create a config parser.
         *
         * @param context The application context.
         * @param properties The properties.
         * @return A PropertiesConfigParser instance.
         */
        public fun fromProperties(
            context: Context, properties: Properties
        ): PropertiesConfigParser {

            val map = properties
                .stringPropertyNames()
                .mapNotNull { name ->
                    val value = properties.getProperty(name)?.trim { it <= ' ' }
                    if (value.isNullOrEmpty()) { return@mapNotNull null }
                    name to value
                }
                .toMap()

            return PropertiesConfigParser(context, map.keys.toList(), map)
        }
    }
}
