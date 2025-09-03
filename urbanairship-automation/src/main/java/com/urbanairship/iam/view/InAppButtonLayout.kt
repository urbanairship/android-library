/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.urbanairship.automation.R
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageButtonLayoutType

/**
 * In-app button layout. Supports stacked, separated, and joined button layouts.
 */

/**
 * Default constructor.
 *
 * @param context A Context object used to access application assets.
 * @param attrs An AttributeSet passed to our parent.
 * @param defStyle The default style resource ID.
 * @param defResStyle A resource identifier of a style resource that supplies default values for
 * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
 * look for defaults.
 */
internal class InAppButtonLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defResStyle: Int = 0
) : BoundedLinearLayout(context, attrs, defStyle, defResStyle) {

    private var stackedSpaceHeight = 0
    private var separatedSpaceWidth = 0
    private var buttonLayoutResourceId = 0

    /**
     * Button click listener.
     */
    interface ButtonClickListener {

        /**
         * Called when a button is clicked.
         *
         * @param view The button's view.
         * @param buttonInfo The button info.
         */
        fun onButtonClicked(view: View, buttonInfo: InAppMessageButtonInfo)
    }

    private var buttonClickListener: ButtonClickListener? = null

    /**
     * Initializes the view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */

    init {
        if (attrs != null) {
            val attributes = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.UrbanAirshipInAppButtonLayout,
                defStyle,
                defResStyle
            )
            stackedSpaceHeight = attributes.getDimensionPixelSize(
                R.styleable.UrbanAirshipInAppButtonLayout_urbanAirshipStackedSpaceHeight,
                0
            )
            separatedSpaceWidth = attributes.getDimensionPixelSize(
                R.styleable.UrbanAirshipInAppButtonLayout_urbanAirshipSeparatedSpaceWidth,
                0
            )
            buttonLayoutResourceId = attributes.getResourceId(
                R.styleable.UrbanAirshipInAppButtonLayout_urbanAirshipButtonLayoutResourceId,
                0
            )
            attributes.recycle()
        }
    }

    /**
     * Sets the button click listener.
     *
     * @param buttonClickListener The button click listener.
     */
    fun setButtonClickListener(buttonClickListener: ButtonClickListener?) {
        this.buttonClickListener = buttonClickListener
    }

    /**
     * Sets the buttons.
     *
     * @param layout The button layout.
     * @param buttonInfos The list of button infos.
     */
    fun setButtons(layout: InAppMessageButtonLayoutType, buttonInfos: List<InAppMessageButtonInfo>) {
        removeAllViews()
        orientation = if (layout == InAppMessageButtonLayoutType.STACKED) VERTICAL else HORIZONTAL
        isMeasureWithLargestChildEnabled = true
        for (i in buttonInfos.indices) {
            var radiusFlag = 0
            val buttonInfo = buttonInfos[i]
            if (layout == InAppMessageButtonLayoutType.JOINED) {
                if (i == 0) {
                    radiusFlag = BorderRadius.LEFT
                } else if (i == buttonInfos.size - 1) {
                    radiusFlag = BorderRadius.RIGHT
                }
            } else {
                radiusFlag = BorderRadius.ALL
            }

            val button =
                LayoutInflater.from(context).inflate(buttonLayoutResourceId, this, false) as Button
            InAppViewUtils.applyButtonInfo(button, buttonInfo, radiusFlag)
            if (layout == InAppMessageButtonLayoutType.STACKED) {
                val params = LayoutParams(LayoutParams.MATCH_PARENT, 0)
                params.weight = 1f
                button.layoutParams = params
                if (i > 0) {
                    params.setMargins(0, stackedSpaceHeight, 0, 0)
                }
            } else {
                val params = LayoutParams(0, LayoutParams.MATCH_PARENT)
                params.weight = 1f
                button.layoutParams = params
                if (layout != InAppMessageButtonLayoutType.JOINED && i > 0) {
                    params.setMargins(separatedSpaceWidth, 0, 0, 0)
                    params.marginStart = separatedSpaceWidth
                }
            }
            addView(button)
            button.setOnClickListener { view ->
                buttonClickListener?.onButtonClicked(view, buttonInfo)
            }
        }
        requestLayout()
    }
}
