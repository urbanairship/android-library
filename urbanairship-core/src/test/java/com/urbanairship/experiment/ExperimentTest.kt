/* Copyright Airship and Contributors */

package com.urbanairship.experiment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.AudienceHash
import com.urbanairship.audience.AudienceHashSelector
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.BucketSubset
import com.urbanairship.audience.HashAlgorithm
import com.urbanairship.audience.HashIdentifiers
import com.urbanairship.audience.CompoundAudienceSelector
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ExperimentTest {

    @Test
    public fun testParsing() {
        val json = """
            {
           "created" : "2023-07-10T18:10:46.203",
           "experiment_definition" : {
              "audience_selector" : {
                 "hash" : {
                    "audience_hash" : {
                       "hash_algorithm" : "farm_hash",
                       "hash_identifier" : "contact",
                       "hash_prefix" : "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c:",
                       "num_hash_buckets" : 16384
                    },
                    "audience_subset" : {
                       "max_hash_bucket" : 8192,
                       "min_hash_bucket" : 0
                    }
                 }
              },
              "experiment_type" : "holdout",
              "message_exclusions" : [
                 {
                    "message_type" : {
                       "value" : {
                          "equals" : "transactional"
                       }
                    }
                 }
              ],
              "reporting_metadata" : {
                 "experiment_id" : "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c"
              },
              "time_criteria" : {
                 "end_timestamp" : 1689091608000,
                 "start_timestamp" : 1689012595000
              },
              "type" : "static"
           },
           "experiment_id" : "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c",
           "last_updated" : "2023-07-11T16:06:49.003"
        }
        """.trimIndent()

        val parsed = Experiment.fromJson(JsonValue.parseString(json).requireMap())

        val expected = Experiment(
            id = "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c",
            lastUpdated = 1689091609000,
            created = 1689012646000,
            reportingMetadata = jsonMapOf("experiment_id" to "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c"),
            audience = AudienceSelector
                .newBuilder()
                .setAudienceHashSelector(AudienceHashSelector(
                    hash = AudienceHash(
                        prefix = "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c:",
                        property = HashIdentifiers.CONTACT,
                        algorithm = HashAlgorithm.FARM,
                        seed = null,
                        numberOfHashBuckets = 16384,
                        overrides = null
                    ),
                    bucket = BucketSubset(0U, 8192U),

                ))
                .build(),
            exclusions = listOf(
                MessageCriteria(
                    messageTypePredicate = JsonPredicate.parse(JsonValue.parseString("{\"value\": {\"equals\": \"transactional\"}}")),
                    campaignPredicate = null)
            ),
            timeCriteria = TimeCriteria(1689012595000L, 1689091608000L),
            type = ExperimentType.HOLDOUT_GROUP,
            resolutionType = ResolutionType.STATIC
        )

        assertEquals(expected, parsed)
    }

    @Test
    public fun testParsingWithCompoundAudience() {
        val json = """
            {
               "created": "2023-07-10T18:10:46.203",
               "experiment_definition": {
                 "audience_selector": {
                   "hash": {
                     "audience_hash": {
                       "hash_algorithm": "farm_hash",
                       "hash_identifier": "contact",
                       "hash_prefix": "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c:",
                       "num_hash_buckets": 16384
                     },
                     "audience_subset": {
                       "max_hash_bucket": 8192,
                       "min_hash_bucket": 0
                     }
                   }
                 },
                 "compound_audience": {
                   "selector": {
                     "type": "atomic",
                     "audience": {
                       "new_user": true
                     }
                   }
                 },
                 "experiment_type": "holdout",
                 "message_exclusions": [
                   {
                     "message_type": {
                       "value": {
                         "equals": "transactional"
                       }
                     }
                   }
                 ],
                 "reporting_metadata": {
                   "experiment_id": "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c"
                 },
                 "time_criteria": {
                   "end_timestamp": 1689091608000,
                   "start_timestamp": 1689012595000
                 },
                 "type": "static"
               },
               "experiment_id": "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c",
               "last_updated": "2023-07-11T16:06:49.003"
             }
        """.trimIndent()
        val parsed = Experiment.fromJson(JsonValue.parseString(json).requireMap())

        val expected = Experiment(
            id = "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c",
            lastUpdated = 1689091609000,
            created = 1689012646000,
            reportingMetadata = jsonMapOf("experiment_id" to "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c"),
            audience = AudienceSelector
                .newBuilder()
                .setAudienceHashSelector(AudienceHashSelector(
                    hash = AudienceHash(
                        prefix = "cf9b8c05-05e2-4b8e-a2a3-7ed06d99cc1c:",
                        property = HashIdentifiers.CONTACT,
                        algorithm = HashAlgorithm.FARM,
                        seed = null,
                        numberOfHashBuckets = 16384,
                        overrides = null
                    ),
                    bucket = BucketSubset(0U, 8192U),

                    ))
                .build(),
            compoundAudienceSelector = ExperimentCompoundAudience(
                selector = CompoundAudienceSelector.Atomic(AudienceSelector.newBuilder().setNewUser(true).build())
            ),
            exclusions = listOf(
                MessageCriteria(
                    messageTypePredicate = JsonPredicate.parse(JsonValue.parseString("{\"value\": {\"equals\": \"transactional\"}}")),
                    campaignPredicate = null)
            ),
            timeCriteria = TimeCriteria(1689012595000L, 1689091608000L),
            type = ExperimentType.HOLDOUT_GROUP,
            resolutionType = ResolutionType.STATIC
        )

        assertEquals(expected, parsed)
    }
}
