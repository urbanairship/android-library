/* Copyright Airship and Contributors */
package com.urbanairship.devapp.ai

import com.urbanairship.ai.ModelAdapter
import com.urbanairship.ai.ModelRequest
import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * Example [ModelAdapter] that routes Airship AI evaluations to Gemini's `generateContent` API.
 *
 * Sample code, not a shipping pattern: a real app proxies the call through its own backend so
 * the key never reaches the device. See `devapp/build.gradle` for where the dev key is read
 * from.
 *
 * [availability], [availabilityUpdates] and [retryDecision] all have interface defaults that
 * suit a network-backed model, so [respond] is the only thing to implement.
 *
 * @param apiKey The Gemini API key.
 * @param model The model id. `GET https://generativelanguage.googleapis.com/v1beta/models`
 * with the same key lists what the key can reach.
 */
class GeminiModel(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
) : ModelAdapter {

    override suspend fun respond(request: ModelRequest): JsonValue = withContext(Dispatchers.IO) {
        val body = jsonMapOf(
            // Gemini keeps the system instructions off the conversation turns rather than
            // giving them a role of their own.
            "systemInstruction" to jsonMapOf(
                "parts" to jsonListOf(jsonMapOf("text" to request.instructions))
            ),
            "contents" to jsonListOf(
                jsonMapOf(
                    "role" to "user",
                    "parts" to jsonListOf(jsonMapOf("text" to request.prompt()))
                )
            ),
            // Both keys are needed: the schema alone still yields prose unless the response
            // MIME type asks for JSON.
            "generationConfig" to jsonMapOf(
                "responseMimeType" to "application/json",
                "responseSchema" to responseSchema(request.schema)
            )
        )

        parseContent(post(body))
    }

    private suspend fun post(body: JsonMap): JsonValue {
        val url = "$ENDPOINT/${model}:generateContent"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT.inWholeMilliseconds.toInt()
            readTimeout = READ_TIMEOUT.inWholeMilliseconds.toInt()
            // Header rather than the `?key=` query parameter the API also accepts: a key in a
            // URL ends up in logs and crash reports.
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        // HttpURLConnection doesn't observe coroutine cancellation, so a superseded keystroke
        // would otherwise keep paying for a call whose answer is already discarded. Closing the
        // socket is what actually stops it.
        val cancellation = coroutineContext.job.invokeOnCompletion { connection.disconnect() }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (status !in 200..299) {
                throw IOException("Gemini request failed ($status): $response")
            }

            return JsonValue.parseString(response)
        } finally {
            cancellation.dispose()
            connection.disconnect()
        }
    }

    /**
     * The first candidate's text, which the JSON response MIME type guarantees is JSON.
     *
     * A candidate with no parts is the shape a safety block takes, so the `finishReason` is
     * worth surfacing rather than reporting this as malformed JSON.
     */
    private fun parseContent(response: JsonValue): JsonValue {
        val candidate = response.optMap()
            .opt("candidates").optList()
            .firstOrNull()?.optMap()
            ?: throw IOException("Gemini response had no candidates: $response")

        val text = candidate
            .opt("content").optMap()
            .opt("parts").optList()
            .firstOrNull()?.optMap()
            ?.get("text")?.string
            ?: throw IOException(
                "Gemini candidate had no text (finishReason=${candidate.opt("finishReason").string})"
            )

        return JsonValue.parseString(text)
    }

    internal companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        val CONNECT_TIMEOUT = 15.seconds
        val READ_TIMEOUT = 30.seconds

        /**
         * Converts an [AirshipJsonSchema] into Gemini's `responseSchema`.
         *
         * Nearly one-to-one, unlike the OpenAI strict-mode conversion next door: Gemini takes
         * `required` as authored rather than demanding every property, so an optional property
         * stays optional instead of becoming nullable, and an open object — `properties` unset,
         * meaning "any object" — is expressible by simply omitting the key.
         *
         * Airship's `x-*` extensions are dropped. They are renderer metadata, and walking the
         * typed schema rather than its JSON leaves them behind for free.
         *
         * @param schema The schema to convert.
         * @return The `responseSchema` value.
         */
        internal fun responseSchema(schema: AirshipJsonSchema): JsonMap = when (val type = schema.type) {
            is AirshipJsonSchema.ValueType.ObjectType -> jsonMapOf(
                "type" to "object",
                "description" to schema.description,
                "properties" to type.properties?.let { properties ->
                    JsonMap(properties.mapValues { responseSchema(it.value).toJsonValue() })
                },
                "required" to type.required
            )
            is AirshipJsonSchema.ValueType.ArrayType -> jsonMapOf(
                "type" to "array",
                "description" to schema.description,
                "items" to responseSchema(type.items)
            )
            is AirshipJsonSchema.ValueType.StringType -> jsonMapOf(
                "type" to "string",
                "description" to schema.description,
                "enum" to type.choices
            )
            AirshipJsonSchema.ValueType.BooleanType -> scalar("boolean", schema.description)
            AirshipJsonSchema.ValueType.IntegerType -> scalar("integer", schema.description)
            AirshipJsonSchema.ValueType.NumberType -> scalar("number", schema.description)
        }

        private fun scalar(type: String, description: String?): JsonMap =
            jsonMapOf("type" to type, "description" to description)
    }
}
