/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.info

import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ThomasFormRegistrationOverridesTest {

    private val state = JsonValue.wrap(
        jsonMapOf(
            "\$forms" to jsonMapOf(
                "current" to jsonMapOf(
                    "data" to jsonMapOf(
                        "children" to jsonMapOf(
                            "opt_in" to jsonMapOf("value" to true),
                            "opt_out" to jsonMapOf("value" to false),
                            "referral" to jsonMapOf("value" to "friend")
                        )
                    )
                )
            )
        )
    )

    @Test
    public fun testParsesOverrides() {
        val overrides = overrides(
            """
            {
              "input_id": "email",
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": {
                "type": "commercial",
                "commercial_opted_in": { "type": "value", "value": true },
                "open_tracking_opted_in": { "type": "state", "scope": ["a", "b"] },
                "properties": {
                  "set": { "source": { "type": "value", "value": "layout" } },
                  "remove": ["old"]
                }
              }
            }
            """
        )

        val registration = overrides.email!!.single().registration
        assertEquals("email", overrides.email!!.single().inputId)
        assertEquals(ThomasEmailRegistrationOptions.Type.COMMERCIAL, registration.type)
        assertEquals(ThomasPatchValue.Literal(JsonValue.wrap(true)), registration.commercialOptedIn)
        assertEquals(ThomasPatchValue.State(listOf("a", "b")), registration.openTrackingOptedIn)
        assertEquals(
            ThomasEmailRegistrationPatch.PropertiesPatch(
                set = mapOf("source" to ThomasPatchValue.Literal(JsonValue.wrap("layout"))),
                remove = listOf("old")
            ),
            registration.properties
        )
    }

    @Test(expected = JsonException::class)
    public fun testRejectsUnknownPatchValueType() {
        ThomasPatchValue.fromJson(JsonValue.parseString("""{ "type": "other", "value": true }"""))
    }

    @Test
    public fun testMatchingOverrideSwitchesTypeAndKeepsProperties() {
        val overrides = overrides(
            """
            {
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": {
                "type": "commercial",
                "commercial_opted_in": { "type": "value", "value": true }
              }
            }
            """
        )

        val resolved = overrides.resolveEmail(
            inputId = "email",
            options = ThomasEmailRegistrationOptions.Transactional(jsonMapOf("keep" to "me")),
            state = state
        )

        assertEquals(
            ThomasEmailRegistrationOptions.Commercial(
                properties = jsonMapOf("keep" to "me"),
                optedIn = true
            ),
            resolved
        )
    }

    @Test
    public fun testSkipsOverridesForOtherInputsAndUnmatchedPredicates() {
        val overrides = overrides(
            """
            {
              "input_id": "other",
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": { "type": "commercial" }
            }
            """,
            """
            {
              "when_state_matches": $OPT_OUT_PREDICATE,
              "registration": { "type": "transactional" }
            }
            """
        )

        val options = ThomasEmailRegistrationOptions.DoubleOptIn(null)
        assertEquals(options, overrides.resolveEmail("email", options, state))
    }

    @Test
    public fun testAppliesMatchingOverridesInOrder() {
        val overrides = overrides(
            """
            {
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": {
                "type": "commercial",
                "open_tracking_opted_in": { "type": "value", "value": true }
              }
            }
            """,
            """
            {
              "input_id": "email",
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": {
                "commercial_opted_in": { "type": "value", "value": true },
                "open_tracking_opted_in": { "type": "value", "value": false },
                "click_tracking_opted_in": { "type": "state", "scope": $OPT_OUT_SCOPE }
              }
            }
            """
        )

        assertEquals(
            ThomasEmailRegistrationOptions.Commercial(
                properties = null,
                optedIn = true,
                openTrackingOptedIn = false,
                clickTrackingOptedIn = false
            ),
            overrides.resolveEmail("email", null, state)
        )
    }

    @Test
    public fun testCommercialWithoutOptInResolvesFalse() {
        val overrides = overrides(
            """
            {
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": { "type": "commercial" }
            }
            """
        )

        assertEquals(
            ThomasEmailRegistrationOptions.Commercial(properties = null, optedIn = false),
            overrides.resolveEmail("email", null, state)
        )
    }

    @Test
    public fun testNoTypeResolvesNull() {
        val overrides = overrides(
            """
            {
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": { "commercial_opted_in": { "type": "value", "value": true } }
            }
            """
        )

        assertNull(overrides.resolveEmail("email", null, state))
    }

    @Test
    public fun testPropertiesRemoveThenSetWithStateValues() {
        val overrides = overrides(
            """
            {
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": {
                "properties": {
                  "set": {
                    "both": { "type": "value", "value": "set" },
                    "referral": { "type": "state", "scope": $REFERRAL_SCOPE },
                    "missing": { "type": "state", "scope": ["${'$'}forms", "nope"] }
                  },
                  "remove": ["both", "gone", "absent"]
                }
              }
            }
            """
        )

        val resolved = overrides.resolveEmail(
            inputId = "email",
            options = ThomasEmailRegistrationOptions.DoubleOptIn(
                jsonMapOf("both" to "original", "gone" to 1, "kept" to 2)
            ),
            state = state
        )

        assertEquals(
            ThomasEmailRegistrationOptions.DoubleOptIn(
                jsonMapOf("both" to "set", "referral" to "friend", "kept" to 2)
            ),
            resolved
        )
    }

    @Test
    public fun testStateValueOfWrongTypeLeavesFieldUnchanged() {
        val overrides = overrides(
            """
            {
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": {
                "commercial_opted_in": { "type": "state", "scope": $REFERRAL_SCOPE }
              }
            }
            """
        )

        val options = ThomasEmailRegistrationOptions.Commercial(properties = null, optedIn = true)
        assertEquals(options, overrides.resolveEmail("email", options, state))
    }

    @Test
    public fun testFormChannelsRegisterEmailInputWithoutOwnOptions() {
        val overrides = overrides(
            """
            {
              "input_id": "email",
              "when_state_matches": $OPT_IN_PREDICATE,
              "registration": { "type": "double_opt_in" }
            }
            """
        )

        val form = State.Form(
            identifier = "form",
            formType = FormType.Form,
            formResponseType = null,
            validationMode = FormValidationMode.IMMEDIATE,
            initialChildrenValues = emptyMap()
        )
            .copyWithFormInput(emailInput("email", "a@example.com"))
            .copyWithFormInput(emailInput("other", "b@example.com"))

        assertEquals(emptyList<ThomasChannelRegistration>(), form.channels())
        assertEquals(
            listOf(
                ThomasChannelRegistration.Email(
                    "a@example.com",
                    ThomasEmailRegistrationOptions.DoubleOptIn(null)
                )
            ),
            form.channels(overrides, state)
        )
    }

    private fun emailInput(identifier: String, address: String) = ThomasFormField.TextInput(
        textInput = FormInputType.EMAIL,
        identifier = identifier,
        originalValue = address,
        fieldType = ThomasFormField.FieldType.just(address)
    )

    private fun overrides(vararg email: String): ThomasFormRegistrationOverrides =
        ThomasFormRegistrationOverrides.fromJson(
            JsonValue.parseString("""{ "email": [${email.joinToString(",")}] }""")
        )

    private companion object {
        val OPT_IN_PREDICATE = """
            {
              "scope": ["${'$'}forms", "current", "data", "children", "opt_in"],
              "key": "value",
              "value": { "equals": true }
            }
        """
        val OPT_OUT_PREDICATE = """
            {
              "scope": ["${'$'}forms", "current", "data", "children", "opt_out"],
              "key": "value",
              "value": { "equals": true }
            }
        """
        val OPT_OUT_SCOPE = """["${'$'}forms", "current", "data", "children", "opt_out", "value"]"""
        val REFERRAL_SCOPE = """["${'$'}forms", "current", "data", "children", "referral", "value"]"""
    }
}
