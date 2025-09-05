/* Copyright Airship and Contributors */

package com.urbanairship.audience

import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalFieldConverted
import com.urbanairship.json.requireField
import com.urbanairship.util.FarmHashFingerprint64

internal enum class HashIdentifiers(val jsonValue: String) {
    CHANNEL("channel"),
    CONTACT("contact");

    companion object {
        fun from(value: String): HashIdentifiers? {
            return HashIdentifiers.entries.firstOrNull { it.jsonValue == value }
        }
    }
}

internal enum class HashAlgorithm(val jsonValue: String) {
    FARM("farm_hash");

    companion object {
        fun from(value: String): HashAlgorithm? {
            return HashAlgorithm.entries.firstOrNull { it.jsonValue == value }
        }
    }
}

internal data class AudienceHash(
    val prefix: String,
    val property: HashIdentifiers,
    val algorithm: HashAlgorithm,
    val seed: Long?,
    val numberOfHashBuckets: Int,
    val overrides: JsonMap?,
) : JsonSerializable {

    companion object {
        private const val KEY_PREFIX = "hash_prefix"
        private const val KEY_SEED = "hash_seed"
        private const val KEY_HASH_BUCKETS = "num_hash_buckets"
        private const val KEY_OVERRIDES = "hash_identifier_overrides"
        private const val KEY_IDENTIFIERS = "hash_identifier"
        private const val KEY_ALGORITHM = "hash_algorithm"

        /**
         * Creates a `AudienceHash` object a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for an AudienceHash.
         *
         * @hide
         */
        internal fun fromJson(json: JsonMap): AudienceHash? {
            try {
                val domain = json.optionalFieldConverted(
                    KEY_IDENTIFIERS,
                    HashIdentifiers.Companion::from
                )
                    ?: return null
                val algorithm = json.optionalFieldConverted(
                    KEY_ALGORITHM,
                    HashAlgorithm.Companion::from
                )
                    ?: return null

                return AudienceHash(
                    prefix = json.requireField(KEY_PREFIX),
                    property = domain,
                    algorithm = algorithm,
                    seed = json.optionalField(KEY_SEED),
                    numberOfHashBuckets = json.requireField(KEY_HASH_BUCKETS),
                    overrides = json.optionalField(KEY_OVERRIDES)
                )
            } catch (ex: JsonException) {
                UALog.e { "failed to parse AudienceHash from json $json" }
                return null
            }
        }
    }

    internal fun generate(properties: Map<String, String?>): ULong? {
        if (!properties.containsKey(property.jsonValue)) {
            UALog.e { "can't find device property ${property.jsonValue}" }
        }

        val key = properties[property.jsonValue] ?: return null
        val value = overrides?.optionalField<String>(key) ?: key

        val hashFunction: HashFunction = when (algorithm) {
            HashAlgorithm.FARM -> FarmHashFingerprint64::fingerprint
        }

        return hashFunction.invoke("$prefix$value").toULong() % numberOfHashBuckets.toUInt()
    }

    override fun toJsonValue(): JsonValue {
        return JsonMap
            .newBuilder()
            .put(KEY_PREFIX, prefix)
            .put(KEY_IDENTIFIERS, property.jsonValue)
            .put(KEY_ALGORITHM, algorithm.jsonValue)
            .put(KEY_SEED, (seed ?: 0))
            .put(KEY_HASH_BUCKETS, numberOfHashBuckets)
            .put(KEY_OVERRIDES, overrides)
            .build()
            .toJsonValue()
    }
}

private typealias HashFunction = (String) -> Long
