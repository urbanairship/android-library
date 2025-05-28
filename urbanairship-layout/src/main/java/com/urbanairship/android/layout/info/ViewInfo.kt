package com.urbanairship.android.layout.info

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.environment.ThomasStateTrigger
import com.urbanairship.android.layout.info.ItemInfo.ViewItemInfo
import com.urbanairship.android.layout.info.ViewInfo.Companion.viewInfoFromJson
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.AutomatedAction
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.property.DisableSwipeSelector
import com.urbanairship.android.layout.property.EnableBehaviorType
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.FormBehaviorType
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.Margin
import com.urbanairship.android.layout.property.MarkdownOptions
import com.urbanairship.android.layout.property.MediaFit
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.PageBranching
import com.urbanairship.android.layout.property.PagerControllerBranching
import com.urbanairship.android.layout.property.PagerGesture
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.ScoreStyle
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.property.SmsLocale
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.android.layout.property.StoryIndicatorSource
import com.urbanairship.android.layout.property.StoryIndicatorStyle
import com.urbanairship.android.layout.property.TapEffect
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.property.TextInputTextAppearance
import com.urbanairship.android.layout.property.ToggleStyle
import com.urbanairship.android.layout.property.Video
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.android.layout.property.ViewType.BASIC_TOGGLE_LAYOUT
import com.urbanairship.android.layout.property.ViewType.BUTTON_LAYOUT
import com.urbanairship.android.layout.property.ViewType.CHECKBOX
import com.urbanairship.android.layout.property.ViewType.CHECKBOX_CONTROLLER
import com.urbanairship.android.layout.property.ViewType.CHECKBOX_TOGGLE_LAYOUT
import com.urbanairship.android.layout.property.ViewType.CONTAINER
import com.urbanairship.android.layout.property.ViewType.CUSTOM_VIEW
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
import com.urbanairship.android.layout.property.ViewType.RADIO_INPUT_TOGGLE_LAYOUT
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
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.json.requireMap

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class ViewInfo : View {

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
                BUTTON_LAYOUT -> ButtonLayoutInfo(json)
                CUSTOM_VIEW -> CustomViewInfo(json)
                PAGER_CONTROLLER -> PagerControllerInfo(json)
                PAGER -> PagerInfo(json)
                PAGER_INDICATOR -> PagerIndicatorInfo(json)
                STORY_INDICATOR -> StoryIndicatorInfo(json)
                FORM_CONTROLLER -> FormControllerInfo(json)
                NPS_FORM_CONTROLLER -> NpsFormControllerInfo(json)
                CHECKBOX_CONTROLLER -> CheckboxControllerInfo(json)
                CHECKBOX -> CheckboxInfo(json)
                TOGGLE -> ToggleInfo(json)
                BASIC_TOGGLE_LAYOUT -> BasicToggleLayoutInfo(json)
                CHECKBOX_TOGGLE_LAYOUT -> CheckboxToggleLayoutInfo(json)
                RADIO_INPUT_TOGGLE_LAYOUT -> RadioInputToggleLayoutInfo(json)
                RADIO_INPUT_CONTROLLER -> RadioInputControllerInfo(json)
                RADIO_INPUT -> RadioInputInfo(json)
                TEXT_INPUT -> TextInputInfo(json)
                SCORE -> ScoreInfo(json)
                STATE_CONTROLLER -> StateControllerInfo(json)
                UNKNOWN -> throw JsonException("Unknown view type! '${json.requireField<String>("type")}'")
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
    val commonViewOverrides: CommonViewOverrides?
    val stateTriggers: List<ThomasStateTrigger>?
}

internal data class ViewPropertyOverride<T>(
    val whenStateMatcher: JsonPredicate?,
    val value: T?
) {
    constructor(
        json: JsonValue, valueParser: (JsonValue) -> T
    ): this(
        whenStateMatcher = json.requireMap().get("when_state_matches")?.let { JsonPredicate.parse(it) },
        value = json.requireMap().get("value")?.let(valueParser)
    )
}


