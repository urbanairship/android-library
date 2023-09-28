/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonListOf
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureFlagInfoTest {

    @Test
    fun testFeatureFlagParsedFromJson() {
        val json = generateFeatureFlagPayload()
        val featureFlags = json
            .require("feature_flags")
            .optList()
            .map { it.optMap() }
            .map { FeatureFlagInfo.fromJson(it) }

        assert(featureFlags.size == 1)

        val testFlag = featureFlags.first()!!
        assert(testFlag.id == "27f26d85-0550-4df5-85f0-7022fa7a5925")
        assert(1684868854000 == testFlag.created)
        assert(1684868855000 == testFlag.lastUpdated)
        assert("cool_flag" == testFlag.name)
        assert(jsonMapOf("flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925") == testFlag.reportingContext)
        assertNotNull(testFlag.audience)
        assertNotNull(testFlag.timeCriteria)
        assert(testFlag.payload is StaticPayload)
    }

    private fun generateFeatureFlagPayload(): JsonMap {
        return jsonMapOf(
            "feature_flags" to jsonListOf(
                jsonMapOf(
                "flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925",
                "created" to "2023-05-23T19:07:34.000",
                "last_updated" to "2023-05-23T19:07:35.000",
                "platforms" to jsonListOf("android"),
                "flag" to jsonMapOf(
                    "name" to "cool_flag",
                    "type" to "static",
                    "reporting_metadata" to jsonMapOf("flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925"),
                    "audience_selector" to jsonMapOf(
                        "app_version" to jsonMapOf(
                            "value" to jsonMapOf("version_matches" to "1")
                        ),
                        "hash" to jsonMapOf(
                            "audience_hash" to jsonMapOf(
                                "hash_prefix" to "27f26d85-0550-4df5-85f0-7022fa7a5925 to",
                                "num_hash_buckets" to 16384,
                                "hash_identifier" to "contact",
                                "hash_algorithm" to "farm_hash"),
                            "audience_subset" to jsonMapOf(
                                "min_hash_bucket" to 0,
                                "max_hash_bucket" to 1637
                            ),
                        )
                    ),
                    "time_criteria" to jsonMapOf(
                        "start_timestamp" to 123,
                        "end_timestamp" to 321
                    ),
                    "variables" to jsonMapOf(
                        "type" to "variant",
                        "variants" to jsonListOf(
                            jsonMapOf(
                                "id" to "dda26cb5-e40b-4bc8-abb1-eb88240f7fd7",
                                "reporting_metadata" to jsonMapOf(
                                    "flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925",
                                    "variant_id" to "dda26cb5-e40b-4bc8-abb1-eb88240f7fd7"
                                ),
                                "audience_selector" to jsonMapOf(
                                    "hash" to jsonMapOf(
                                        "audience_hash" to jsonMapOf(
                                            "hash_prefix" to "686f2c15-cf8c-47a6-ae9f-e749fc792a9d to",
                                            "num_hash_buckets" to 100,
                                            "hash_identifier" to "contact",
                                            "hash_algorithm" to "farm_hash"
                                        ),
                                        "audience_subset" to jsonMapOf(
                                            "min_hash_bucket" to 0,
                                            "max_hash_bucket" to 9
                                        )
                                    )
                                ),
                                "data" to jsonMapOf(
                                    "arbitrary_key_1" to "some_value",
                                    "arbitrary_key_2" to "some_other_value"
                                )
                            ),
                            jsonMapOf(
                                "id" to "15422380-ce8f-49df-a7b1-9755b88ec0ef",
                                "reporting_metadata" to jsonMapOf(
                                    "flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925",
                                    "variant_id" to "15422380-ce8f-49df-a7b1-9755b88ec0ef"
                                ),
                                "audience_selector" to jsonMapOf(
                                    "hash" to jsonMapOf(
                                        "audience_hash" to jsonMapOf(
                                            "hash_prefix" to "686f2c15-cf8c-47a6-ae9f-e749fc792a9d to",
                                            "num_hash_buckets" to 100,
                                            "hash_identifier" to "contact",
                                            "hash_algorithm" to "farm_hash"
                                        ),
                                        "audience_subset" to jsonMapOf(
                                            "min_hash_bucket" to 0,
                                            "max_hash_bucket" to 19
                                        )
                                    )
                                ),
                                "data" to jsonMapOf(
                                    "arbitrary_key_1" to "different_value",
                                    "arbitrary_key_2" to "different_other_value"
                                )
                            ),
                            jsonMapOf(
                                "id" to "40e08a3d-8901-40fc-a01a-e6c263bec895",
                                "reporting_metadata" to jsonMapOf(
                                    "flag_id" to "27f26d85-0550-4df5-85f0-7022fa7a5925",
                                    "variant_id" to "40e08a3d-8901-40fc-a01a-e6c263bec895"
                                ),
                                "data" to jsonMapOf(
                                    "arbitrary_key_1" to "some default value",
                                    "arbitrary_key_2" to "some other default value"
                                )
                            )
                        )
                    )
                ))
            )
        )
    }
}
