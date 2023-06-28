package com.urbanairship.android.layout.info

import android.widget.ImageView.ScaleType
import com.urbanairship.android.layout.info.ItemInfo.ViewItemInfo
import com.urbanairship.android.layout.info.ViewInfo.Companion.viewInfoFromJson
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.AutomatedAction
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.Margin
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.PagerGesture
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.ScoreStyle
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.StoryIndicatorSource
import com.urbanairship.android.layout.property.StoryIndicatorStyle
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.property.TextInputTextAppearance
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.Video
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.ViewType.CHECKBOX
import com.urbanairship.android.layout.property.ViewType.CHECKBOX_CONTROLLER
import com.urbanairship.android.layout.property.ViewType.CONTAINER
import com.urbanairship.android.layout.property.ViewType.EMPTY_VIEW
import com.urbanairship.android.layout.property.ViewType.FORM_CONTROLLER
import com.urbanairship.android.layout.property.ViewType.IMAGE_BUTTON
import com.urbanairship.android.layout.property.ViewType.LABEL
import com.urbanairship.android.layout.property.ViewType.LABEL_BUTTON
import com.urbanairship.android.layout.property.ViewType.LINEAR_LAYOUT
import com.urbanairship.android.layout.property.ViewType.MEDIA
import com.urbanairship.android.layout.property.ViewType.NPS_FORM_CONTROLLER
import com.urbanairship.android.layout.property.ViewType.PAGER
import com.urbanairship.android.layout.property.ViewType.PAGER_CONTROLLER
import com.urbanairship.android.layout.property.ViewType.PAGER_INDICATOR
import com.urbanairship.android.layout.property.ViewType.RADIO_INPUT
import com.urbanairship.android.layout.property.ViewType.RADIO_INPUT_CONTROLLER
import com.urbanairship.android.layout.property.ViewType.SCORE
import com.urbanairship.android.layout.property.ViewType.SCROLL_LAYOUT
import com.urbanairship.android.layout.property.ViewType.STATE_CONTROLLER
import com.urbanairship.android.layout.property.ViewType.STORY_INDICATOR
import com.urbanairship.android.layout.property.ViewType.TEXT_INPUT
import com.urbanairship.android.layout.property.ViewType.TOGGLE
import com.urbanairship.android.layout.property.ViewType.UNKNOWN
import com.urbanairship.android.layout.property.ViewType.WEB_VIEW
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.AttributeName.attributeNameFromJson
import com.urbanairship.android.layout.shape.Shape
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

public sealed class ViewInfo : View {

    public companion object {
        @JvmStatic
        @Throws(JsonException::class)
        public fun viewInfoFromJson(json: JsonMap): ViewInfo {
            return when (val type = ViewType.from(json.requireField<String>("type"))) {
                CONTAINER -> ContainerLayoutInfo(json)
                LINEAR_LAYOUT -> LinearLayoutInfo(json)
                SCROLL_LAYOUT -> ScrollLayoutInfo(json)
                EMPTY_VIEW -> EmptyInfo(json)
                WEB_VIEW -> WebViewInfo(json)
                MEDIA -> MediaInfo(json)
                LABEL -> LabelInfo(json)
                LABEL_BUTTON -> LabelButtonInfo(json)
                IMAGE_BUTTON -> ImageButtonInfo(json)
                PAGER_CONTROLLER -> PagerControllerInfo(json)
                PAGER -> PagerInfo(json)
                PAGER_INDICATOR -> PagerIndicatorInfo(json)
                STORY_INDICATOR -> StoryIndicatorInfo(json)
                FORM_CONTROLLER -> FormControllerInfo(json)
                NPS_FORM_CONTROLLER -> NpsFormControllerInfo(json)
                CHECKBOX_CONTROLLER -> CheckboxControllerInfo(json)
                CHECKBOX -> CheckboxInfo(json)
                TOGGLE -> ToggleInfo(json)
                RADIO_INPUT_CONTROLLER -> RadioInputControllerInfo(json)
                RADIO_INPUT -> RadioInputInfo(json)
                TEXT_INPUT -> TextInputInfo(json)
                SCORE -> ScoreInfo(json)
                STATE_CONTROLLER -> StateControllerInfo(json)
                UNKNOWN -> throw JsonException("Unknown view type! '$type'")
            }
        }
    }
}

internal sealed class ViewGroupInfo<C : ItemInfo> : ViewInfo() {
    abstract val children: List<C>
}