internal class CommonViewOverrides(json: JsonMap) {
    val backgroundColor = json.optionalList("background_color")?.map {
        ViewPropertyOverride(it) { json -> Color.fromJson(json.requireMap()) }
    }

    val border = json.optionalList("border")?.map {
        ViewPropertyOverride(it) { json -> Border.fromJson(json.requireMap()) }
    }
}

internal class BaseViewInfo(json: JsonMap) : View {
    override val type = ViewType.from(json.requireField<String>("type"))

    override val backgroundColor = json.optionalMap("background_color")?.let {
        Color.fromJson(it)
    }
    override val border = json.optionalMap("border")?.let {
        Border.fromJson(it)
    }

    override val visibility = json.optionalMap("visibility")?.let {
        VisibilityInfo(it)
    }

    override val eventHandlers =  json.optionalList("event_handlers")?.let { list ->
        list.map { EventHandler(it.requireMap()) }
    }

    override val enableBehaviors = json.optionalList("enabled")?.let { list ->
        list.map { EnableBehaviorType.from(it.requireString()) }
    }

    override val commonViewOverrides = json.optionalMap("view_overrides")?.let {
        CommonViewOverrides(it)
    }

    override val stateTriggers = json.optionalList("state_triggers")?.let { list ->
        list.map { ThomasStateTrigger.fromJson(it.requireMap()) }
    }
}

private fun view(json: JsonMap): View = BaseViewInfo(json)

internal interface Accessible {
    val contentDescription: String?
    val localizedContentDescription: LocalizedContentDescription?
    val accessibilityHidden: Boolean?
}

private fun accessible(json: JsonMap): Accessible = object : Accessible {
    override val contentDescription: String? = json.optionalField("content_description")

    override val localizedContentDescription: LocalizedContentDescription? =
        json.optionalMap("localized_content_description")?.let { LocalizedContentDescription(it) }

    override val accessibilityHidden: Boolean? =
        json.optionalField<Boolean>("accessibility_hidden")
}

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

internal data class ValidationAction(val actions: List<StateAction>?) {
    constructor(json: JsonValue): this(
        actions = json.requireMap().optionalList("state_actions")?.map {
            StateAction.fromJson(it.requireMap())
        }
    )
}

internal interface Validatable {
    val isRequired: Boolean
    val onError: ValidationAction?
    val onValid: ValidationAction?
    val onEdit: ValidationAction?
}

internal data class ValidatableInfo(
    override val isRequired: Boolean,
    override val onError: ValidationAction?,
    override val onValid: ValidationAction?,
    override val onEdit: ValidationAction?
) : Validatable

private fun validatable(json: JsonMap) =
    ValidatableInfo(
        isRequired = json.optionalField("required") ?: false,
        onError = json.get("on_error")?.let { ValidationAction(it) },
        onValid = json.get("on_valid")?.let { ValidationAction(it) },
        onEdit = json.get("on_edit")?.let { ValidationAction(it) },
    )

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
    val validationMode: FormValidationMode
}

internal enum class FormValidationMode(private val value: String) {
    ON_DEMAND("on_demand"),
    IMMEDIATE("immediate");

    override fun toString(): String {
        return name.lowercase()
    }

    internal companion object {
        @Throws(JsonException::class)
        fun from(value: String): FormValidationMode {
            for (type in FormValidationMode.entries) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw JsonException("Unknown form validation mode value: $value")
        }
    }
}

internal abstract class FormInfo(json: JsonMap) : ViewGroupInfo<ViewItemInfo>(), FormController, Controller by controller(json) {
    override val responseType: String? =
        json.optionalField("response_type")
    override val submitBehavior: FormBehaviorType? =
        json.optionalField<String>("submit")?.let {
            FormBehaviorType.from(it)
        }
    override val formEnabled: List<EnableBehaviorType>? =
        json.optionalList("form_enabled")?.map { EnableBehaviorType.from(it.optString()) }
    override val validationMode: FormValidationMode =
        json.optionalMap("validation_mode")?.let {
            FormValidationMode.from(it.requireField("type"))
        } ?: FormValidationMode.IMMEDIATE
}

