/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.urbanairship.Airship
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** A named, reusable snapshot of a sandbox's inputs. */
internal data class AirshipDebugAIPreset<State>(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val state: State
)

/** One recorded run: the inputs that produced it plus a short summary and the output. */
internal data class AirshipDebugAIRunRecord<State>(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String,
    val output: String,
    val state: State
)

/**
 * Per-usage persistence for an AI debug sandbox.
 *
 * Keeps three things as JSON, namespaced by usage key, in a dedicated `SharedPreferences` file:
 * - the last-entered session, auto-restored when the screen reappears,
 * - user-saved presets, and
 * - a short run history.
 *
 * Debug-only, so there is no migration story: data that no longer decodes (e.g. after [State]'s
 * shape changes) is simply ignored and treated as absent.
 *
 * @param usageKey Namespaces this sandbox's keys within the shared prefs file.
 * @param bundledFixtures Built-in example cases supplied by the screen. Not persisted — always
 * available.
 * @param historyLimit Maximum number of runs kept in [history].
 * @param toJson Encodes a [State] for persistence.
 * @param fromJson Decodes a [State], or `null` when the JSON no longer matches the current shape.
 */
internal class AirshipDebugAIStore<State : Any>(
    private val usageKey: String,
    val bundledFixtures: List<AirshipDebugAIPreset<State>> = emptyList(),
    private val historyLimit: Int = 10,
    private val toJson: (State) -> JsonValue,
    private val fromJson: (JsonValue) -> State?,
    private val prefs: SharedPreferences = defaultPrefs()
) {

    var presets: List<AirshipDebugAIPreset<State>> by mutableStateOf(loadList(SLOT_PRESETS, ::decodePreset))
        private set

    var history: List<AirshipDebugAIRunRecord<State>> by mutableStateOf(loadList(SLOT_HISTORY, ::decodeRun))
        private set

    // region Last session

    fun loadLastSession(): State? = prefs.getString(key(SLOT_LAST_SESSION), null)
        ?.let(::parseOrNull)
        ?.let(fromJson)

    fun saveLastSession(state: State) {
        prefs.edit().putString(key(SLOT_LAST_SESSION), toJson(state).toString()).apply()
    }

    // endregion

    // region Presets

    fun savePreset(name: String, state: State): AirshipDebugAIPreset<State> {
        val trimmed = name.trim()
        val preset = AirshipDebugAIPreset(name = trimmed.ifEmpty { "Untitled" }, state = state)
        presets = presets + preset
        persistList(SLOT_PRESETS, presets, ::encodePreset)
        return preset
    }

    fun deletePreset(id: String) {
        presets = presets.filterNot { it.id == id }
        persistList(SLOT_PRESETS, presets, ::encodePreset)
    }

    // endregion

    // region History

    fun recordRun(summary: String, output: String, state: State) {
        val record = AirshipDebugAIRunRecord(summary = summary, output = output, state = state)
        history = (listOf(record) + history).take(historyLimit)
        persistList(SLOT_HISTORY, history, ::encodeRun)
    }

    fun clearHistory() {
        history = emptyList()
        persistList(SLOT_HISTORY, history, ::encodeRun)
    }

    // endregion

    // region Import / export

    /** A shareable, pretty-printed JSON representation of a state, for export. */
    fun exportJSON(state: State): String = toJson(state).prettyPrint()

    /** Decodes a state from exported JSON, or `null` when the text isn't a valid state. */
    fun importJSON(json: String): State? = parseOrNull(json.trim())?.let(fromJson)

    // endregion

    // region Storage

    private fun key(slot: String): String = "$usageKey.$slot"

    private fun <T> loadList(slot: String, decode: (JsonValue) -> T?): List<T> {
        val raw = prefs.getString(key(slot), null) ?: return emptyList()
        val list = parseOrNull(raw)?.list ?: return emptyList()
        return list.mapNotNull(decode)
    }

    private fun <T> persistList(slot: String, values: List<T>, encode: (T) -> JsonValue) {
        val json = JsonValue.wrapOpt(values.map(encode))
        prefs.edit().putString(key(slot), json.toString()).apply()
    }

    private fun encodePreset(preset: AirshipDebugAIPreset<State>): JsonValue = jsonMapOf(
        FIELD_ID to preset.id,
        FIELD_NAME to preset.name,
        FIELD_STATE to toJson(preset.state)
    ).toJsonValue()

    private fun decodePreset(json: JsonValue): AirshipDebugAIPreset<State>? = tryOrNull {
        val map = json.requireMap()
        val state = fromJson(map.require(FIELD_STATE)) ?: return@tryOrNull null
        AirshipDebugAIPreset(
            id = map.requireField(FIELD_ID),
            name = map.requireField(FIELD_NAME),
            state = state
        )
    }

    private fun encodeRun(record: AirshipDebugAIRunRecord<State>): JsonValue = jsonMapOf(
        FIELD_ID to record.id,
        FIELD_TIMESTAMP to record.timestamp,
        FIELD_SUMMARY to record.summary,
        FIELD_OUTPUT to record.output,
        FIELD_STATE to toJson(record.state)
    ).toJsonValue()

    private fun decodeRun(json: JsonValue): AirshipDebugAIRunRecord<State>? = tryOrNull {
        val map = json.requireMap()
        val state = fromJson(map.require(FIELD_STATE)) ?: return@tryOrNull null
        AirshipDebugAIRunRecord(
            id = map.requireField(FIELD_ID),
            timestamp = map.requireField(FIELD_TIMESTAMP),
            summary = map.requireField(FIELD_SUMMARY),
            output = map.requireField(FIELD_OUTPUT),
            state = state
        )
    }

    private fun parseOrNull(json: String): JsonValue? = tryOrNull { JsonValue.parseString(json) }

    private fun <T> tryOrNull(block: () -> T?): T? = try {
        block()
    } catch (e: JsonException) {
        null
    }

    private companion object {
        const val SLOT_LAST_SESSION = "last_session"
        const val SLOT_PRESETS = "presets"
        const val SLOT_HISTORY = "history"

        const val FIELD_ID = "id"
        const val FIELD_NAME = "name"
        const val FIELD_STATE = "state"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_SUMMARY = "summary"
        const val FIELD_OUTPUT = "output"

        fun defaultPrefs(): SharedPreferences =
            Airship.application.getSharedPreferences("com.urbanairship.debug.ai", Context.MODE_PRIVATE)
    }

    // endregion
}

/** Pretty-prints any JSON value (object, array, or scalar) with a 4-space indent. */
internal fun JsonValue.prettyPrint(): String = when {
    isJsonMap -> JSONObject(toString()).toString(4)
    isJsonList -> JSONArray(toString()).toString(4)
    else -> toString()
}
