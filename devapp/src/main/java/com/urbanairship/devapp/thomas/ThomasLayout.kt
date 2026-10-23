package com.urbanairship.devapp.thomas

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.iam.InAppMessagePreview
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import java.io.File
import java.io.InputStream
import java.util.Scanner
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

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
            @Throws(JsonException::class)
            fun from(json: JsonValue): Type {
                val content = json.requireString()
                return entries.firstOrNull { it.directory == content }
                    ?: throw JsonException("Invalid layout type: $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(directory)
    }

    sealed interface Payload {
        data class Scene(val info: LayoutInfo, val priority: Int) : Payload

        data class Message(val json: JsonValue) : Payload

        fun display(context: Context) {
            when (this) {
                is Scene -> DefaultThomasLayoutDisplay.shared.display(
                    context = context,
                    info = info,
                    priority = priority
                )
                is Message -> InAppMessagePreview(json).display(context)
            }
        }
    }

    data class LayoutFile(
        val assetsPath: String,
        val filename: String,
        val type: Type? = null
    ): JsonSerializable {

        @Throws(IllegalArgumentException::class)
        fun readFile(context: Context): JsonMap {
            val string = readStream(openStream(context))

            if (assetsPath.endsWith(".json")) {
                return JsonValue.parseString(string).optMap()
            } else if (assetsPath.endsWith(".yml") || assetsPath.endsWith(".yaml")) {
                val map: Map<String, Object> = yaml().load(string)
                return JsonValue.wrap(map).optMap()
            }

            throw IllegalArgumentException("Unsupported file type: $assetsPath")
        }

        /**
         * A file pushed to the app's external files dir wins over the bundled asset of the same
         * relative path, so a layout can be iterated on with `adb push` and no rebuild.
         */
        private fun openStream(context: Context): InputStream {
            val pushed = pushedFile(context)

            return pushed?.inputStream() ?: context.assets.open(assetsPath)
        }

        @Throws(IllegalArgumentException::class)
        private fun pushedFile(context: Context): File? {
            val root = File(context.getExternalFilesDir(null), PUSHED_LAYOUTS_FOLDER)
            val relativePath = assetsPath.removePrefix("${ThomasLayoutLoader.LAYOUTS_ROOT_FOLDER}/")
            val file = File(root, relativePath)

            // The preview activity is exported, so the path is caller-supplied.
            if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                throw IllegalArgumentException("Layout path escapes $PUSHED_LAYOUTS_FOLDER: $relativePath")
            }

            return file.takeIf { it.isFile }
        }

        /**
         * The layout JSON, unwrapped from the full in-app message payload if needed, or `null`
         * if the file isn't a scene.
         */
        private fun layoutJson(map: JsonMap): JsonMap? {
            return if (map.containsKey(KEY_PRESENTATION)) {
                // DevApp layout, without the full in-app message wrapper
                map
            } else {
                // Full API payload, from Flight Deck, etc.
                map.optionalMap("in_app_message")
                    ?.optionalMap("message")
                    ?.optionalMap("display")
                    ?.optionalMap("layout")
            }
        }

        /**
         * Reads the file as an [AirshipLayout], for hosts that render a layout inline rather
         * than displaying it themselves.
         */
        @Throws(IllegalArgumentException::class, IllegalStateException::class, JsonException::class)
        fun readAirshipLayout(context: Context): AirshipLayout {
            val layout = layoutJson(readFile(context))
                ?: throw IllegalStateException("Malformed layout file: $assetsPath")

            return AirshipLayout.fromJson(jsonMapOf("layout" to layout).toJsonValue())
        }

        @Throws(IllegalArgumentException::class, IllegalStateException::class, JsonException::class)
        fun parse(context: Context): Payload {
            val map = readFile(context)
            val layout = layoutJson(map)

            return when {
                layout != null -> Payload.Scene(
                    info = LayoutInfo(layout),
                    // Devapp-only, and absent from every layout that doesn't care. The
                    // preview path used to queue everything at 0, which left priority
                    // ordering and any selection that reads it untestable from here.
                    priority = map.optionalField<Int>(KEY_DEVAPP_PRIORITY) ?: 0
                )
                map.containsKey(KEY_DISPLAY_TYPE) -> Payload.Message(map.toJsonValue())
                else -> throw IllegalStateException(
                    "Malformed layout file, expected a '$KEY_PRESENTATION' or '$KEY_DISPLAY_TYPE' key: $assetsPath"
                )
            }
        }

        @Throws(IllegalArgumentException::class, IllegalStateException::class, JsonException::class)
        fun display(context: Context) {
            parse(context).display(context)
        }

        /**
         * SnakeYAML capped to 50 collection aliases by default, which a hand-written scene
         * blows past as soon as it reuses an anchored block — a shared text appearance, say —
         * across more than a handful of pages.
         */
        private fun yaml(): Yaml = Yaml(
            SafeConstructor(LoaderOptions().apply { maxAliasesForCollections = MAX_YAML_ALIASES })
        )

        private fun readStream(inputStream: InputStream): String {
            Scanner(inputStream, "UTF-8")
                .useDelimiter("\\A")
                .use { s -> return if (s.hasNext()) s.next() else "" }
        }

        companion object {
            private const val PUSHED_LAYOUTS_FOLDER = "agent_layouts"

            private const val KEY_PATH = "asset_path"
            private const val KEY_NAME = "name"
            private const val KEY_TYPE = "type"
            private const val KEY_PRESENTATION = "presentation"
            private const val KEY_DISPLAY_TYPE = "display_type"
            private const val KEY_DEVAPP_PRIORITY = "devapp_priority"
            private const val MAX_YAML_ALIASES = 10_000

            fun from(json: JsonValue): LayoutFile? {
                return try {
                    val content = json.requireMap()
                    LayoutFile(
                        assetsPath = content.requireField(KEY_PATH),
                        filename = content.requireField(KEY_NAME),
                        type = content.get(KEY_TYPE)?.takeUnless { it.isNull }?.let { Type.from(it) }
                    )
                } catch (ex: JsonException) {
                    UALog.e(ex) { "Failed to restore layout file from JSON ${json.toString(true)}" }
                    null
                }
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            KEY_PATH to assetsPath,
            KEY_NAME to filename,
            KEY_TYPE to type?.toJsonValue()
        ).toJsonValue()
    }
}
