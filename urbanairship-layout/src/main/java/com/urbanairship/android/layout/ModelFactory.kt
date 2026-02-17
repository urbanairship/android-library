package com.urbanairship.android.layout

import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.FormType
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.PagersViewTracker
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasForm
import com.urbanairship.android.layout.environment.makeThomasState
import com.urbanairship.android.layout.info.BasicToggleLayoutInfo
import com.urbanairship.android.layout.info.ButtonLayoutInfo
import com.urbanairship.android.layout.info.CheckboxControllerInfo
import com.urbanairship.android.layout.info.CheckboxInfo
import com.urbanairship.android.layout.info.CheckboxToggleLayoutInfo
import com.urbanairship.android.layout.info.ContainerLayoutInfo
import com.urbanairship.android.layout.info.ContainerLayoutItemInfo
import com.urbanairship.android.layout.info.CustomViewInfo
import com.urbanairship.android.layout.info.EmptyInfo
import com.urbanairship.android.layout.info.FormControllerInfo
import com.urbanairship.android.layout.info.IconViewInfo
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
import com.urbanairship.android.layout.info.RadioInputToggleLayoutInfo
import com.urbanairship.android.layout.info.ScoreControllerInfo
import com.urbanairship.android.layout.info.ScoreInfo
import com.urbanairship.android.layout.info.ScoreToggleLayoutInfo
import com.urbanairship.android.layout.info.ScrollLayoutInfo
import com.urbanairship.android.layout.info.StackImageButtonInfo
import com.urbanairship.android.layout.info.StateControllerInfo
import com.urbanairship.android.layout.info.StoryIndicatorInfo
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.info.ToggleInfo
import com.urbanairship.android.layout.info.ViewGroupInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.model.BasicToggleLayoutModel
import com.urbanairship.android.layout.model.ButtonLayoutModel
import com.urbanairship.android.layout.model.CheckboxController
import com.urbanairship.android.layout.model.CheckboxModel
import com.urbanairship.android.layout.model.CheckboxToggleLayoutModel
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.model.CustomViewModel
import com.urbanairship.android.layout.model.EmptyModel
import com.urbanairship.android.layout.model.FormController
import com.urbanairship.android.layout.model.HorizontalScrollLayoutModel
import com.urbanairship.android.layout.model.IconModel
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
import com.urbanairship.android.layout.model.RadioInputToggleLayoutModel
import com.urbanairship.android.layout.model.ScoreController
import com.urbanairship.android.layout.model.ScoreInputToggleLayoutModel
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.model.StackImageButtonModel
import com.urbanairship.android.layout.model.StateController
import com.urbanairship.android.layout.model.StoryIndicatorModel
import com.urbanairship.android.layout.model.TextInputModel
import com.urbanairship.android.layout.model.ToggleModel
import com.urbanairship.android.layout.model.VerticalScrollLayoutModel
import com.urbanairship.android.layout.model.WebViewModel
import com.urbanairship.android.layout.property.Direction
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
            val (tag, parentTag, info, controllers, pagerPageId) = stack.removeAt(0)
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
            createMutableSharedState(ctrl.info.info, environment.stateStorage)
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
                    node.controllers.buildLayoutState(layoutStates, environment.pagerTracker)
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

    private fun createMutableSharedState(info: ViewInfo, stateStorage: LayoutStateStorage?): SharedState<State>? {
        fun makeState(identifier: String, default: () -> State): SharedState<State> {
            val storage = stateStorage ?: return SharedState(default())

            return storage.getState(identifier) { SharedState(default()) }
        }

        return when (info) {
            is FormControllerInfo -> makeState(
                identifier = info.identifier,
                default = {
                    State.Form(
                        identifier = info.identifier,
                        formType = FormType.Form,
                        formResponseType = info.responseType,
                        validationMode = info.validationMode,
                        initialChildrenValues = emptyMap()
                    )
                }
            )
            is NpsFormControllerInfo -> makeState(
                identifier = info.identifier,
                default = {
                    State.Form(
                        identifier = info.identifier,
                        formType = FormType.Nps(info.npsIdentifier),
                        formResponseType = info.responseType,
                        validationMode = info.validationMode,
                        initialChildrenValues = emptyMap()
                    )
                }
            )
            is RadioInputControllerInfo -> makeState(
                identifier = info.identifier,
                default = { State.Radio(info.identifier) }
            )
            is ScoreControllerInfo -> makeState(
                identifier = info.identifier,
                default = { State.Score(info.identifier) }
            )
            is CheckboxControllerInfo -> makeState(
                identifier = info.identifier,
                default = {
                    State.Checkbox(info.identifier, info.minSelection, info.maxSelection)
                }
            )
            is PagerControllerInfo -> makeState(
                identifier = info.identifier,
                default = {
                    State.Pager(identifier = info.identifier, branching = info.branching)
                }
            )
            is StateControllerInfo -> {
                val identifier = info.identifier ?: StateControllerInfo.IDENTIFIER
                makeState(
                    identifier = identifier,
                    default = { State.Layout(identifier =  identifier) }
                )
            }

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
        val score: Tag?,
        val layout: Tag?,
        val story: Tag?
    ) {
        @Suppress("UNCHECKED_CAST")
        fun buildLayoutState(states: Map<Tag, SharedState<State>?>, pagerTracker: PagersViewTracker?): LayoutState {
            val childForm = form.firstOrNull()
            val parentForm = form.getOrNull(1)

            val formFlow = childForm?.let { states[it] as? SharedState<State.Form> }
            val parentFormFlow = parentForm?.let { states[it] as? SharedState<State.Form> }

            val layoutFlow = layout?.let { states[it] as? SharedState<State.Layout> }
                ?: LayoutState.EMPTY.layout

            val pagerFlow = pager?.let { states[it] as? SharedState<State.Pager> }

            return LayoutState(
                thomasForm = formFlow?.let { ThomasForm(it, pagerFlow) },
                parentForm = parentFormFlow?.let { ThomasForm(it, pagerFlow) },
                pager = pagerFlow,
                checkbox = checkbox?.let { states[it] as? SharedState<State.Checkbox> },
                radio = radio?.let { states[it] as? SharedState<State.Radio> },
                score = score?.let { states[it] as? SharedState<State.Score> },
                layout = layoutFlow,
                thomasState = makeThomasState(formFlow, layoutFlow, pagerFlow),
                pagerTracker = pagerTracker
            )
        }

        data class Builder(
            val form: List<Tag> = mutableListOf(),
            var pager: Tag? = null,
            var checkbox: Tag? = null,
            var radio: Tag? = null,
            var score: Tag? = null,
            var layout: Tag? = null,
            var story: Tag? = null
        ) {
            fun update(type: ViewType, tag: Tag): Builder = when (type) {
                ViewType.FORM_CONTROLLER,
                ViewType.NPS_FORM_CONTROLLER -> copy(form = listOf(tag) + form)
                ViewType.PAGER_CONTROLLER -> copy(pager = tag)
                ViewType.CHECKBOX_CONTROLLER -> copy(checkbox = tag)
                ViewType.RADIO_INPUT_CONTROLLER -> copy(radio = tag)
                ViewType.SCORE_CONTROLLER -> copy(score = tag)
                ViewType.STATE_CONTROLLER -> copy(layout = tag)
                ViewType.STORY_INDICATOR -> copy(story = tag)
                else -> this
            }

            fun build(): Controllers = Controllers(
                form = form.toList(),
                pager = pager,
                checkbox = checkbox,
                radio = radio,
                score = score,
                layout = layout,
                story = story
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
                viewInfo = info,
                items = children.map { (model, itemInfo) ->
                    (itemInfo as? ContainerLayoutItemInfo)?.let {
                        ContainerLayoutModel.Item(it, model)
                    } ?: throw ModelFactoryException("ContainerLayoutItemInfo expected")
                },
                environment = environment,
                properties = properties
            )
            is LinearLayoutInfo -> LinearLayoutModel(
                viewInfo = info,
                items = children.map { (model, itemInfo) ->
                    (itemInfo as? LinearLayoutItemInfo)?.let {
                        LinearLayoutModel.Item(it, model)
                    } ?: throw ModelFactoryException("LinearLayoutItemInfo expected")
                },
                environment = environment,
                properties = properties
            )
            is PagerInfo -> PagerModel(
                viewInfo = info,
                availablePages = children.map { (model, itemInfo) ->
                    (itemInfo as? PagerItemInfo)?.let {
                        PagerModel.Item(
                            view = model,
                            identifier = itemInfo.identifier,
                            displayActions = itemInfo.displayActions,
                            automatedActions = itemInfo.automatedActions,
                            accessibilityActions = itemInfo.accessibilityActions,
                            stateActions = itemInfo.stateActions,
                            branching = itemInfo.branching
                        )
                    } ?: throw ModelFactoryException("PagerItemInfo expected")
                },
                pagerState = environment.layoutState.pager
                    ?: throw ModelFactoryException("Required pager state was null for PagerController!"),
                environment = environment,
                properties = properties
            )
            is ScrollLayoutInfo -> {
                when(info.direction) {
                    Direction.VERTICAL -> VerticalScrollLayoutModel(
                        viewInfo = info,
                        view = children.first().first,
                        environment = environment,
                        properties = properties
                    )
                    Direction.HORIZONTAL -> HorizontalScrollLayoutModel(
                        viewInfo = info,
                        view = children.first().first,
                        environment = environment,
                        properties = properties
                    )
                }
            }
            is ButtonLayoutInfo -> ButtonLayoutModel(
                viewInfo = info,
                view = children.first().first,
                formState = environment.layoutState.thomasForm,
                pagerState = environment.layoutState.pager,
                environment = environment,
                properties = properties
            )
            is FormControllerInfo -> FormController(
                viewInfo = info,
                view = children.first().first,
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state was null for FormController!"),
                parentState = environment.layoutState.parentForm,
                // pagerState can be null, depending on the layout.
                pagerState = environment.layoutState.pager,
                environment = environment,
                properties = properties
            )
            is NpsFormControllerInfo -> NpsFormController(
                viewInfo = info,
                view = children.first().first,
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state was null for NpsFormController!"),
                parentForm = environment.layoutState.parentForm,
                // pagerState can be null, depending on the layout.
                pagerState = environment.layoutState.pager,
                environment = environment,
                properties = properties
            )
            is PagerControllerInfo -> PagerController(
                viewInfo = info,
                view = children.first().first,
                pagerState = environment.layoutState.pager
                    ?: throw ModelFactoryException("Required pager state was null for PagerController!"),
                environment = environment,
                properties = properties,
                branching = info.branching
            )
            is CheckboxControllerInfo -> CheckboxController(
                viewInfo = info,
                view = children.first().first,
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state was null for CheckboxController!"),
                checkboxState = environment.layoutState.checkbox
                    ?: throw ModelFactoryException("Required checkbox state was null for CheckboxController!"),
                environment = environment,
                properties = properties
            )
            is RadioInputControllerInfo -> RadioInputController(
                viewInfo = info,
                view = children.first().first,
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state was null for RadioInputController!"),
                radioState = environment.layoutState.radio
                    ?: throw ModelFactoryException("Required radio state was null for RadioInputController!"),
                environment = environment,
                properties = properties
            )

            is ScoreControllerInfo -> ScoreController(
                viewInfo = info,
                view = children.first().first,
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state(score) was null for ScoreController!"),
                scoreState = environment.layoutState.score
                    ?: throw ModelFactoryException("Required radio state(score) was null for ScoreController!"),
                environment = environment,
                properties = properties
            )

            is StateControllerInfo -> StateController(
                viewInfo = info,
                view = children.first().first,
                environment = environment,
                properties = properties
            )

            is BasicToggleLayoutInfo -> BasicToggleLayoutModel(
                viewInfo = info,
                view = children.first().first,
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state was null for RadioInputController!"),
                environment = environment,
                properties = properties
            )

            is CheckboxToggleLayoutInfo -> CheckboxToggleLayoutModel(
                viewInfo = info,
                view = children.first().first,
                checkboxState = environment.layoutState.checkbox
                    ?: throw ModelFactoryException("Required checkbox state was null for CheckboxController!"),
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state was null for RadioInputController!"),
                environment = environment,
                properties = properties
            )

            is RadioInputToggleLayoutInfo -> RadioInputToggleLayoutModel(
                viewInfo = info,
                view = children.first().first,
                radioState = environment.layoutState.radio
                    ?: throw ModelFactoryException("Required radio state was null for RadioInputController!"),
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state was null for RadioInputController!"),
                environment = environment,
                properties = properties
            )

            is ScoreToggleLayoutInfo -> ScoreInputToggleLayoutModel(
                viewInfo = info,
                view = children.first().first,
                scoreState = environment.layoutState.score
                    ?: throw ModelFactoryException("Required score state was null for ScoreToggleLayout!"),
                formState = environment.layoutState.thomasForm
                    ?: throw ModelFactoryException("Required form state(score) was null for RadioInputController!"),
                environment = environment,
                properties = properties
            )

            else -> throw ModelFactoryException("Unsupported view type: ${info::class.java.name}")
        }
        is EmptyInfo -> EmptyModel(
            viewInfo = info,
            environment = environment,
            properties = properties
        )
        is WebViewInfo -> WebViewModel(
            viewInfo = info,
            environment = environment,
            properties = properties
        )
        is MediaInfo -> MediaModel(
            viewInfo = info,
            pagerState = environment.layoutState.pager,
            environment = environment,
            properties = properties
        )
        is LabelInfo -> LabelModel(
            viewInfo = info,
            environment = environment,
            properties = properties
        )
        is LabelButtonInfo -> LabelButtonModel(
            viewInfo = info,
            label = LabelModel(info.label, environment, properties),
            formState = environment.layoutState.thomasForm,
            pagerState = environment.layoutState.pager,
            environment = environment,
            properties = properties
        )
        is ImageButtonInfo -> ImageButtonModel(
            viewInfo = info,
            formState = environment.layoutState.thomasForm,
            pagerState = environment.layoutState.pager,
            environment = environment,
            properties = properties
        )
        is PagerIndicatorInfo -> PagerIndicatorModel(
            viewInfo = info,
            environment = environment,
            properties = properties
        )
        is StoryIndicatorInfo -> StoryIndicatorModel(
            viewInfo = info,
            environment = environment,
            properties = properties
        )
        is CheckboxInfo -> CheckboxModel(
            viewInfo = info,
            checkboxState = environment.layoutState.checkbox
                ?: throw ModelFactoryException("Required checkbox state was null for CheckboxModel!"),
            formState = environment.layoutState.thomasForm
                ?: throw ModelFactoryException("Required form state was null for CheckboxModel!"),
            environment = environment,
            properties = properties
        )
        is ToggleInfo -> ToggleModel(
            viewInfo = info,
            formState = environment.layoutState.thomasForm
                ?: throw ModelFactoryException("Required form state was null for ToggleModel!"),
            environment = environment,
            properties = properties
        )
        is RadioInputInfo -> RadioInputModel(
            viewInfo = info,
            radioState = environment.layoutState.radio
                ?: throw ModelFactoryException("Required radio state was null for RadioInputModel!"),
            formState = environment.layoutState.thomasForm
                ?: throw ModelFactoryException("Required form state was null for RadioInputModel!"),
            environment = environment,
            properties = properties
        )
        is TextInputInfo -> TextInputModel(
            viewInfo = info,
            formState = environment.layoutState.thomasForm
                ?: throw ModelFactoryException("Required form state was null for TextInputModel!"),
            environment = environment,
            properties = properties
        )
        is ScoreInfo -> ScoreModel(
            viewInfo = info,
            formState = environment.layoutState.thomasForm
                ?: throw ModelFactoryException("Required form state was null for ScoreModel!"),
            environment = environment,
            properties = properties
        )
        is CustomViewInfo -> CustomViewModel(
            viewInfo = info,
            environment = environment,
            properties = properties
        )
        is IconViewInfo -> IconModel(
            viewInfo = info,
            environment = environment,
            properties = properties
        )
        is StackImageButtonInfo -> StackImageButtonModel(
            viewInfo = info,
            formState = environment.layoutState.thomasForm,
            pagerState = environment.layoutState.pager,
            environment = environment,
            properties = properties
        )

        else -> throw ModelFactoryException("Unsupported view type: ${info::class.java.name}")
    }
}
