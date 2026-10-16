/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.ui

import android.view.View
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.json.JsonMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tells a layout's root view when the environment conditions it was built with have changed.
 *
 * Watches only while [rootView] is attached. [onChange] is posted, since the first reading is
 * published from the root's `onLayout`, where views can't be swapped.
 *
 * @param rootView the layout's root view.
 * @param model the layout's root model.
 * @param onChange called with the new values once they differ from those last applied.
 */
internal class EnvironmentConditionsWatcher(
    private val rootView: View,
    private val model: AnyModel,
    private val onChange: (JsonMap) -> Unit
) : View.OnAttachStateChangeListener {

    /** The values the root last built its views or resolved its placement with. */
    private var applied: JsonMap = model.environmentConditionValues
    private var job: Job? = null

    init {
        rootView.addOnAttachStateChangeListener(this)
        if (rootView.isAttachedToWindow) onViewAttachedToWindow(rootView)
    }

    override fun onViewAttachedToWindow(v: View) {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main.immediate).launch {
            model.environmentConditionChanges.collect { values ->
                if (values == applied) return@collect
                applied = values
                rootView.post { onChange(values) }
            }
        }
    }

    override fun onViewDetachedFromWindow(v: View) {
        job?.cancel()
        job = null
    }
}