internal interface Button : View, Accessible, Identifiable {
    val clickBehaviors: List<ButtonClickBehaviorType>
    val actions: Map<String, JsonValue>?
    val reportingMetadata: JsonValue?
    val tapEffect: TapEffect
}

internal open class ButtonInfo(
    json: JsonMap
) : ViewInfo(), Button, View by view(json), Accessible by accessible(json), Identifiable by identifiable(json) {
    override val clickBehaviors: List<ButtonClickBehaviorType> =
        json.optionalList("button_click")
            ?.let { ButtonClickBehaviorType.fromList(it) } ?: emptyList()

    override val actions: Map<String, JsonValue>? =
        json.optionalMap("actions")?.map

    override val reportingMetadata: JsonValue? =
        json.optionalField<JsonValue>("reporting_metadata")

    override val tapEffect: TapEffect =
        json.optionalMap("tap_effect").let { TapEffect.fromJson(it) }
}

/** ButtonLayout is a bit special because it's both a ViewGroup and a Button. */
internal class ButtonLayoutInfo(json: JsonMap) : ViewGroupInfo<ViewItemInfo>(), Button by ButtonInfo(json), Accessible by accessible(json) {
    val view = viewInfoFromJson(json.requireField("view"))

    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))

    override val localizedContentDescription: LocalizedContentDescription? = json.optionalMap("localized_content_description")?.let { LocalizedContentDescription(it) }
    override val accessibilityHidden: Boolean? = json.optionalField("accessibility_hidden")
    override val contentDescription: String? = json.optionalField("content_description")

    var accessibilityRole: AccessibilityRole? = json.optionalMap("accessibility_role")?.let { AccessibilityRole.fromJson(it) }

    internal enum class AccessibilityRoleType {
        BUTTON,
        CONTAINER;

        companion object {
            fun fromString(value: String): AccessibilityRoleType? = when (value.lowercase()) {
                "button" -> BUTTON
                "container" -> CONTAINER
                else -> null
            }
        }
    }

    internal sealed class AccessibilityRole {
        abstract val type: AccessibilityRoleType

        data object Button : AccessibilityRole() {
            override val type = AccessibilityRoleType.BUTTON
        }

        data object Container : AccessibilityRole() {
            override val type = AccessibilityRoleType.CONTAINER
        }

        companion object {
            fun fromJson(json: JsonMap): AccessibilityRole? {
                val typeStr = json.optionalField<String>("type") ?: return null
                return when (AccessibilityRoleType.fromString(typeStr)) {
                    AccessibilityRoleType.BUTTON -> Button
                    AccessibilityRoleType.CONTAINER -> Container
                    null -> null
                }
            }
        }
    }
}

internal interface BaseCheckable: View, Accessible {
}

internal open class BaseCheckableInfo(
    json: JsonMap
) : ViewInfo(), BaseCheckable, View by view(json), Accessible by accessible(json) {
}

internal interface Checkable : BaseCheckable, View, Accessible {
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
    val margin = json.optionalMap("margin")?.let { Margin.fromJson(it) }
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
    val margin: Margin? = json.optionalMap("margin")?.let { Margin.fromJson(it) }
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
    val mediaFit: MediaFit = MediaFit.from(json.requireField("media_fit"))
    val position: Position = json.optionalMap("position")
        ?.let { Position.fromJson(it) } ?: Position.CENTER
    val video: Video? = json.optionalMap("video")?.let { Video.fromJson(it) }
}

