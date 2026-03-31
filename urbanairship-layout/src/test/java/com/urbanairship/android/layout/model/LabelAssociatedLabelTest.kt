/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.urbanairship.Airship
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestRequestSession
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.android.layout.ThomasModelFactory
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.android.layout.util.ThomasViewIdResolver
import com.urbanairship.android.layout.view.AsyncLayoutView
import com.urbanairship.android.layout.view.LabelView
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class LabelAssociatedLabelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val viewIdResolver = ThomasViewIdResolver()

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { modelScope } returns testScope
        every { viewIdResolver } returns this@LabelAssociatedLabelTest.viewIdResolver
        every { layoutState } returns mockk(relaxed = true)
    }

    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun labelsType_setsLabelForToAssociatedViewId() {
        val targetIdentifier = "email_input"
        val info = parseLabelJson(
            """
            {
                "type": "label",
                "text": "Email",
                ${requiredTextAppearanceJson()},
                "labels": {
                    "type": "labels",
                    "view_id": "$targetIdentifier",
                    "view_type": "text_input"
                }
            }
            """
        )

        val expectedTargetId = viewIdResolver.viewId(targetIdentifier, ViewType.TEXT_INPUT)
        val model = LabelModel(info, mockEnv, ModelProperties(pagerPageId = null))
        val context = RuntimeEnvironment.getApplication()

        val view = model.createView(context, viewEnvironment, null) as LabelView

        assertEquals(expectedTargetId, view.labelFor)
    }

    @Test
    public fun describesType_doesNotSetLabelFor() {
        val info = parseLabelJson(
            """
            {
                "type": "label",
                "text": "Caption",
                ${requiredTextAppearanceJson()},
                "labels": {
                    "type": "describes",
                    "view_id": "some_id",
                    "view_type": "text_input"
                }
            }
            """
        )

        val model = LabelModel(info, mockEnv, ModelProperties(pagerPageId = null))
        val view = model.createView(RuntimeEnvironment.getApplication(), viewEnvironment, null) as LabelView

        assertEquals(View.NO_ID, view.labelFor)
    }

    /**
     * Root layout: label (AssociatedLabel -> text_input in async payload) + async_view_controller.
     * After HTTP loads a form_controller wrapping that text_input, [LabelView.labelFor] must match
     * the nested field's edit-text id ([ThomasViewIdResolver] is shared across the tree).
     */
    @Test
    public fun labelForNestedAsyncTextInput_resolvesAfterAsyncLoad(): TestResult = runTest(testDispatcher) {
        val inputIdentifier = "async_nested_email"
        val session = TestRequestSession()
        session.addResponse(
            200,
            body = nestedFormWithTextInputJson(inputIdentifier)
        )

        mockkObject(Airship)
        try {
            every { Airship.runtimeConfig } returns TestAirshipRuntimeConfig(session = session)
            every { Airship.isFlyingOrTakingOff } returns true
            every { Airship.inputValidator } returns mockk<AirshipInputValidation.Validator>(relaxed = true)

            val viewModel = LayoutViewModel()
            val modelEnv = viewModel.getOrCreateEnvironment(
                reporter = mockk(relaxUnitFun = true),
                displayTimer = mockk { every { time } returns System.currentTimeMillis() },
                actionRunner = mockk(relaxUnitFun = true),
                layoutState = LayoutState.EMPTY
            )

            val rootJson = rootLinearWithLabelAndAsyncJson(
                inputIdentifier = inputIdentifier,
                asyncUrl = "https://example.com/nested-layout.json"
            )
            val rootInfo = ViewInfo.viewInfoFromJson(
                JsonValue.parseString(rootJson).requireMap()
            )

            val rootModel = viewModel.getOrCreateModel(
                viewInfo = rootInfo,
                modelEnvironment = modelEnv,
                factory = ThomasModelFactory()
            ) as LinearLayoutModel

            val context = RuntimeEnvironment.getApplication()
            val viewEnv = mockk<ViewEnvironment>(relaxed = true)
            val rootView = rootModel.createView(context, viewEnv, null)

            val labelView = rootView.getChildAt(0) as LabelView
            val asyncLayoutView = rootView.getChildAt(1) as AsyncLayoutView

            val expectedEditId = modelEnv.viewIdResolver.viewId(
                inputIdentifier,
                ViewType.TEXT_INPUT
            )
            assertEquals(expectedEditId, labelView.labelFor)

            advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(asyncLayoutView.childCount >= 1)
            val loadedRoot = asyncLayoutView.getChildAt(0)
            val labeledField = loadedRoot.findViewById<View>(labelView.labelFor)
            assertNotNull(labeledField)
            assertTrue(labeledField is EditText)
            assertEquals(expectedEditId, labelView.labelFor)
        } finally {
            unmockkObject(Airship)
        }
    }

    /**
     * Root [FormController] with a sync [text_input] and [AsyncLayoutView]; async payload is a [LabelView]
     * whose AssociatedLabel targets the sibling field. [ThomasViewIdResolver] is shared, so after load
     * [LabelView.labelFor] must resolve to the parent layout’s [EditText].
     */
    @Test
    public fun labelForSyncTextInput_resolvesWhenLabelLoadsInAsyncView(): TestResult = runTest(testDispatcher) {
        val inputIdentifier = "sync_parent_email"
        val session = TestRequestSession()
        session.addResponse(
            200,
            body = asyncPayloadLabelForSyncFieldJson(inputIdentifier)
        )

        mockkObject(Airship)
        try {
            every { Airship.runtimeConfig } returns TestAirshipRuntimeConfig(session = session)
            every { Airship.isFlyingOrTakingOff } returns true
            every { Airship.inputValidator } returns mockk<AirshipInputValidation.Validator>(relaxed = true)

            val viewModel = LayoutViewModel()
            val modelEnv = viewModel.getOrCreateEnvironment(
                reporter = mockk(relaxUnitFun = true),
                displayTimer = mockk { every { time } returns System.currentTimeMillis() },
                actionRunner = mockk(relaxUnitFun = true),
                layoutState = LayoutState.EMPTY
            )

            val rootJson = rootFormWithSyncTextInputAndAsyncJson(
                inputIdentifier = inputIdentifier,
                asyncUrl = "https://example.com/async-label.json"
            )
            val rootInfo = ViewInfo.viewInfoFromJson(
                JsonValue.parseString(rootJson).requireMap()
            )

            val rootModel = viewModel.getOrCreateModel(
                viewInfo = rootInfo,
                modelEnvironment = modelEnv,
                factory = ThomasModelFactory()
            ) as FormController

            val context = RuntimeEnvironment.getApplication()
            val viewEnv = mockk<ViewEnvironment>(relaxed = true)
            val rootView = rootModel.createView(context, viewEnv, null) as ViewGroup

            val asyncLayoutView = rootView.getChildAt(1) as AsyncLayoutView

            val expectedEditId = modelEnv.viewIdResolver.viewId(
                inputIdentifier,
                ViewType.TEXT_INPUT
            )
            val syncFieldBeforeAsync = rootView.findViewById<View>(expectedEditId)
            assertNotNull(syncFieldBeforeAsync)
            assertTrue(syncFieldBeforeAsync is EditText)

            advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(asyncLayoutView.childCount >= 1)
            val loadedAsyncRoot = asyncLayoutView.getChildAt(0) as ViewGroup
            val labelView = loadedAsyncRoot.getChildAt(0) as LabelView

            assertEquals(expectedEditId, labelView.labelFor)

            val labeledField = rootView.findViewById<View>(labelView.labelFor)
            assertNotNull(labeledField)
            assertTrue(labeledField is EditText)
        } finally {
            unmockkObject(Airship)
        }
    }

    /**
     * Async payload is a [linear_layout] with a [LabelView] and [text_input] as siblings.
     * [ThomasViewIdResolver] must assign consistent ids in the async subtree so
     * [LabelView.labelFor] resolves to the co-loaded field after HTTP completes.
     */
    @Test
    public fun labelForTextInputBothInsideAsyncPayload_resolvesAfterAsyncLoad(): TestResult = runTest(testDispatcher) {
        val inputIdentifier = "async_inner_email"
        val session = TestRequestSession()
        session.addResponse(
            200,
            body = asyncPayloadLabelAndTextInputSiblingsJson(inputIdentifier)
        )

        mockkObject(Airship)
        try {
            every { Airship.runtimeConfig } returns TestAirshipRuntimeConfig(session = session)
            every { Airship.isFlyingOrTakingOff } returns true
            every { Airship.inputValidator } returns mockk<AirshipInputValidation.Validator>(relaxed = true)

            val viewModel = LayoutViewModel()
            val modelEnv = viewModel.getOrCreateEnvironment(
                reporter = mockk(relaxUnitFun = true),
                displayTimer = mockk { every { time } returns System.currentTimeMillis() },
                actionRunner = mockk(relaxUnitFun = true),
                layoutState = LayoutState.EMPTY
            )

            val rootJson = rootLinearWithOnlyAsyncJson(
                asyncUrl = "https://example.com/async-inner-label.json"
            )
            val rootInfo = ViewInfo.viewInfoFromJson(
                JsonValue.parseString(rootJson).requireMap()
            )

            val rootModel = viewModel.getOrCreateModel(
                viewInfo = rootInfo,
                modelEnvironment = modelEnv,
                factory = ThomasModelFactory()
            ) as LinearLayoutModel

            val context = RuntimeEnvironment.getApplication()
            val viewEnv = mockk<ViewEnvironment>(relaxed = true)
            val rootView = rootModel.createView(context, viewEnv, null)

            val asyncLayoutView = rootView.getChildAt(0) as AsyncLayoutView

            val expectedEditId = modelEnv.viewIdResolver.viewId(
                inputIdentifier,
                ViewType.TEXT_INPUT
            )

            advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(asyncLayoutView.childCount >= 1)
            val loadedRoot = asyncLayoutView.getChildAt(0) as ViewGroup
            val labelView = loadedRoot.getChildAt(0) as LabelView

            assertEquals(expectedEditId, labelView.labelFor)
            val labeledField = loadedRoot.findViewById<View>(labelView.labelFor)
            assertNotNull(labeledField)
            assertTrue(labeledField is EditText)
        } finally {
            unmockkObject(Airship)
        }
    }

    private fun parseLabelJson(json: String): LabelInfo =
        ViewInfo.viewInfoFromJson(JsonValue.parseString(json.trimIndent()).requireMap()) as LabelInfo

    /** Label and text_input JSON require a `text_appearance` key; only `color` is required inside that object. */
    private fun requiredTextAppearanceJson(): String = """
        "text_appearance": {
            "color": { "default": { "hex": "#000000", "alpha": 1 } }
        }
    """.trimIndent()

    private fun rootLinearWithLabelAndAsyncJson(
        inputIdentifier: String,
        asyncUrl: String
    ): String = """
        {
            "type": "linear_layout",
            "direction": "vertical",
            "items": [
                {
                    "size": { "width": "100%", "height": "auto" },
                    "view": {
                        "type": "label",
                        "text": "Email",
                        ${requiredTextAppearanceJson()},
                        "labels": {
                            "type": "labels",
                            "view_id": "$inputIdentifier",
                            "view_type": "text_input"
                        }
                    }
                },
                {
                    "size": { "width": "100%", "height": "auto" },
                    "view": {
                        "type": "async_view_controller",
                        "identifier": "async_block",
                        "placeholder": { "type": "empty_view" },
                        "request": {
                            "type": "content",
                            "url": "$asyncUrl"
                        },
                        "retry": { "max_retries": 0 }
                    }
                }
            ]
        }
    """.trimIndent()

    private fun nestedFormWithTextInputJson(inputIdentifier: String): String = """
        {
            "type": "form_controller",
            "identifier": "nested_form",
            "response_type": "test",
            "submit": "submit_event",
            "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [
                    {
                        "size": { "width": "100%", "height": "auto" },
                        "view": {
                            "type": "text_input",
                            "identifier": "$inputIdentifier",
                            "input_type": "text",
                            ${requiredTextAppearanceJson()}
                        }
                    }
                ]
            }
        }
    """.trimIndent()

    private fun rootFormWithSyncTextInputAndAsyncJson(
        inputIdentifier: String,
        asyncUrl: String
    ): String = """
        {
            "type": "form_controller",
            "identifier": "root_form",
            "response_type": "test",
            "submit": "submit_event",
            "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [
                    {
                        "size": { "width": "100%", "height": "auto" },
                        "view": {
                            "type": "text_input",
                            "identifier": "$inputIdentifier",
                            "input_type": "text",
                            ${requiredTextAppearanceJson()}
                        }
                    },
                    {
                        "size": { "width": "100%", "height": "auto" },
                        "view": {
                            "type": "async_view_controller",
                            "identifier": "async_label_block",
                            "placeholder": { "type": "empty_view" },
                            "request": {
                                "type": "content",
                                "url": "$asyncUrl"
                            },
                            "retry": { "max_retries": 0 }
                        }
                    }
                ]
            }
        }
    """.trimIndent()

    private fun asyncPayloadLabelForSyncFieldJson(inputIdentifier: String): String = """
        {
            "type": "linear_layout",
            "direction": "vertical",
            "items": [
                {
                    "size": { "width": "100%", "height": "auto" },
                    "view": {
                        "type": "label",
                        "text": "Email",
                        ${requiredTextAppearanceJson()},
                        "labels": {
                            "type": "labels",
                            "view_id": "$inputIdentifier",
                            "view_type": "text_input"
                        }
                    }
                }
            ]
        }
    """.trimIndent()

    private fun rootLinearWithOnlyAsyncJson(asyncUrl: String): String = """
        {
            "type": "linear_layout",
            "direction": "vertical",
            "items": [
                {
                    "size": { "width": "100%", "height": "auto" },
                    "view": {
                        "type": "async_view_controller",
                        "identifier": "async_inner",
                        "placeholder": { "type": "empty_view" },
                        "request": {
                            "type": "content",
                            "url": "$asyncUrl"
                        },
                        "retry": { "max_retries": 0 }
                    }
                }
            ]
        }
    """.trimIndent()

    private fun asyncPayloadLabelAndTextInputSiblingsJson(inputIdentifier: String): String = """
        {
            "type": "form_controller",
            "identifier": "async_inner_form",
            "response_type": "test",
            "submit": "submit_event",
            "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [
                    {
                        "size": { "width": "100%", "height": "auto" },
                        "view": {
                            "type": "label",
                            "text": "Email",
                            ${requiredTextAppearanceJson()},
                            "labels": {
                                "type": "labels",
                                "view_id": "$inputIdentifier",
                                "view_type": "text_input"
                            }
                        }
                    },
                    {
                        "size": { "width": "100%", "height": "auto" },
                        "view": {
                            "type": "text_input",
                            "identifier": "$inputIdentifier",
                            "input_type": "text",
                            ${requiredTextAppearanceJson()}
                        }
                    }
                ]
            }
        }
    """.trimIndent()
}
