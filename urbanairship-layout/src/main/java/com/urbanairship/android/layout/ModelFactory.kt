package com.urbanairship.android.layout

import com.urbanairship.android.layout.info.CheckboxControllerInfo
import com.urbanairship.android.layout.info.CheckboxInfo
import com.urbanairship.android.layout.info.ContainerLayoutInfo
import com.urbanairship.android.layout.info.EmptyInfo
import com.urbanairship.android.layout.info.FormControllerInfo
import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.info.LabelButtonInfo
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.LinearLayoutInfo
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.info.NpsFormControllerInfo
import com.urbanairship.android.layout.info.PagerControllerInfo
import com.urbanairship.android.layout.info.PagerIndicatorInfo
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.info.RadioInputControllerInfo
import com.urbanairship.android.layout.info.RadioInputInfo
import com.urbanairship.android.layout.info.ScoreInfo
import com.urbanairship.android.layout.info.ScrollLayoutInfo
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.info.ToggleInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.model.BaseModel
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
import com.urbanairship.android.layout.model.NpsFormController
import com.urbanairship.android.layout.model.PagerController
import com.urbanairship.android.layout.model.PagerIndicatorModel
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.model.RadioInputController
import com.urbanairship.android.layout.model.RadioInputModel
import com.urbanairship.android.layout.model.ScoreModel
import com.urbanairship.android.layout.model.ScrollLayoutModel
import com.urbanairship.android.layout.model.TextInputModel
import com.urbanairship.android.layout.model.ToggleModel
import com.urbanairship.android.layout.model.WebViewModel

internal interface ModelFactory {
    @Throws(ModelFactoryException::class)
    fun create(info: ViewInfo, environment: ModelEnvironment): BaseModel
}

internal class DefaultModelFactory : ModelFactory {
    override fun create(info: ViewInfo, environment: ModelEnvironment): BaseModel =
        when (info) {
            is ContainerLayoutInfo -> ContainerLayoutModel(info, environment)
            is LinearLayoutInfo -> LinearLayoutModel(info, environment)
            is ScrollLayoutInfo -> ScrollLayoutModel(info, environment)
            is EmptyInfo -> EmptyModel(info, environment)
            is WebViewInfo -> WebViewModel(info, environment)
            is MediaInfo -> MediaModel(info, environment)
            is LabelInfo -> LabelModel(info, environment)
            is LabelButtonInfo -> LabelButtonModel(info, environment)
            is ImageButtonInfo -> ImageButtonModel(info, environment)
            is PagerControllerInfo -> PagerController(info, environment)
            is PagerInfo -> PagerModel(info, environment)
            is PagerIndicatorInfo -> PagerIndicatorModel(info, environment)
            is FormControllerInfo -> FormController(info, environment)
            is NpsFormControllerInfo -> NpsFormController(info, environment)
            is CheckboxControllerInfo -> CheckboxController(info, environment)
            is CheckboxInfo -> CheckboxModel(info, environment)
            is ToggleInfo -> ToggleModel(info, environment)
            is RadioInputControllerInfo -> RadioInputController(info, environment)
            is RadioInputInfo -> RadioInputModel(info, environment)
            is TextInputInfo -> TextInputModel(info, environment)
            is ScoreInfo -> ScoreModel(info, environment)

            else -> throw ModelFactoryException("Unsupported view type: ${info::class.java.name}")
        }
}

internal class ModelFactoryException(message: String) : Exception(message)
