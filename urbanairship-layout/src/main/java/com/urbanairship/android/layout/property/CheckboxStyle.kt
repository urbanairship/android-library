/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.shape.Shape
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalList

public class CheckboxStyle public constructor(
    public val bindings: Bindings
) : ToggleStyle(ToggleType.CHECKBOX) {

    public class Bindings internal constructor(
        public val selected: Binding,
        public val unselected: Binding
    ) {

        public companion object {
            private const val KEY_SELECTED = "selected"
            private const val KEY_UNSELECTED = "unselected"

            @Throws(JsonException::class)
            public fun fromJson(json: JsonValue): Bindings {
                val content = json.requireMap()

                return Bindings(
                    selected = Binding.fromJson(content.opt(KEY_SELECTED)),
                    unselected = Binding.fromJson(content.opt(KEY_UNSELECTED))
                )
            }
        }
    }

    public class Binding public constructor(
        public val shapes: List<Shape>,
        public val icon: Image.Icon?
    ) {

        public companion object {
            private const val KEY_SHAPES = "shapes"
            private const val KEY_ICON = "icon"

            @Throws(JsonException::class)
            public fun fromJson(json: JsonValue): Binding {
                val content = json.requireMap()

                return Binding(
                    shapes = content
                        .optionalList(KEY_SHAPES)
                        ?.map(Shape::fromJson)
                        ?: listOf(),
                    icon = content[KEY_ICON]?.let(Image.Icon::fromJson)
                )
            }
        }
    }

    public companion object {

        private const val KEY_BINDINGS = "bindings"

        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(json: JsonValue): CheckboxStyle {
            val content = json.requireMap()

            return CheckboxStyle(
                bindings = Bindings.fromJson(content.opt(KEY_BINDINGS))
            )
        }
    }
}
