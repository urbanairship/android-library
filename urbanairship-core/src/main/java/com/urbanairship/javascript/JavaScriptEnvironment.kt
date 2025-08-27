/* Copyright Airship and Contributors */
package com.urbanairship.javascript

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.urbanairship.R
import com.urbanairship.UALog
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale

/**
 * The Airship JavaScript Environment.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JavaScriptEnvironment private constructor(builder: Builder) {

    private val getters: List<String> = builder.getters.toList()

    /*
     * The native bridge will prototype _Airship, so inject any additional
     * functionality under _Airship and the final Airship object will have
     * access to it.
     */
    @WorkerThread
    public fun getJavaScript(context: Context): String {
        val sb = StringBuilder().append("var _Airship = {};")
        getters.forEach(sb::append)

        try {
            sb.append(readNativeBridge(context))
        } catch (e: IOException) {
            UALog.e("Failed to read native bridge.")
            return ""
        }

        return sb.toString()
    }

    public class Builder {

        internal val getters = mutableListOf<String>()

        public fun addGetter(functionName: String, value: String?): Builder {
            return addGetter(functionName, JsonValue.wrapOpt(value))
        }

        public fun addGetter(functionName: String, value: Long): Builder {
            return addGetter(functionName, JsonValue.wrapOpt(value))
        }

        public fun addGetter(functionName: String, value: JsonSerializable): Builder {
            val json = value.toJsonValue()
            val getter = String.format(
                Locale.ROOT, "_Airship.%s = function(){return %s;};", functionName, json.toString()
            )
            getters.add(getter)
            return this
        }

        public fun build(): JavaScriptEnvironment {
            return JavaScriptEnvironment(this)
        }
    }

    public companion object {
        public fun newBuilder(): Builder {
            return Builder()
        }

        /**
         * Helper method to read the native bridge from resources.
         *
         * @return The native bridge.
         * @throws IOException if output steam read or write operations fail.
         */
        @WorkerThread
        @Throws(IOException::class)
        private fun readNativeBridge(context: Context): String {
            val input = context.resources.openRawResource(R.raw.ua_native_bridge)
            val outputStream = ByteArrayOutputStream()

            try {
                val buffer = ByteArray(1024)
                var length: Int

                while ((input.read(buffer).also { length = it }) != -1) {
                    outputStream.write(buffer, 0, length)
                }

                return outputStream.toString()
            } finally {
                try {
                    input.close()
                    outputStream.close()
                } catch (e: Exception) {
                    UALog.d(e, "Failed to close streams")
                }
            }
        }
    }
}
