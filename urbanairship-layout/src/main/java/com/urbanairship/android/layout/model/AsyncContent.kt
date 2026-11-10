/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.property.NamedColors
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap

/** The view an async view's request resolves to, and the named colors in scope within it. */
internal class AsyncContent(val info: ViewInfo, val namedColors: NamedColors) {

    internal companion object {

        /**
         * Parses an async view's response, which is either a view or a wrapper of the form
         * `{ "type": "async_response", "view": <view>, "references": { "colors": { ... } } }` with
         * `references` optional.
         *
         * Any other `type` is a view.
         * The wrapper's named colors are looked up before [sceneColors]. A bare view's own
         * `references` key isn't read.
         *
         * @param json the response body.
         * @param sceneColors the named colors in scope where the async view sits.
         * @return the content to display and its named colors.
         * @throws JsonException if the view doesn't parse, or `references` or its `colors` isn't
         * an object.
         */
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap, sceneColors: NamedColors): AsyncContent {
            if (!isWrapper(json)) {
                return AsyncContent(ViewInfo.viewInfoFromJson(json), sceneColors)
            }

            val view = json.require("view").requireMap()
            requireWellFormedReferences(json)
            return AsyncContent(ViewInfo.viewInfoFromJson(view), sceneColors.scopedTo(json))
        }

        /** The `type` that marks the response as a wrapper rather than a view. */
        private const val WRAPPER_TYPE = "async_response"

        private fun isWrapper(json: JsonMap): Boolean =
            json.opt("type").optString() == WRAPPER_TYPE

        /** [NamedColors.scopedTo] skips a `references` that isn't an object; here it's an error. */
        @Throws(JsonException::class)
        private fun requireWellFormedReferences(wrapper: JsonMap) {
            val references = wrapper.opt("references")
            if (references.isNull) return
            val colors = references.requireMap().opt("colors")
            if (colors.isNull) return
            colors.requireMap()
        }
    }
}
