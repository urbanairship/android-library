/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.screenshot

import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import java.io.File

/**
 * Records what the sweep captured and what it passed over, for the report to surface.
 *
 * A green screenshot should never claim more than it exercised, so every fixture that relied on a
 * stub says which one, and every fixture that was passed over says why.
 *
 * Deliberately backed by the file rather than by static state: Robolectric runs each test inside a
 * sandbox classloader, and a class reloaded between cases would silently drop everything recorded
 * before it. Re-reading and rewriting on every entry also means a sweep that dies partway through
 * still leaves a readable account of how far it got.
 */
internal object ScreenshotManifest {

    private const val CAPTURED = "captured"
    private const val SKIPPED = "skipped"

    @Synchronized
    fun captured(total: Int, fixture: String, name: String, stubs: List<String>) {
        val entry = jsonMapOf(
            "fixture" to fixture,
            "name" to name,
            "stubs" to jsonListOf(*stubs.toTypedArray())
        )
        update(total, fixture, CAPTURED, entry.toJsonValue())
    }

    @Synchronized
    fun skipped(total: Int, fixture: String, reason: String) {
        val entry = jsonMapOf("fixture" to fixture, "reason" to reason)
        update(total, fixture, SKIPPED, entry.toJsonValue())
    }

    private fun update(total: Int, fixture: String, bucket: String, entry: JsonValue) {
        val file = File(ScreenshotPaths.shots, "manifest.json")
        val existing = if (file.exists()) {
            JsonValue.parseString(file.readText()).optMap()
        } else {
            JsonMap.EMPTY_MAP
        }

        // A re-run of the same fixture replaces its entry rather than doubling it up.
        val buckets = listOf(CAPTURED, SKIPPED).associateWith { key ->
            existing.opt(key).optList().list
                .filterNot { it.optMap().opt("fixture").string == fixture }
                .toMutableList()
        }
        buckets.getValue(bucket).add(entry)

        val manifest = jsonMapOf(
            "fixtures" to total,
            CAPTURED to JsonList(buckets.getValue(CAPTURED)),
            SKIPPED to JsonList(buckets.getValue(SKIPPED))
        )

        file.parentFile?.mkdirs()
        file.writeText(manifest.toJsonValue().toString())
    }
}

/** Locations handed in by Gradle; see the `wantsScreenshots` block in the module's build.gradle. */
internal object ScreenshotPaths {

    val fixtures: File get() = required("thomas.fixtures.dir")
    val shots: File get() = required("thomas.shots.dir")
    val config: File get() = required("thomas.config")

    private fun required(property: String): File {
        val value = System.getProperty(property)
        checkNotNull(value) {
            "-D$property is not set - run the sweep with: ./gradlew uitestRun --console=plain"
        }
        return File(value)
    }
}
