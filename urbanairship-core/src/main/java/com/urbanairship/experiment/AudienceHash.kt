package com.urbanairship.experiment

import com.urbanairship.Logger
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalFieldConverted
import com.urbanairship.json.requireField

internal enum class HashIdentifiers(val jsonValue: String) {
    CHANNEL("channel"),
    CONTACT("contact");

    companion object {
        fun from(value: String): HashIdentifiers? {
            return values().firstOrNull { it.jsonValue == value }
        }
    }
}

internal enum class HashAlgorithm(val jsonValue: String) {
    FARM("farm_hash");

    companion object {
        fun from(value: String): HashAlgorithm? {
            return values().firstOrNull { it.jsonValue == value }
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
) {

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
                val domain = json.optionalFieldConverted(KEY_IDENTIFIERS, HashIdentifiers::from)
                    ?: return null
                val algorithm = json.optionalFieldConverted(KEY_ALGORITHM, HashAlgorithm::from)
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
                Logger.e { "failed to parse AudienceHash from json $json" }
                return null
            }
        }
    }
}
