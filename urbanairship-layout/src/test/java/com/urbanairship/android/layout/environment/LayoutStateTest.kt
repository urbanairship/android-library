
package com.urbanairship.android.layout.environment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.PagerControllerBranching
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LayoutStateTest{
    @Test
    public fun testPagerStateJsonSerialization() {
        val pagerState = State.Pager(
            identifier = "pagerId",
            pageIndex = 1,
            lastPageIndex = 0,
            completed = true,
            pageIds = listOf("page1", "page2"),
            durations = listOf(100, 200),
            progress = 50,
            isMediaPaused = true,
            wasMediaPaused = false,
            isStoryPaused = true,
            isTouchExplorationEnabled = false,
            branching = PagerControllerBranching(listOf()),
            isScrollDisabled = true
        )

        val json = pagerState.toJsonValue().toString()
        val fromJson = State.Pager.fromJson(JsonValue.parseString(json))

        assertEquals(pagerState, fromJson)
    }

    @Test
    public fun testCheckboxStateJsonSerialization() {
        val checkboxState = State.Checkbox(
            identifier = "checkboxId",
            minSelection = 1,
            maxSelection = 2,
            selectedItems = setOf(
                State.Checkbox.Selected("item1", JsonValue.wrap("value1")),
                State.Checkbox.Selected("item2", JsonValue.wrap("value2"))
            ),
            isEnabled = true
        )

        val json = checkboxState.toJsonValue().toString()
        val fromJson = State.Checkbox.fromJson(JsonValue.parseString(json))

        assertEquals(checkboxState, fromJson)
    }

    @Test
    public fun testRadioStateJsonSerialization() {
        val radioState = State.Radio(
            identifier = "radioId",
            selectedItem = State.Radio.Selected(
                identifier = "itemId",
                reportingValue = JsonValue.wrap("value"),
                attributeValue = JsonValue.parseString("{\"key\": \"value\"}")
            ),
            isEnabled = true
        )

        val json = radioState.toJsonValue().toString()
        val fromJson = State.Radio.fromJson(JsonValue.parseString(json))

        assertEquals(radioState, fromJson)
    }

    @Test
    fun testScoreStateJsonSerialization() {
        val scoreState = State.Score(
            identifier = "scoreId",
            selectedItem = State.Score.Selected(
                identifier = "itemId",
                reportingValue = JsonValue.wrap("value"),
                attributeValue = JsonValue.parseString("{\"key\": \"value\"}")
            ),
            isEnabled = true
        )

        val json = scoreState.toJsonValue().toString()
        val fromJson = State.Score.fromJson(JsonValue.parseString(json))

        assertEquals(scoreState, fromJson)
    }

    @Test
    fun testLayoutStateJsonSerialization() {
        val layoutState = State.Layout(
            identifier = "test",
            mutations = mapOf(
                "key1" to LayoutState.StateMutation("id1", "key1", JsonValue.wrap("value1")),
                "key2" to LayoutState.StateMutation("id2", "key2", JsonValue.wrap("value2"))
            )
        )

        val json = layoutState.toJsonValue().toString()
        val fromJson = State.Layout.fromJson(JsonValue.parseString(json))

        assertEquals(layoutState, fromJson)
    }

    @Test
    fun testFormJsonSerialization() {
        val formState = State.Form(
            identifier = "formId",
            formType = FormType.Form,
            formResponseType = "responseType",
            validationMode = FormValidationMode.IMMEDIATE,
            status = ThomasFormStatus.VALID,
            displayedInputs = setOf("input1", "input2"),
            isVisible = true,
            isEnabled = true,
            isDisplayReported = true,
            children = emptyMap(),
            initialChildrenValues = emptyMap()
        )

        val json = formState.toJsonValue().toString()
        val fromJson = State.Form.fromJson(JsonValue.parseString(json))

        assertEquals(formState, fromJson)
    }
}
