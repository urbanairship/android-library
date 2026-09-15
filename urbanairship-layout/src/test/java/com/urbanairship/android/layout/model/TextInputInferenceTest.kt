/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.ai.ThomasAIInference
import com.urbanairship.android.layout.ai.ThomasAIInferenceRequest
import com.urbanairship.android.layout.ai.ThomasAIStatus
import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.view.TextInputView
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class TextInputInferenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val inference = FakeInference()

    private val formState = SharedState(
        State.Form(
            identifier = "form-id",
            formType = FormType.Form,
            formResponseType = "form",
            validationMode = FormValidationMode.ON_DEMAND,
            initialChildrenValues = emptyMap()
        )
    )

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { modelScope } returns testScope
        every { layoutState } returns LayoutState.EMPTY
        every { aiInference } returns inference
    }

    private val textFlow = MutableStateFlow("")

    private val mockView: TextInputView = mockk(relaxed = true) {
        every { textChanges() } returns textFlow
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testCompletedInferenceIsProjectedAndReported(): TestResult = runTest(testDispatcher) {
        inference.response = JsonValue.parseString("""{ "topic": "shipping", "note": "private" }""")
        val model = model(withInference = true)

        type(model, "the parcel never turned up")

        assertEquals(1, inference.requests.size)
        assertEquals("the parcel never turned up", inference.requests.single().text)

        // Predicates see the whole output...
        assertEquals(
            JsonValue.parseString(
                """{ "status": "complete", "result": { "topic": "shipping", "note": "private" } }"""
            ),
            field().formData(withState = true).require("status").requireMap().get("ai")
        )
        // ...while the event reports only what the schema opted in.
        assertEquals(
            JsonValue.parseString("""{ "result": "success", "output": { "topic": "shipping" } }"""),
            field().formData(withState = false).get("ai_inference")
        )
    }

    @Test
    public fun testFieldStaysValidWhenTheModelHasNoAnswer(): TestResult = runTest(testDispatcher) {
        inference.response = null
        val model = model(withInference = true)

        type(model, "hello")

        assertTrue(field().status.isValid)
        assertEquals(
            JsonValue.parseString("""{ "result": "failed" }"""),
            field().formData(withState = false).get("ai_inference")
        )
    }

    @Test
    public fun testUnavailableModelResolvesWithoutAskingIt(): TestResult = runTest(testDispatcher) {
        inference.available = false
        val model = model(withInference = true)

        type(model, "hello")

        assertTrue(inference.requests.isEmpty())
        assertTrue(field().status.isValid)
        assertEquals(
            JsonValue.parseString("""{ "result": "failed" }"""),
            field().formData(withState = false).get("ai_inference")
        )
    }

    @Test
    public fun testInputWithoutAnInferencePayloadNeverAsks(): TestResult = runTest(testDispatcher) {
        val model = model(withInference = false)

        type(model, "hello")

        assertTrue(inference.requests.isEmpty())
        assertTrue(field().status.isValid)
        assertNull(field().formData(withState = false).get("ai_inference"))
    }

    @Test
    public fun testUnchangedTextReusesTheAnswer(): TestResult = runTest(testDispatcher) {
        inference.response = JsonValue.parseString("""{ "topic": "shipping" }""")
        val model = model(withInference = true)

        type(model, "late")
        type(model, "late ")

        assertEquals(1, inference.requests.size)
        assertEquals(
            JsonValue.parseString("""{ "result": "success", "output": { "topic": "shipping" } }"""),
            field().formData(withState = false).get("ai_inference")
        )
    }

    @Test
    public fun testFailedAnswerIsNotMemoized(): TestResult = runTest(testDispatcher) {
        // The memo holds one entry, so only text that resolves to the same string as the
        // previous evaluation reaches it — the mirror of testUnchangedTextReusesTheAnswer.
        // A failure parked there is never retried: the memo short-circuits before the
        // fetcher, and the fetcher's retry backoff never applies because a failure resolves
        // as valid rather than as an error.
        val model = model(withInference = true)

        inference.response = null
        type(model, "late")
        assertEquals(
            JsonValue.parseString("""{ "result": "failed" }"""),
            field().formData(withState = false).get("ai_inference")
        )

        inference.response = JsonValue.parseString("""{ "topic": "shipping" }""")
        type(model, "late ")

        assertEquals(
            JsonValue.parseString("""{ "result": "success", "output": { "topic": "shipping" } }"""),
            field().formData(withState = false).get("ai_inference")
        )
    }

    private fun TestScope.type(model: TextInputModel, value: String) {
        model.onViewAttached(mockView)
        textFlow.value = value
        advanceUntilIdle()
    }

    private fun field(): ThomasFormField<*> =
        formState.changes.value.filteredFields.getValue(IDENTIFIER)

    private fun model(withInference: Boolean): TextInputModel = TextInputModel(
        viewInfo = TextInputInfo(JsonValue.parseString(inputJson(withInference)).requireMap()),
        formState = ThomasForm(formState),
        environment = mockEnv,
        properties = ModelProperties(pagerPageId = null)
    )

    private fun inputJson(withInference: Boolean): String = """
        {
            "type": "text_input",
            "identifier": "$IDENTIFIER",
            "input_type": "text_multiline",
            "text_appearance": {
                "color": { "default": { "hex": "#000000", "alpha": 1 } }
            }
            ${if (withInference) ", ${'"'}ai_inference${'"'}: $INFERENCE_JSON" else ""}
        }
    """.trimIndent()

    /** Records what it was asked and answers with whatever the test set. */
    private class FakeInference : ThomasAIInference {
        var available: Boolean = true
        var response: JsonValue? = null
        val requests: MutableList<ThomasAIInferenceRequest> = mutableListOf()

        override val isAvailable: Boolean
            get() = available

        override val statusUpdates = flowOf(ThomasAIStatus(textInputInference = true))

        override suspend fun run(request: ThomasAIInferenceRequest): JsonValue? {
            requests.add(request)
            return response
        }
    }

    private companion object {
        const val IDENTIFIER = "feedback"

        /** `topic` is reportable; `note` stays on the device. */
        const val INFERENCE_JSON = """
            {
                "prompt": "Classify the feedback",
                "output_schema": {
                    "type": "object",
                    "x-ua-report-property": true,
                    "properties": {
                        "topic": { "type": "string", "x-ua-report-property": true },
                        "note": { "type": "string" }
                    }
                }
            }
        """
    }
}
