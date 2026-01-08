package com.urbanairship.devapp.thomas

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.iam.InAppMessagePreview
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import java.io.InputStream
import java.util.Scanner
import org.json.JSONException
import org.yaml.snakeyaml.Yaml

internal class ThomasLayout {
    enum class Type(val directory: String): JsonSerializable {
        SCENE_BANNERS("Scenes/Banner"),
        SCENE_EMBEDDED("Scenes/Embedded"),
        SCENE_MODALS("Scenes/Modal"),
        MESSAGE_BANNERS("Messages/Banner"),
        MESSAGE_FULLSCREEN("Messages/Fullscreen"),
        MESSAGE_HTML("Messages/HTML"),
        MESSAGE_MODAL("Messages/Modal");

        companion object {
            @Throws(JSONException::class)
            fun from(json: JsonValue): Type {
                val content = json.requireString()
                return entries.firstOrNull { it.directory == content }
                    ?: throw JsonException("Invalid layout type: $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(directory)
    }

    data class LayoutFile(
        val assetsPath: String,
        val filename: String,
        val type: Type
    ): JsonSerializable {

        @Throws(IllegalArgumentException::class)
        fun readFile(context: Context): JsonMap {
            val inputStream = context.assets.open(assetsPath)
            val string = readStream(inputStream)

            if (assetsPath.endsWith(".json")) {
                return JsonValue.parseString(string).optMap()
            } else if (assetsPath.endsWith(".yml") || assetsPath.endsWith(".yaml")) {
                val map: Map<String, Object> = Yaml().load(string)
                return JsonValue.wrap(map).optMap()
            }

            throw IllegalArgumentException("Unsupported file type: $assetsPath")
        }

        @Throws(IllegalArgumentException::class, IllegalStateException::class, JsonException::class)
        fun display(context: Context) {
            val map = readFile(context)

            when(type) {
                Type.SCENE_BANNERS, Type.SCENE_EMBEDDED, Type.SCENE_MODALS -> {
                    val payload = if (map.containsKey("presentation")) {
                        // DevApp layout, without the full in-app message wrapper
                        LayoutInfo(map)
                    } else {
                        // Full API payload, from Flight Deck, etc.
                        val layoutInfo = map.optionalMap("in_app_message")
                            ?.optionalMap("message")
                            ?.optionalMap("display")
                            ?.optionalMap("layout")
                            ?: throw IllegalStateException("Malformed layout file: $assetsPath")

                        LayoutInfo(layoutInfo)
                    }

                    DefaultThomasLayoutDisplay.shared.display(context, payload)
                }
                Type.MESSAGE_BANNERS, Type.MESSAGE_FULLSCREEN, Type.MESSAGE_HTML, Type.MESSAGE_MODAL -> {
                    InAppMessagePreview(map.toJsonValue()).display(context)
                }
            }
        }

        private fun readStream(inputStream: InputStream): String {
            Scanner(inputStream, "UTF-8")
                .useDelimiter("\\A")
                .use { s -> return if (s.hasNext()) s.next() else "" }
        }

        companion object {
            private const val KEY_PATH = "asset_path"
            private const val KEY_NAME = "name"
            private const val KEY_TYPE = "type"

            fun from(json: JsonValue): LayoutFile? {
                return try {
                    val content = json.requireMap()
                    LayoutFile(
                        assetsPath = content.requireField(KEY_PATH),
                        filename = content.requireField(KEY_NAME),
                        type = Type.from(content.require(KEY_TYPE))
                    )
                } catch (ex: JSONException) {
                    UALog.e(ex) { "Failed to restore layout file from JSON ${json.toString(true)}" }
                    null
                }
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_PATH to assetsPath,
            KEY_NAME to filename,
            KEY_TYPE to type.toJsonValue()
        ).toJsonValue()
    }
}