internal class LabelInfo(
    json: JsonMap
) : ViewInfo(), View by view(json), Accessible by accessible(json) {
    val text: String = json.requireField("text")
    val ref: String? = json.optionalField("ref")
    val iconStart: IconStart? = json.optionalMap("icon_start")?.let { IconStart.fromJson(it) }
    val textAppearance: TextAppearance =
        TextAppearance.fromJson(json.requireField("text_appearance"))
    val markdownOptions: MarkdownOptions? = json.optionalMap("markdown")?.let { MarkdownOptions(it) }
    var accessibilityRole: AccessibilityRole? = json.optionalMap("accessibility_role")?.let { AccessibilityRole.fromJson(it) }
    val viewOverrides: ViewOverrides? = json.optionalMap("view_overrides")?.let { ViewOverrides(it) }

    internal sealed class IconStart(
        val type: Type
    ) {
        abstract val space: Int

        data class Floating(val icon: Image.Icon, override val space: Int): IconStart(Type.FLOATING)

        internal enum class Type(val value: String) {
            FLOATING("floating");

            internal companion object {

                @Throws(JsonException::class)
                fun fromJson(value: String): Type = entries.firstOrNull {
                    it.value.equals(value, ignoreCase = true)
                }?: throw JsonException("Invalid IconStart type: $value")
            }
        }

        internal companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): IconStart {
                val type = json.requireField<String>("type").let { Type.fromJson(it) }
                val space = json.requireField<Int>("space")

                return when (type) {
                    Type.FLOATING -> {
                        val icon = Image.Icon.fromJson(json.requireField("icon"))
                        Floating(icon, space)
                    }
                }
            }
        }
    }

    internal enum class AccessibilityRoleType {
        HEADING;

        companion object {
            fun fromString(value: String): AccessibilityRoleType? = when (value.lowercase()) {
                "heading" -> HEADING
                else -> null
            }
        }
    }

    internal sealed class AccessibilityRole {
        abstract val type: AccessibilityRoleType

        data class Heading(
            val level: Int
        ) : AccessibilityRole() {
            override val type = AccessibilityRoleType.HEADING
        }

        companion object {
            fun fromJson(json: JsonMap): AccessibilityRole? {
                val typeStr = json.optionalField<String>("type") ?: return null
                return when (AccessibilityRoleType.fromString(typeStr)) {
                    AccessibilityRoleType.HEADING -> Heading(
                        level = json.optionalField("level") ?: 1
                    )
                    null -> null
                }
            }
        }
    }

    internal class ViewOverrides(json: JsonMap) {
        val text = json.optionalList("text")?.map {
            ViewPropertyOverride(it, valueParser = { value -> value.optString() })
        }
        val iconStart = json.optionalList("icon_start")?.map {
            ViewPropertyOverride(it) { value -> IconStart.fromJson(value.optMap()) }
        }
        val ref = json.optionalList("ref")?.map {
            ViewPropertyOverride(it, valueParser = { value -> value.optString() })
        }
    }
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
    val attributeName: AttributeName? = attributeNameFromJson(json)
    val iconEnd: IconEnd? = json.optionalField<JsonMap>("icon_end")?.let {
        IconEnd.fromJson(it)
    }
    val viewOverrides: ViewOverrides? = json.optionalMap("view_overrides")?.let { ViewOverrides(it) }

    val emailRegistrationOptions: ThomasEmailRegistrationOptions? = json.get("email_registration")?.let {
        if (inputType == FormInputType.EMAIL) {
            ThomasEmailRegistrationOptions.fromJson(it)
        } else {
            null
        }
    }

    val smsLocales: List<SmsLocale>? = json.optionalList("locales")?.map(SmsLocale::fromJson)

    internal class ViewOverrides(json: JsonMap) {
        val iconEnd = json.optionalList("icon_end")?.map { iconEnd ->
            ViewPropertyOverride(iconEnd) { value -> IconEnd.fromJson(value.optMap()) }
        }
    }

    sealed class IconEnd(
        val type: Type,
    ) {
        data class Floating(val icon: Image.Icon): IconEnd(Type.FLOATING)

        internal enum class Type(val value: String) {
            FLOATING("floating");

            internal companion object {

                @Throws(JsonException::class)
                fun fromJson(value: String): Type = entries.firstOrNull {
                    it.value.equals(value, ignoreCase = true)
                }?: throw JsonException("Invalid IconEnd type: $value")
            }
        }

        internal companion object {
            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): IconEnd {
                val type = json.requireField<String>("type").let { Type.fromJson(it) }

                return when (type) {
                    Type.FLOATING -> {
                        val icon = Image.Icon.fromJson(json.requireField("icon"))
                        Floating(icon)
                    }
                }
            }
        }
    }

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
    val gestures = json.optionalList("gestures")?.let { PagerGesture.fromList(it) }

    val disableSwipeWhen = json.optionalList("disable_swipe_when")?.map(DisableSwipeSelector::fromJson)

    override val children: List<PagerItemInfo> = items
}

