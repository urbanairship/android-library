package com.urbanairship.android.layout

import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.info.CheckboxControllerInfo
import com.urbanairship.android.layout.info.CheckboxInfo
import com.urbanairship.android.layout.info.ContainerLayoutInfo
import com.urbanairship.android.layout.info.ContainerLayoutItemInfo
import com.urbanairship.android.layout.info.EmptyInfo
import com.urbanairship.android.layout.info.FormControllerInfo
import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.info.ItemInfo
import com.urbanairship.android.layout.info.ItemInfo.ViewItemInfo
import com.urbanairship.android.layout.info.LabelButtonInfo
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.LinearLayoutInfo
import com.urbanairship.android.layout.info.LinearLayoutItemInfo
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.info.NpsFormControllerInfo
import com.urbanairship.android.layout.info.PagerControllerInfo
import com.urbanairship.android.layout.info.PagerIndicatorInfo
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.info.PagerItemInfo
import com.urbanairship.android.layout.info.RadioInputControllerInfo
import com.urbanairship.android.layout.info.RadioInputInfo
import com.urbanairship.android.layout.info.ScoreInfo
import com.urbanairship.android.layout.info.ScrollLayoutInfo
import com.urbanairship.android.layout.info.StateControllerInfo
import com.urbanairship.android.layout.info.StoryIndicatorInfo
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.info.ToggleInfo
import com.urbanairship.android.layout.info.ViewGroupInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.model.CheckboxController
import com.urbanairship.android.layout.model.CheckboxModel
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.model.EmptyModel
import com.urbanairship.android.layout.model.FormController
import com.urbanairship.android.layout.model.ImageButtonModel
import com.urbanairship.android.layout.model.LabelButtonModel
import com.urbanairship.android.layout.model.LabelModel
import com.urbanairship.android.layout.model.LinearLayoutModel
import com.urbanairship.android.layout.model.MediaModel
import com.urbanairship.android.layout.model.ModelProperties
import com.urbanairship.android.layout.model.NpsFormController
import com.urbanairship.android.layout.model.PagerController
import com.urbanairship.android.layout.model.PagerIndicatorModel
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.model.RadioInputController
import com.urbanairship.android.layout.model.RadioInputModel
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.model.ScrollLayoutModel
import com.urbanairship.android.layout.model.StateController
import com.urbanairship.android.layout.model.StoryIndicatorModel
import com.urbanairship.android.layout.model.TextInputModel
import com.urbanairship.android.layout.model.ToggleModel
import com.urbanairship.android.layout.model.WebViewModel
import com.urbanairship.android.layout.property.ViewType

internal class ModelFactoryException(message: String) : Exception(message)

internal interface ModelFactory {
    @Throws(ModelFactoryException::class)
    fun create(info: ViewInfo, environment: ModelEnvironment): AnyModel
}

/** Temporary unique identifier for a layout node. */
private typealias Tag = String

internal class ThomasModelFactory : ModelFactory {
    /** Controllers by tag */
    private val processedControllers = mutableMapOf<Tag, LayoutNode.Builder>()
    /** Node builders by tag */
    private val processedNodes = mutableMapOf<Tag, LayoutNode.Builder>()
    /** Generated tags by type */
    private val tagIndexMap = mutableMapOf<ViewType, Int>()

    private lateinit var rootTag: Tag

    @Throws(ModelFactoryException::class)
    override fun create(info: ViewInfo, environment: ModelEnvironment): AnyModel {
        rootTag = generateTag(info)
        process(info)
        return build(environment)
    }

    private fun generateTag(info: ViewInfo): Tag {
        val id = tagIndexMap
            .getOrElse(info.type) { 0 }
            .also { tagIndexMap[info.type] = it + 1 }
        return "${info.type}_$id"
    }