internal data class VisibilityInfo(
    val invertWhenStateMatcher: JsonPredicate,
    val default: Boolean
) {
    constructor(json: JsonMap) : this(
        invertWhenStateMatcher = JsonPredicate.parse(
            json.requireField("invert_when_state_matches")),
        default = json.requireField("default")
    )
}

internal sealed class ItemInfo(
    val info: ViewInfo
) {
    val type = info.type
    class ViewItemInfo(info: ViewInfo) : ItemInfo(info)
}

// ------ Base Interfaces ------

internal interface View {
    val type: ViewType
    val backgroundColor: Color?
    val border: Border?

    val visibility: VisibilityInfo?
    val eventHandlers: List<EventHandler>?
    val enableBehaviors: List<EnableBehaviorType>?
}

internal class BaseViewInfo(json: JsonMap) : View {
    override val type = ViewType.from(json.requireField<String>("type"))
    override val backgroundColor =
        json.optionalField<JsonMap>("background_color")?.let { Color.fromJson(it) }
    override val border =
        json.optionalField<JsonMap>("border")?.let { Border.fromJson(it) }
    override val visibility = json.optionalField<JsonMap>("visibility")?.let { VisibilityInfo(it) }
    override val eventHandlers: List<EventHandler>? =
        json.optionalField<JsonList>("event_handlers")?.let { list ->
            list.map { EventHandler(it.requireMap()) }
        }
    override val enableBehaviors =
        json.optionalField<JsonList>("enabled")?.let { list ->
            list.map { EnableBehaviorType.from(it.requireString()) }
        }
}

private fun view(json: JsonMap): View = BaseViewInfo(json)

internal interface Accessible {
    val contentDescription: String?
}

internal data class AccessibleInfo(override val contentDescription: String?) : Accessible

private fun accessible(json: JsonMap): Accessible =
    AccessibleInfo(contentDescription = json.optionalField("content_description"))

internal interface Identifiable {
    val identifier: String
}

internal class IdentifiableInfo(override val identifier: String) : Identifiable

private fun identifiable(json: JsonMap): Identifiable =
    IdentifiableInfo(identifier = json.requireField("identifier"))

internal interface SafeAreaAware {
    val ignoreSafeArea: Boolean
}

internal class SafeAreaAwareInfo(override val ignoreSafeArea: Boolean) : SafeAreaAware

private fun safeAreaAware(json: JsonMap): SafeAreaAware =
    SafeAreaAwareInfo(ignoreSafeArea = json.optionalField("ignore_safe_area") ?: false)

internal interface Validatable {
    val isRequired: Boolean
}

internal data class ValidatableInfo(override val isRequired: Boolean) : Validatable

private fun validatable(json: JsonMap) =
    ValidatableInfo(isRequired = json.optionalField("required") ?: false)

// ------ Base Component Interfaces ------

internal interface Controller : View, Identifiable {
    val view: ViewInfo
}

internal class ControllerInfo(
    json: JsonMap
) : ViewGroupInfo<ViewItemInfo>(), Controller, View by view(json), Identifiable by identifiable(json) {
    override val view: ViewInfo = viewInfoFromJson(json.requireField("view"))
    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))
}

private fun controller(json: JsonMap): Controller = ControllerInfo(json)

internal interface FormController : Controller {
    val responseType: String?
    val submitBehavior: FormBehaviorType?
    val formEnabled: List<EnableBehaviorType>?
}

internal abstract class FormInfo(json: JsonMap) : ViewGroupInfo<ViewItemInfo>(), FormController, Controller by controller(json) {
    override val responseType: String? =
        json.optionalField("response_type")
    override val submitBehavior: FormBehaviorType? =
        json.optionalField<String>("submit")?.let { FormBehaviorType.from(it) }
    override val formEnabled: List<EnableBehaviorType>? =
        json.optionalField<JsonList>("form_enabled")?.map { EnableBehaviorType.from(it.optString()) }
}

internal interface Button : View, Accessible, Identifiable {
    val clickBehaviors: List<ButtonClickBehaviorType>
    val actions: Map<String, JsonValue>?
    val reportingMetadata: JsonValue?
}

