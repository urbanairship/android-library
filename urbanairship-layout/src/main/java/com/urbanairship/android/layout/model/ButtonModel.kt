/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.Logger
import com.urbanairship.android.layout.event.ButtonEvent
import com.urbanairship.android.layout.event.Event
import com.urbanairship.android.layout.event.EventType
import com.urbanairship.android.layout.event.FormEvent.ValidationUpdate
import com.urbanairship.android.layout.event.PagerEvent
import com.urbanairship.android.layout.event.PagerEvent.Scroll
import com.urbanairship.android.layout.event.ReportingEvent.ButtonTap
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.ButtonEnableBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

internal abstract class ButtonModel(
    type: ViewType,
    override val identifier: String,
    private val buttonClickBehaviors: List<ButtonClickBehaviorType>,
    val actions: Map<String, JsonValue>,
    private val enableBehaviors: List<ButtonEnableBehaviorType>,
    backgroundColor: Color?,
    border: Border?,
    override val contentDescription: String?
) : BaseModel(type, backgroundColor, border), Accessible, Identifiable {
    abstract fun reportingDescription(): String

    private var viewListener: Listener? = null
    private var isEnabled = true

    interface Listener {
        fun setEnabled(isEnabled: Boolean)
    }

    fun setViewListener(viewListener: Listener?) {
        this.viewListener = viewListener
        viewListener?.setEnabled(isEnabled())
    }

    fun onClick() {
        val layoutData = LayoutData.button(identifier)

        // Report button tap event.
        bubbleEvent(ButtonTap(identifier), layoutData)
        if (actions.isNotEmpty()) {
            bubbleEvent(ButtonEvent.Actions(this), layoutData)
        }
        for (behavior in buttonClickBehaviors) {
            try {
                bubbleEvent(ButtonEvent.fromBehavior(behavior, this), layoutData)
            } catch (e: JsonException) {
                Logger.warn(e, "Skipping button click behavior!")
            }
        }

        // Note: Button dismiss events are reported at the top level when handled.
        // We can't send them directly from here because we need to include
        // the display time, which is tracked by the hosting Activity.
    }

    override fun onEvent(event: Event, layoutData: LayoutData): Boolean {
        return when (event.type) {
            EventType.FORM_VALIDATION -> handleFormSubmitUpdate(event as ValidationUpdate)
            EventType.PAGER_INIT -> {
                val init = event as PagerEvent.Init
                handlePagerScroll(init.hasNext(), init.hasPrevious())
            }
            EventType.PAGER_SCROLL -> {
                val scroll = event as Scroll
                handlePagerScroll(scroll.hasNext(), scroll.hasPrevious())
            }
            else -> super.onEvent(event, layoutData)
        }
    }

    private fun isEnabled(): Boolean {
        return enableBehaviors.isEmpty() || isEnabled
    }

    private fun handleFormSubmitUpdate(update: ValidationUpdate): Boolean {
        if (enableBehaviors.contains(ButtonEnableBehaviorType.FORM_VALIDATION)) {
            isEnabled = update.isValid
            viewListener?.setEnabled(update.isValid)
            return true
        }
        return false
    }

    private fun handlePagerScroll(hasNext: Boolean, hasPrevious: Boolean): Boolean {
        if (enableBehaviors.contains(ButtonEnableBehaviorType.PAGER_NEXT)) {
            isEnabled = hasNext
            viewListener?.setEnabled(hasNext)
        }
        if (enableBehaviors.contains(ButtonEnableBehaviorType.PAGER_PREVIOUS)) {
            isEnabled = hasPrevious
            viewListener?.setEnabled(hasPrevious)
        }
        // Always return false so other views can react to pager scroll events.
        return false
    }

    companion object {
        @JvmStatic
        @Throws(JsonException::class)
        fun buttonClickBehaviorsFromJson(json: JsonMap): List<ButtonClickBehaviorType> {
            val clickBehaviorsList = json.opt("button_click").optList()
            return ButtonClickBehaviorType.fromList(clickBehaviorsList)
        }

        @JvmStatic
        fun actionsFromJson(json: JsonMap): Map<String, JsonValue> =
            json.opt("actions").optMap().map

        @JvmStatic
        @Throws(JsonException::class)
        fun buttonEnableBehaviorsFromJson(json: JsonMap): List<ButtonEnableBehaviorType> {
            val enableBehaviorsList = json.opt("enabled").optList()
            return ButtonEnableBehaviorType.fromList(enableBehaviorsList)
        }
    }
}