    private fun process(root: ViewInfo) {
        // Processing stack entry
        data class StackEntry(
            val tag: Tag,
            val parentTag: Tag?,
            val info: ItemInfo,
            val controllers: Controllers.Builder,
            val pagerPageId: String?
        )
        // Processing stack
        val stack = ArrayDeque<StackEntry>()

        // Create controllers builder
        val emptyControllers = Controllers.Builder()

        // Add root node to the stack
        stack.addFirst(
            StackEntry(
                tag = rootTag,
                parentTag = null,
                info = ViewItemInfo(root),
                controllers = emptyControllers,
                pagerPageId = null
            )
        )

        // Process layout
        while (stack.isNotEmpty()) {
            val (tag, parentTag, info, controllers, pagerPageId) = stack.removeFirst()
            // Create node builder and add it to the processed nodes map
            val node = LayoutNode.Builder(
                tag = tag,
                info = info,
                controllers = controllers,
                pagerPageId = pagerPageId
            )
            // Add node to parent (the only time we won't have a parent tag is for the root node)
            if (!parentTag.isNullOrEmpty()) {
                processedNodes[parentTag]?.childTags?.add(node.tag)
            }
            val childControllers = if (info.type.isController) {
                // If node is a controller, update the controllers for the child, add this node
                // to the processed controllers map, and also update the controllers for this node
                // so that it can access its own state.
                controllers.update(info.type, tag).also {
                    processedControllers[tag] = node
                    node.controllers = node.controllers.update(info.type, tag)
                }
            } else {
                // If node is not a controller, use the current set of controllers for this node
                controllers
            }
            // Save processed node
            processedNodes[tag] = node
            // If node is a view group, add children to the processing stack. We push children in
            // reverse order so that they're removed from the FIFO stack in the correct order.
            if (info.info is ViewGroupInfo<*>) {
                val children = info.info.children
                for (i in (children.size - 1).downTo(0)) {
                    val child = children[i]
                    stack.addFirst(
                        StackEntry(
                            tag = generateTag(child.info),
                            parentTag = tag,
                            info = child,
                            controllers = childControllers,
                            pagerPageId = pagerPageId ?: (child as? PagerItemInfo)?.identifier
                        )
                    )
                }
            }
        }
    }

    @Throws(ModelFactoryException::class)
    private fun build(environment: ModelEnvironment): AnyModel {
        // Map of tags to built controller nodes
        val controllers = processedControllers.mapValues { it.value.build() }
        // Mutable map of tags to built models
        val builtModels = mutableMapOf<Tag, Pair<AnyModel, ItemInfo>>()
        // Layout states, passed to model via their model environment when a node references
        // controllers by tag
        val layoutStates = controllers.mapValues { (_, ctrl) ->
            createMutableSharedState(ctrl.info.info)
        }
        // Loop over processed nodes until we've built all models
        while (processedNodes.isNotEmpty()) {
            // For each pass, find any nodes that can be built (i.e. all their children are built)
            val leaves = processedNodes
                .filter {
                    it.value.childTags.isEmpty() || builtModels.keys.containsAll(it.value.childTags)
                }
                .map { it.key to it.value.build() }

            // Build this batch of models
            for ((tag, node) in leaves) {
                // Look up children
                val children = node.childTags.map {
                    builtModels[it] ?: throw ModelFactoryException(
                        "Unable to build model. Child with tag '$it' is not built yet!"
                    )
                }
                // Setup environment and properties
                val childEnvironment = environment.withState(
                    node.controllers.buildLayoutState(layoutStates)
                )
                val properties = ModelProperties(
                    pagerPageId = node.pagerPageId,
                )

                // Build model and store it in our builtModels map, so that it can be looked up
                // in subsequent passes.
                val model = model(node, children, childEnvironment, properties)
                builtModels[tag] = Pair(model, node.info)

                processedNodes.remove(tag)
            }
        }
        val root = builtModels[rootTag]
            ?: throw ModelFactoryException("Failed to build models. Root model not found!")
        // Return the model from the pair, ignoring the item info
        return root.first
    }

    private fun createMutableSharedState(info: ViewInfo): SharedState<State>? {
        return when (info) {
            is FormControllerInfo -> SharedState(
                State.Form(info.identifier, FormType.Form, info.responseType))
            is NpsFormControllerInfo -> SharedState(
                State.Form(info.identifier, FormType.Nps(info.npsIdentifier), info.responseType)
            )
            is RadioInputControllerInfo -> SharedState(State.Radio(info.identifier))
            is CheckboxControllerInfo -> SharedState(
                State.Checkbox(info.identifier, info.minSelection, info.maxSelection)
            )
            is PagerControllerInfo -> SharedState(State.Pager(info.identifier))
            is StateControllerInfo -> SharedState(State.Layout())
            else -> null
        }
    }

