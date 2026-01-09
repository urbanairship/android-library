/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.R
import android.content.Context
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.property.TextAppearance
import com.urbanairship.android.layout.view.LabelView
import com.urbanairship.util.stringResource

internal class LabelModel(
    viewInfo: LabelInfo,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<LabelView, LabelInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = LabelView(context, this).apply {
        id = viewId

        viewInfo.labels?.let { label ->
            if (label.type == LabelInfo.AssociatedLabel.Type.LABELS) {
                val id = environment.viewIdResolver.viewId(label.viewId, label.viewType)
                labelFor = id
            }
        }
    }

    private fun resolveText(context: Context, state: ThomasState): String {
        val refs = state.resolveOptional(
            overrides = viewInfo.viewOverrides?.refs,
            default = viewInfo.refs
        )

        val ref = state.resolveOptional(
            overrides = viewInfo.viewOverrides?.ref,
            default = viewInfo.ref
        )

        val result = refs
            ?.firstNotNullOfOrNull { it.stringResource(context) }
            ?: ref?.stringResource(context)
        if (result != null) {
            return result
        }

        return state.resolveRequired(
            overrides = viewInfo.viewOverrides?.text,
            default = viewInfo.text
        )
    }

    fun resolveState(context: Context, state: ThomasState?): ResolvedState {
        return if (state != null) {
            ResolvedState(
                text = resolveText(context, state),
                iconStart = state.resolveOptional(
                    overrides = viewInfo.viewOverrides?.iconStart,
                    default = viewInfo.iconStart
                ),
                iconEnd = state.resolveOptional(
                    overrides = viewInfo.viewOverrides?.iconEnd,
                    default = viewInfo.iconEnd
                ),
                textAppearance = state.resolveRequired(
                    overrides = viewInfo.viewOverrides?.textAppearance,
                    default = viewInfo.textAppearance
                )
            )
        } else {
            ResolvedState(
                text = viewInfo.text,
                iconStart = viewInfo.iconStart,
                iconEnd = viewInfo.iconEnd,
                textAppearance = viewInfo.textAppearance
            )
        }
    }

    data class ResolvedState(
        val text: String,
        val iconStart: LabelInfo.LabelIcon?,
        val iconEnd: LabelInfo.LabelIcon?,
        val textAppearance: TextAppearance
    )
}
