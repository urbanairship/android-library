/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.info

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AssociatedLabelInfoTest {

    @Test
    public fun parsesLabelsAssociation() {
        val info = parseLabel(
            """
            {
                "type": "label",
                "text": "Email",
                "text_appearance": {
                    "color": { "default": { "hex": "#000000", "alpha": 1 } },
                    "alignment": "start",
                    "font_size": 14
                },
                "labels": {
                    "type": "labels",
                    "view_id": "email_field",
                    "view_type": "text_input"
                }
            }
            """
        )

        val labels = requireNotNull(info.labels)
        assertEquals(LabelInfo.AssociatedLabel.Type.LABELS, labels.type)
        assertEquals("email_field", labels.viewId)
        assertEquals(ViewType.TEXT_INPUT, labels.viewType)
    }

    @Test
    public fun parsesDescribesTypeCaseInsensitive() {
        val info = parseLabel(
            """
            {
                "type": "label",
                "text": "Hint",
                "text_appearance": {
                    "color": { "default": { "hex": "#000000", "alpha": 1 } },
                    "alignment": "start",
                    "font_size": 14
                },
                "labels": {
                    "type": "DESCRIBES",
                    "view_id": "x",
                    "view_type": "label"
                }
            }
            """
        )

        assertEquals(LabelInfo.AssociatedLabel.Type.DESCRIBES, requireNotNull(info.labels).type)
    }

    @Test
    public fun omitsLabelsWhenNotInJson() {
        val info = parseLabel(
            """
            {
                "type": "label",
                "text": "Only text",
                "text_appearance": {
                    "color": { "default": { "hex": "#000000", "alpha": 1 } },
                    "alignment": "start",
                    "font_size": 14
                }
            }
            """
        )

        assertNull(info.labels)
    }

    @Test(expected = JsonException::class)
    public fun unknownLabelsTypeThrows() {
        parseLabel(
            """
            {
                "type": "label",
                "text": "x",
                "text_appearance": {
                    "color": { "default": { "hex": "#000000", "alpha": 1 } },
                    "alignment": "start",
                    "font_size": 14
                },
                "labels": {
                    "type": "unknown_kind",
                    "view_id": "a",
                    "view_type": "label"
                }
            }
            """
        )
    }

    private fun parseLabel(json: String): LabelInfo =
        ViewInfo.viewInfoFromJson(JsonValue.parseString(json.trimIndent()).requireMap()) as LabelInfo
}