internal open class ButtonInfo(
    json: JsonMap
) : ViewInfo(), Button, View by view(json), Accessible by accessible(json), Identifiable by identifiable(json) {
    override val clickBehaviors: List<ButtonClickBehaviorType> =
        json.optionalField<JsonList>("button_click")
            ?.let { ButtonClickBehaviorType.fromList(it) } ?: emptyList()

    override val actions: Map<String, JsonValue>? =
        json.optionalField<JsonMap>("actions")?.map

    override val reportingMetadata: JsonValue? =
        json.optionalField<JsonValue>("reporting_metadata")
}

internal interface Checkable : View, Accessible {
    val style: ToggleStyle
}

internal open class CheckableInfo(
    json: JsonMap
) : ViewInfo(), Checkable, View by view(json), Accessible by accessible(json) {
    override val style: ToggleStyle = ToggleStyle.fromJson(json.requireField("style"))
}

// ------ Components ------

internal class LinearLayoutInfo(json: JsonMap) : ViewGroupInfo<LinearLayoutItemInfo>(), View by view(json) {
    private val randomizeChildren: Boolean = json.optionalField("randomize_children") ?: false
    val direction: Direction = Direction.from(json.requireField("direction"))
    val items = json.requireField<JsonList>("items").map { LinearLayoutItemInfo(it.requireMap()) }
        .let { if (randomizeChildren) it.shuffled() else it }

    override val children: List<LinearLayoutItemInfo> = items
}

internal class LinearLayoutItemInfo(
    val json: JsonMap
) : ItemInfo(viewInfoFromJson(json.requireField("view"))) {
    val size = Size.fromJson(json.requireField("size"))
    val margin = json.optionalField<JsonMap>("margin")?.let { Margin.fromJson(it) }
}

internal class ContainerLayoutInfo(
    json: JsonMap
) : ViewGroupInfo<ContainerLayoutItemInfo>(), View by view(json) {
    val items = json.requireField<JsonList>("items").map { ContainerLayoutItemInfo(it.requireMap()) }

    override val children: List<ContainerLayoutItemInfo> = items
}

internal class ContainerLayoutItemInfo(
    json: JsonMap
) : ItemInfo(viewInfoFromJson(json.requireField("view"))), SafeAreaAware by safeAreaAware(json) {
    val position: Position = Position.fromJson(json.requireField("position"))
    val size = Size.fromJson(json.requireField("size"))
    val margin: Margin? = json.optionalField<JsonMap>("margin")?.let { Margin.fromJson(it) }
}

internal class ScrollLayoutInfo(json: JsonMap) : ViewGroupInfo<ViewItemInfo>(), View by view(json) {
    val view = viewInfoFromJson(json.requireField("view"))
    val direction: Direction = Direction.from(json.requireField("direction"))

    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))
}

internal class EmptyInfo(json: JsonMap) : ViewInfo(), View by view(json)

internal class MediaInfo(
    json: JsonMap
) : ViewInfo(), View by view(json), Accessible by accessible(json) {
    val url: String = json.requireField("url")
    val mediaType: MediaType = MediaType.from(json.requireField("media_type"))
    val scaleType: ScaleType = MediaFit.asScaleType(json.requireField("media_fit"))
    val video: Video? = json.optionalField<JsonMap>("video")?.let { Video.fromJson(it) }
}

internal class LabelInfo(
    json: JsonMap
) : ViewInfo(), View by view(json), Accessible by accessible(json) {
    val text: String = json.requireField("text")
    val textAppearance: TextAppearance =
        TextAppearance.fromJson(json.requireField("text_appearance"))
}

internal class LabelButtonInfo(json: JsonMap) : ButtonInfo(json) {
    val label: LabelInfo = LabelInfo(json.requireField("label"))
}

internal class ImageButtonInfo(json: JsonMap) : ButtonInfo(json) {
    val image: Image = Image.fromJson(json.requireField("image"))
}

internal class CheckboxInfo(json: JsonMap) : CheckableInfo(json) {
    val reportingValue: JsonValue = json.requireField("reporting_value")
}

internal class ToggleInfo(
    json: JsonMap
) : CheckableInfo(json), Identifiable by identifiable(json),
    Validatable by validatable(json) {
    val attributeName: AttributeName? = attributeNameFromJson(json)
    val attributeValue: AttributeValue? = json.optionalField("attribute_value")
}

internal class RadioInputInfo(json: JsonMap) : CheckableInfo(json) {
    val reportingValue: JsonValue = json.requireField("reporting_value")
    val attributeValue: AttributeValue? = json.optionalField("attribute_value")
}

