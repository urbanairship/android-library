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
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * Example [ModelAdapter] that routes Airship AI evaluations to OpenAI's Chat Completions API.
 *
 * Sample code, not a shipping pattern: a real app proxies the call through its own backend so
 * the key never reaches the device. See `devapp/build.gradle` for where the dev key is read
 * from.
 *
 * [availability], [availabilityUpdates] and [retryDecision] all have interface defaults that
 * suit a network-backed model, so [respond] is the only thing to implement.
 *
 * @param apiKey The OpenAI API key.
 * @param model The OpenAI model id.
 */
class OpenAIModel(
    private val apiKey: String,
    private val model: String = "gpt-4o"
) : ModelAdapter {

    override suspend fun respond(request: ModelRequest): JsonValue = withContext(Dispatchers.IO) {
        val body = jsonMapOf(
            "model" to model,
            "response_format" to jsonMapOf(
                "type" to "json_schema",
                "json_schema" to jsonMapOf(
                    "name" to "airship_ai_response",
                    "strict" to true,
                    "schema" to strictSchema(request.schema)
                )
            ),
            "messages" to jsonListOf(
                jsonMapOf("role" to "system", "content" to request.instructions),
                jsonMapOf("role" to "user", "content" to request.prompt())
            )
        )

        parseContent(post(body))
    }

    private suspend fun post(body: JsonMap): JsonValue {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT.inWholeMilliseconds.toInt()
            readTimeout = READ_TIMEOUT.inWholeMilliseconds.toInt()
            setRequestProperty("Authorization", "Bearer $apiKey")
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
                throw IOException("OpenAI request failed ($status): $response")
            }

            return JsonValue.parseString(response)
        } finally {
            cancellation.dispose()
            connection.disconnect()
        }
    }

    /** The completion's message content, which strict mode guarantees is JSON. */
    private fun parseContent(response: JsonValue): JsonValue {
        val content = response.optMap()
            .opt("choices").optList()
            .firstOrNull()?.optMap()
            ?.opt("message")?.optMap()
            ?.get("content")?.string
            ?: throw IOException("OpenAI response had no message content: $response")

        return JsonValue.parseString(content)
    }

    internal companion object {
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        val CONNECT_TIMEOUT = 15.seconds
        val READ_TIMEOUT = 30.seconds

        /**
         * Converts an [AirshipJsonSchema] into the shape OpenAI's strict mode requires: every
         * object lists all of its properties in `required` and sets
         * `additionalProperties: false`, and a property that was optional becomes nullable
         * instead.
         *
         * Airship's `x-*` extensions are dropped — they are renderer metadata, and strict mode
         * rejects keywords it doesn't know.
         *
         * @param schema The schema to convert.
         * @return The strict-mode schema.
         */
        internal fun strictSchema(schema: AirshipJsonSchema): JsonMap = when (val type = schema.type) {
            is AirshipJsonSchema.ValueType.ObjectType -> {
                // `properties == null` means "any object" in AirshipJsonSchema, which strict
                // mode cannot express: it requires every property be listed and
                // `additionalProperties` be false. Defaulting to an empty map would quietly
                // produce a schema only `{}` satisfies, so say so instead.
                val properties = requireNotNull(type.properties) {
                    "OpenAI strict mode cannot express an open object; give the schema explicit properties"
                }
                val required = type.required?.toSet() ?: emptySet()
                jsonMapOf(
                    "type" to "object",
                    "description" to schema.description,
                    "properties" to JsonMap(
                        properties.mapValues { (name, property) ->
                            val converted = strictSchema(property)
                            if (name in required) converted.toJsonValue() else nullable(converted)
                        }
                    ),
                    "required" to properties.keys.toList(),
                    "additionalProperties" to false
                )
            }
            is AirshipJsonSchema.ValueType.ArrayType -> jsonMapOf(
                "type" to "array",
                "description" to schema.description,
                "items" to strictSchema(type.items)
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

        /** Widens a converted node's `type` to also allow null. */
        private fun nullable(schema: JsonMap): JsonValue {
            val type = schema.opt("type").string ?: return schema.toJsonValue()
            return JsonMap(
                schema.map + ("type" to jsonListOf(type, "null").toJsonValue())
            ).toJsonValue()
        }
    }
}
