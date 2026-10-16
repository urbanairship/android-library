/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.screenshot

import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalMap
import java.io.File
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * A Thomas scene fixture on disk, fetched from the pinned `urbanairship/thomas-layouts` checkout.
 *
 * [discover] is deliberately pure file IO: it runs from the parameterized runner's `@Parameters`
 * method, which executes on the normal classloader rather than inside the Robolectric sandbox, so
 * anything touching `org.json` (and therefore [JsonValue]) would silently read the stub android.jar
 * instead of a real implementation. All parsing happens in [read], from inside a test case.
 */
internal data class ThomasFixture(
    val category: String,
    val file: File
) {

    /** `Scenes/Modal/foo.yml` — how the fixture is identified in config and in the manifest. */
    val path: String = "Scenes/$category/${file.name}"

    /** `modal__foo`, the stem every screenshot for this fixture is named from. */
    val name: String = "${category.lowercase()}__${stem(file.name)}"

    /**
     * The scene payload, accepting either shape the fixture corpus carries: a bare layout with
     * `presentation` and `view` at the top level, or a full API payload with the layout buried
     * under `in_app_message.message.display.layout`.
     */
    fun read(): JsonMap {
        val text = file.readText()
        val map = when {
            file.name.endsWith(".json") -> JsonValue.parseString(text).requireMap()
            else -> JsonValue.wrap(yaml().load<Map<String, Any>>(text)).requireMap()
        }

        if (map.containsKey("presentation")) {
            return map
        }

        return map.optionalMap("in_app_message")
            ?.optionalMap("message")
            ?.optionalMap("display")
            ?.optionalMap("layout")
            ?: throw IllegalStateException("no layout found in $path")
    }

    /**
     * SnakeYAML caps collection aliases at 50 by default, which a hand-written scene blows past as
     * soon as it reuses an anchored block — a shared text appearance, say — across a few pages.
     */
    private fun yaml(): Yaml = Yaml(
        SafeConstructor(LoaderOptions().apply { maxAliasesForCollections = MAX_YAML_ALIASES })
    )

    internal companion object {
        private const val MAX_YAML_ALIASES = 10_000

        private val CATEGORIES = listOf("Modal", "Banner", "Embedded")
        private val PARSEABLE = Regex("""\.(ya?ml|json)$""")
        private val FILENAME_SAFE = Regex("""^[A-Za-z0-9._-]+$""")

        fun discover(root: File): List<ThomasFixture> {
            val fixtures = CATEGORIES.flatMap { category ->
                val dir = File(root, category)
                val files = dir.listFiles()?.filter { it.isFile && PARSEABLE.containsMatchIn(it.name) }
                files.orEmpty().sortedBy { it.name }.map { ThomasFixture(category, it) }
            }

            // A sweep that silently finds nothing would report every baseline as `gone` and read as
            // a rendering regression across the board, so an empty corpus has to stop the run.
            check(fixtures.isNotEmpty()) {
                "no Thomas scene fixtures under $root - run: ./gradlew :devapp:fetchLayouts"
            }

            val collisions = fixtures.groupBy { it.name }.filterValues { it.size > 1 }
            check(collisions.isEmpty()) {
                "fixtures collide on screenshot name: ${collisions.keys.joinToString()}"
            }

            return fixtures
        }

        private fun stem(fileName: String): String {
            val stem = fileName.replace(PARSEABLE, "").replace(Regex("""\s+"""), "_")
            require(FILENAME_SAFE.matches(stem)) {
                "fixture name has characters a screenshot file name cannot carry: $fileName"
            }
            return stem
        }
    }
}