    data class LayoutNode(
        val tag: String,
        val info: ItemInfo,
        val childTags: List<Tag>,
        val controllers: Controllers,
        val pagerPageId: String?
    ) {
        data class Builder(
            val tag: String,
            var info: ItemInfo,
            val childTags: MutableList<Tag> = mutableListOf(),
            var style: String? = null,
            var controllers: Controllers.Builder = Controllers.Builder(),
            val pagerPageId: String?
        ) {
            fun build(): LayoutNode = LayoutNode(
                tag = tag,
                info = info,
                childTags = childTags.toList(),
                controllers = controllers.build(),
                pagerPageId = pagerPageId
            )
        }
    }

    data class Controllers(
        val form: List<Tag>,
        val pager: Tag?,
        val checkbox: Tag?,
        val radio: Tag?,
        val layout: Tag?
    ) {
        @Suppress("UNCHECKED_CAST")
        fun buildLayoutState(states: Map<Tag, SharedState<State>?>): LayoutState {
            val childForm = form.firstOrNull()
            val parentForm = form.getOrNull(1)
            return LayoutState(
                form = childForm?.let { states[it] as? SharedState<State.Form> },
                parentForm = parentForm?.let { states[it] as? SharedState<State.Form> },
                pager = pager?.let { states[it] as? SharedState<State.Pager> },
                checkbox = checkbox?.let { states[it] as? SharedState<State.Checkbox> },
                radio = radio?.let { states[it] as? SharedState<State.Radio> },
                layout = layout?.let { states[it] as? SharedState<State.Layout> }
            )
        }

        data class Builder(
            val form: List<Tag> = mutableListOf(),
            var pager: Tag? = null,
            var checkbox: Tag? = null,
            var radio: Tag? = null,
            var layout: Tag? = null
        ) {
            fun update(type: ViewType, tag: Tag): Builder = when (type) {
                ViewType.FORM_CONTROLLER,
                ViewType.NPS_FORM_CONTROLLER -> copy(form = listOf(tag) + form)
                ViewType.PAGER_CONTROLLER -> copy(pager = tag)
                ViewType.CHECKBOX_CONTROLLER -> copy(checkbox = tag)
                ViewType.RADIO_INPUT_CONTROLLER -> copy(radio = tag)
                ViewType.STATE_CONTROLLER -> copy(layout = tag)
                else -> this
            }

            fun build(): Controllers = Controllers(
                form = form.toList(),
                pager = pager,
                checkbox = checkbox,
                radio = radio,
                layout = layout
            )
        }
    }