internal class CustomViewInfo(
    json: JsonMap
): ViewInfo(), View by view(json) {
    val name: String = json.requireField("name")
    val properties: JsonMap = json.optionalField("properties") ?: jsonMapOf()
}

internal enum class AutomatedAccessibilityActionType {
    ANNOUNCE;

    companion object {
        fun from(value: String?): AutomatedAccessibilityActionType? = when (value?.lowercase()) {
            "announce" -> ANNOUNCE
            else -> null
        }
    }
}

internal enum class AccessibilityActionType {
    DEFAULT,
    ESCAPE;

    companion object {
        fun from(value: String?): AccessibilityActionType? = when (value?.lowercase()) {
            "default" -> DEFAULT
            "escape" -> ESCAPE
            else -> null
        }
    }
}

internal class LocalizedContentDescription(json: JsonMap) {
    val ref: String? = json.optionalField("ref")
    val fallback: String = json.requireField("fallback")
}

internal class AutomatedAccessibilityAction(val type: AutomatedAccessibilityActionType) {
    companion object {
        @Throws(JsonException::class)
        fun from(json: JsonMap): AutomatedAccessibilityAction? {
            val typeString: String = json.opt("type").optString()

            return AutomatedAccessibilityActionType.from(typeString)?.let { AutomatedAccessibilityAction(it) }
        }

        @Throws(JsonException::class)
        fun fromList(jsonList: JsonList): List<AutomatedAccessibilityAction> {
            return if (jsonList.isEmpty) {
                emptyList()
            } else {
                jsonList.mapNotNull { from(it.optMap()) }
            }
        }
    }
}

internal class AccessibilityAction(json: JsonMap) : Accessible by accessible(json), Identifiable {

    val type: AccessibilityActionType? = AccessibilityActionType.from(json.optionalField<String>("type"))
    val reportingMetadata: JsonMap? = json.optionalField("reporting_metadata")
    val actions: Map<String, JsonValue>? = json.optionalList("actions")
        ?.let { parseActionsList(it) }
    val behaviors: List<ButtonClickBehaviorType>? = json.optionalList("behaviors")
        ?.let { ButtonClickBehaviorType.fromList(it) }

    override val identifier: String

    init {
        val fallback = localizedContentDescription?.fallback ?: throw JsonException("Missing 'fallback' in 'localized_content_description'")
        identifier = fallback
    }

    companion object {
        @Throws(JsonException::class)
        fun from(json: JsonMap): AccessibilityAction? {
            return try {
                AccessibilityAction(json)
            } catch (e: JsonException) {
                // Fail gracefully without creating the action if fields are missing
                null
            }
        }

        @Throws(JsonException::class)
        fun fromList(jsonList: JsonList): List<AccessibilityAction> =
            jsonList.mapNotNull { from(it.requireMap()) }

        private fun parseActionsList(actionsList: JsonList): Map<String, JsonValue> {
            val actionsMap = mutableMapOf<String, JsonValue>()
            for (jsonValue in actionsList) {
                val actionMap = jsonValue.requireMap()
                for ((key, value) in actionMap.map) {
                    actionsMap[key] = value
                }
            }
            return actionsMap
        }
    }
}