internal class TextInputInfo(
    json: JsonMap
) : ViewInfo(), View by view(json), Identifiable by identifiable(json), Accessible by accessible(json),
    Validatable by validatable(json) {
    val inputType: FormInputType = FormInputType.from(json.requireField("input_type"))
    val hintText: String? = json.optionalField("place_holder")
    val textAppearance: TextInputTextAppearance =
        TextInputTextAppearance.fromJson(json.requireField("text_appearance"))
}

internal class ScoreInfo(
    json: JsonMap
) : ViewInfo(), View by view(json), Identifiable by identifiable(json), Accessible by accessible(json),
    Validatable by validatable(json) {
    val style: ScoreStyle = ScoreStyle.fromJson(json.requireField("style"))
    val attributeName: AttributeName? = attributeNameFromJson(json)
}

internal class WebViewInfo(json: JsonMap) : ViewInfo(), View by view(json) {
    val url: String = json.requireField("url")
}

internal class PagerInfo(json: JsonMap) : ViewGroupInfo<PagerItemInfo>(), View by view(json) {
    val items = json.requireField<JsonList>("items").map { PagerItemInfo(it.requireMap()) }
    val isSwipeDisabled = json.optionalField("disable_swipe") ?: false
    val gestures = json.optionalField<JsonList>("gestures")?.let { PagerGesture.fromList(it) }

    override val children: List<PagerItemInfo> = items
}

internal class PagerItemInfo(
    json: JsonMap
) : ItemInfo(viewInfoFromJson(json.requireField("view"))), Identifiable by identifiable(json) {
    val displayActions: Map<String, JsonValue>? =
        json.optionalField<JsonMap>("display_actions")?.map
    val automatedActions = json.optionalField<JsonList>("automated_actions")
        ?.let { AutomatedAction.fromList(it) }
}

internal class PagerIndicatorInfo(json: JsonMap) : ViewInfo(), View by view(json) {
    val bindings: Bindings = Bindings(json.requireField("bindings"))
    val indicatorSpacing: Int = json.optionalField<Int>("spacing") ?: 4

    internal class Bindings(json: JsonMap) {
        val selected: Binding = Binding(json.requireField("selected"))
        val unselected: Binding = Binding(json.requireField("unselected"))
    }

    internal class Binding(json: JsonMap) {
        val shapes: List<Shape> =
            json.requireField<JsonList>("shapes").map { Shape.fromJson(it.requireMap()) }
        val icon: Image.Icon? =
            json.optionalField<JsonMap>("icon")?.let { Image.Icon.fromJson(it) }
    }
}

internal class StoryIndicatorInfo(json: JsonMap) : ViewInfo(), View by view(json) {
    val source: StoryIndicatorSource = json.requireField<JsonMap>("source")
        .requireField<String>("type")
        .let { StoryIndicatorSource.from(it) }
    val style = StoryIndicatorStyle.from(json.requireField("style"))
}

internal class StateControllerInfo(json: JsonMap) : ViewGroupInfo<ViewItemInfo>(), View by view(json) {
    val view: ViewInfo = viewInfoFromJson(json.requireField("view"))
    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))
}

internal class FormControllerInfo(json: JsonMap) : FormInfo(json) {
    override val view: ViewInfo = viewInfoFromJson(json.requireField("view"))
    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))
}

internal class NpsFormControllerInfo(json: JsonMap) : FormInfo(json) {
    override val view: ViewInfo = viewInfoFromJson(json.requireField("view"))
    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))

    val npsIdentifier: String = json.requireField("nps_identifier")
}

internal class PagerControllerInfo(json: JsonMap) : ViewGroupInfo<ViewItemInfo>(), Controller by controller(json) {
    override val view: ViewInfo = viewInfoFromJson(json.requireField("view"))
    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))
}

internal class CheckboxControllerInfo(
    json: JsonMap
) : ViewGroupInfo<ViewItemInfo>(), Controller by controller(json), Validatable by validatable(json),
    Accessible by accessible(json) {
    val minSelection: Int = json.optionalField<Int>("min_selection") ?: if (isRequired) 1 else 0
    val maxSelection: Int = json.optionalField<Int>("max_selection") ?: Int.MAX_VALUE

    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))
}

internal class RadioInputControllerInfo(
    json: JsonMap
) : ViewGroupInfo<ViewItemInfo>(), Controller by controller(json), Validatable by validatable(json),
    Accessible by accessible(json) {
    val attributeName: AttributeName? = attributeNameFromJson(json)

    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))
}