    @Throws(ModelFactoryException::class)
    private fun model(
        node: LayoutNode,
        children: List<Pair<AnyModel, ItemInfo>>,
        environment: ModelEnvironment,
        properties: ModelProperties
    ): AnyModel = when (val info = node.info.info) {
        is ViewGroupInfo<*> -> when (info) {
            is ContainerLayoutInfo -> ContainerLayoutModel(
                info = info,
                items = children.map { (model, itemInfo) ->
                    (itemInfo as? ContainerLayoutItemInfo)?.let {
                        ContainerLayoutModel.Item(it, model)
                    } ?: throw ModelFactoryException("ContainerLayoutItemInfo expected")
                },
                env = environment,
                props = properties
            )
            is LinearLayoutInfo -> LinearLayoutModel(
                info = info,
                items = children.map { (model, itemInfo) ->
                    (itemInfo as? LinearLayoutItemInfo)?.let {
                        LinearLayoutModel.Item(it, model)
                    } ?: throw ModelFactoryException("LinearLayoutItemInfo expected")
                },
                env = environment,
                props = properties
            )
            is PagerInfo -> PagerModel(
                info = info,
                items = children.map { (model, itemInfo) ->
                    (itemInfo as? PagerItemInfo)?.let {
                        PagerModel.Item(
                            view = model,
                            identifier = itemInfo.identifier,
                            displayActions = itemInfo.displayActions,
                            automatedActions = itemInfo.automatedActions
                        )
                    } ?: throw ModelFactoryException("PagerItemInfo expected")
                },
                pagerState = environment.layoutState.pager
                    ?: throw ModelFactoryException("Required pager state was null for PagerController!"),
                env = environment,
                props = properties
            )
            is ScrollLayoutInfo -> ScrollLayoutModel(
                info = info,
                view = children.first().first,
                env = environment,
                props = properties
            )
            is FormControllerInfo -> FormController(
                info = info,
                view = children.first().first,
                formState = environment.layoutState.form
                    ?: throw ModelFactoryException("Required form state was null for FormController!"),
                parentFormState = environment.layoutState.parentForm,
                // pagerState can be null, depending on the layout.
                pagerState = environment.layoutState.pager,
                env = environment,
                props = properties
            )
            is NpsFormControllerInfo -> NpsFormController(
                info = info,
                view = children.first().first,
                formState = environment.layoutState.form
                    ?: throw ModelFactoryException("Required form state was null for NpsFormController!"),
                parentFormState = environment.layoutState.parentForm,
                // pagerState can be null, depending on the layout.
                pagerState = environment.layoutState.pager,
                env = environment,
                props = properties
            )
            is PagerControllerInfo -> PagerController(
                info = info,
                view = children.first().first,
                pagerState = environment.layoutState.pager
                    ?: throw ModelFactoryException("Required pager state was null for PagerController!"),
                env = environment,
                props = properties
            )
            is CheckboxControllerInfo -> CheckboxController(
                info = info, view = children.first().first,
                formState = environment.layoutState.form
                    ?: throw ModelFactoryException("Required form state was null for CheckboxController!"),
                checkboxState = environment.layoutState.checkbox
                    ?: throw ModelFactoryException("Required checkbox state was null for CheckboxController!"),
                env = environment,
                props = properties
            )
            is RadioInputControllerInfo -> RadioInputController(
                info = info,
                view = children.first().first,
                formState = environment.layoutState.form
                    ?: throw ModelFactoryException("Required form state was null for RadioInputController!"),
                radioState = environment.layoutState.radio
                    ?: throw ModelFactoryException("Required radio state was null for RadioInputController!"),
                env = environment,
                props = properties
            )
            is StateControllerInfo -> StateController(
                info = info,
                view = children.first().first,
                env = environment,
                props = properties
            )

            else -> throw ModelFactoryException("Unsupported view type: ${info::class.java.name}")
        }
        is EmptyInfo -> EmptyModel(info = info, env = environment, props = properties)
        is WebViewInfo -> WebViewModel(info = info, env = environment, props = properties)
        is MediaInfo -> MediaModel(info = info, env = environment, props = properties)
        is LabelInfo -> LabelModel(info = info, env = environment, props = properties)
        is LabelButtonInfo -> LabelButtonModel(
            info = info,
            label = LabelModel(info.label, environment, properties),
            formState = environment.layoutState.form,
            pagerState = environment.layoutState.pager,
            env = environment,
            props = properties
        )
        is ImageButtonInfo -> ImageButtonModel(
            info = info,
            formState = environment.layoutState.form,
            pagerState = environment.layoutState.pager,
            env = environment,
            props = properties
        )
        is PagerIndicatorInfo -> PagerIndicatorModel(
            info = info,
            env = environment,
            props = properties
        )
        is StoryIndicatorInfo -> StoryIndicatorModel(
            info = info,
            env = environment,
            props = properties
        )
        is CheckboxInfo -> CheckboxModel(
            info = info,
            checkboxState = environment.layoutState.checkbox
                ?: throw ModelFactoryException("Required checkbox state was null for CheckboxModel!"),
            formState = environment.layoutState.form
                ?: throw ModelFactoryException("Required form state was null for CheckboxModel!"),
            env = environment,
            props = properties
        )
        is ToggleInfo -> ToggleModel(
            info = info,
            formState = environment.layoutState.form
                ?: throw ModelFactoryException("Required form state was null for ToggleModel!"),
            env = environment,
            props = properties
        )
        is RadioInputInfo -> RadioInputModel(
            info = info,
            radioState = environment.layoutState.radio
                ?: throw ModelFactoryException("Required radio state was null for RadioInputModel!"),
            formState = environment.layoutState.form
                ?: throw ModelFactoryException("Required form state was null for RadioInputModel!"),
            env = environment,
            props = properties
        )
        is TextInputInfo -> TextInputModel(
            info = info,
            formState = environment.layoutState.form
                ?: throw ModelFactoryException("Required form state was null for TextInputModel!"),
            env = environment,
            props = properties
        )
        is ScoreInfo -> ScoreModel(
            info = info,
            formState = environment.layoutState.form
                ?: throw ModelFactoryException("Required form state was null for ScoreModel!"),
            env = environment,
            props = properties
        )

        else -> throw ModelFactoryException("Unsupported view type: ${info::class.java.name}")
    }
}