internal class PagerItemInfo(
    json: JsonMap
) : ItemInfo(viewInfoFromJson(json.requireField("view"))), Identifiable by identifiable(json) {
    val displayActions: Map<String, JsonValue>? =
        json.optionalMap("display_actions")?.map
    val automatedActions = json.optionalList("automated_actions")
        ?.let { AutomatedAction.fromList(it) }
    val accessibilityActions = json.optionalList("accessibility_actions")
        ?.let { AccessibilityAction.fromList(it) }
    val stateActions = json.optionalList("state_actions")?.map(StateAction::fromJson)
    val branching = json.get("branching")?.let(PageBranching::from)
}

internal class PagerIndicatorInfo(
    json: JsonMap
) : ViewInfo(), View by view(json) {
    val bindings: Bindings = Bindings(json.requireField("bindings"))
    val indicatorSpacing: Int = json.optionalField<Int>("spacing") ?: 4
    val automatedAccessibilityActions = json.optionalList("automated_accessibility_actions")
        ?.let { AutomatedAccessibilityAction.fromList(it) }

    internal class Bindings(json: JsonMap) {
        val selected: Binding = Binding(json.requireField("selected"))
        val unselected: Binding = Binding(json.requireField("unselected"))
    }

    internal class Binding(json: JsonMap) {
        val shapes: List<Shape> =
            json.requireField<JsonList>("shapes").map { Shape.fromJson(it.requireMap()) }
        val icon: Image.Icon? =
            json.optionalMap("icon")?.let { Image.Icon.fromJson(it) }
    }
}

internal class StoryIndicatorInfo(json: JsonMap) : ViewInfo(), View by view(json) {
    val source: StoryIndicatorSource = json.requireField<JsonMap>("source")
        .requireField<String>("type")
        .let { StoryIndicatorSource.from(it) }
    val style = StoryIndicatorStyle.from(json.requireField("style"))
    val automatedAccessibilityActions = json.optionalList("automated_accessibility_actions")
        ?.let { AutomatedAccessibilityAction.fromList(it) }
}

internal class StateControllerInfo(json: JsonMap) : ViewGroupInfo<ViewItemInfo>(), View by view(json) {
    val view: ViewInfo = viewInfoFromJson(json.requireField("view"))
    val initialState: JsonMap? = json.optionalField("initial_state")
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

    val branching = json.get("branching")?.let(PagerControllerBranching::from)
}

internal open class BaseToggleLayoutInfo(json: JsonMap) :  ViewGroupInfo<ViewItemInfo>(), Identifiable by identifiable(json), View by view(json) {
    val onToggleOn: ToggleActions = ToggleActions(
        stateActions = json.requireMap("on_toggle_on")
            .optionalList("state_actions")
            ?.map { StateAction.fromJson(it.requireMap()) }
    )

    val onToggleOff: ToggleActions = ToggleActions(
        stateActions = json.requireMap("on_toggle_off")
            .optionalList("state_actions")
            ?.map { StateAction.fromJson(it.requireMap()) }
    )

    val attributeValue: AttributeValue? = json.optionalField("attribute_value")
    val view: ViewInfo = viewInfoFromJson(json.requireField("view"))

    internal class ToggleActions(
        val stateActions: List<StateAction>?
    )

    override val children: List<ViewItemInfo> = listOf(ViewItemInfo(view))
}

internal class BasicToggleLayoutInfo(json: JsonMap) : BaseToggleLayoutInfo(json), Validatable by validatable(json) {
    val attributeName: AttributeName? = attributeNameFromJson(json)
}

internal class CheckboxToggleLayoutInfo(json: JsonMap) : BaseToggleLayoutInfo(json) {
    val reportingValue: JsonValue = json.requireField("reporting_value")
}

internal class RadioInputToggleLayoutInfo(json: JsonMap) : BaseToggleLayoutInfo(json) {
    val reportingValue: JsonValue = json.requireField("reporting_value")
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
