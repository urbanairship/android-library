/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting

import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions
import com.urbanairship.android.layout.info.ThomasFormRegistrationOverrides
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.SmsLocale
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The form result event reports the options each email and SMS input registered its channel with. */
@RunWith(RobolectricTestRunner::class)
public class FormResultRegistrationTest {

    private val locale = SmsLocale("US", "+1", SmsLocale.Registration.OptIn(SmsLocale.Registration.OptInData("12345")), null)

    @Test
    public fun testARegisteredInputReportsItsRegistrationAndACollectedOneDoesNot() {
        val form = form(
            email("email", "user@example.com", ThomasEmailRegistrationOptions.Commercial(properties = null, optedIn = true)),
            email("collected-email", "other@example.com"),
            sms("sms", "15035550123", locale),
            sms("collected-sms", "15035550124", SmsLocale("US", "+1", null, null))
        )

        val children = eventChildren(form)
        assertEquals(jsonMapOf("type" to "commercial", "commercial_opted_in" to true), registration(children, "email"))
        assertNull(registration(children, "collected-email"))
        assertEquals(jsonMapOf("type" to "opt_in", "sender_id" to "12345"), registration(children, "sms"))
        assertNull(registration(children, "collected-sms"))
    }

    @Test
    public fun testReportsTheOptionsAfterTheFormsOverrides() {
        val overrides = ThomasFormRegistrationOverrides.fromJson(
            JsonValue.parseString(
                """{"email": [{"input_id": "email", "when_state_matches": {"value": {"is_present": false}},
                    "registration": {"type": "double_opt_in"}}]}"""
            )
        )
        val form = form(
            email("email", "user@example.com", ThomasEmailRegistrationOptions.Transactional(properties = null)),
            email("other", "other@example.com", ThomasEmailRegistrationOptions.Transactional(properties = null))
        ).withRegistrationOverrides(overrides)

        val children = eventChildren(form)
        assertEquals(jsonMapOf("type" to "double_opt_in"), registration(children, "email"))
        assertEquals(jsonMapOf("type" to "transactional"), registration(children, "other"))
    }

    @Test
    public fun testANestedFormsInputReportsItsRegistration() {
        val child = form(email("child-email", "child@example.com", ThomasEmailRegistrationOptions.Transactional(properties = null)))
        val fields = child.filteredFields.values.toSet()
        val parent = form(
            ThomasFormField.Form(
                identifier = "child",
                responseType = null,
                children = fields,
                fieldType = ThomasFormField.FieldType.just(fields)
            )
        )

        val nested = eventChildren(parent).opt("child").optMap().opt("children").optMap()
        assertEquals(jsonMapOf("type" to "transactional"), registration(nested, "child-email"))
    }

    @Test
    public fun testTheStatePayloadNeverReportsIt() {
        val form = form(
            email("email", "user@example.com", ThomasEmailRegistrationOptions.Commercial(properties = null, optedIn = true)),
            sms("sms", "15035550123", locale)
        )
        // As `ThomasState` projects `${'$'}forms`.
        val fields = form.filteredFields.values.toSet()
        val children = ThomasFormField.Form("current", null, fields, ThomasFormField.FieldType.just(emptySet()))
            .formData().opt("children").optMap()
        listOf("email", "sms").forEach { identifier ->
            val child = children.opt(identifier).optMap()
            assertNull(identifier, child.get("registration"))
            assertNull(identifier, child.opt("status").optMap().opt("result").optMap().get("registration"))
        }
    }

    private fun form(vararg fields: ThomasFormField<*>): State.Form = fields.fold(
        State.Form(
            identifier = "form",
            formType = FormType.Form,
            formResponseType = null,
            validationMode = FormValidationMode.IMMEDIATE,
            initialChildrenValues = emptyMap()
        )
    ) { form, field -> form.copyWithFormInput(field) }

    private fun email(identifier: String, address: String, options: ThomasEmailRegistrationOptions? = null) =
        ThomasFormField.TextInput(
            textInput = FormInputType.EMAIL,
            identifier = identifier,
            originalValue = address,
            fieldType = ThomasFormField.FieldType.just(
                address,
                channels = options?.let { listOf(ThomasChannelRegistration.Email(address, it)) }
            )
        )

    /** An SMS input as `TextInputModel` builds it: registered only when [locale] has a registration. */
    private fun sms(identifier: String, number: String, locale: SmsLocale) = ThomasFormField.TextInput(
        textInput = FormInputType.SMS,
        smsLocale = locale,
        identifier = identifier,
        originalValue = number,
        fieldType = ThomasFormField.FieldType.just(
            number,
            channels = locale.registration?.let { listOf(ThomasChannelRegistration.Sms(number, it)) }
        )
    )

    private fun eventChildren(form: State.Form): JsonMap =
        form.formResult().toJsonValue().optMap().opt("forms").optMap().opt("form").optMap().opt("children").optMap()

    private fun registration(children: JsonMap, identifier: String): JsonMap? =
        children.opt(identifier).optMap().get("registration")?.optMap()
}
