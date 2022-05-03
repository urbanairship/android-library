/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.ModalPresentation;
import com.urbanairship.android.layout.property.ConstrainedSize;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.ModalPlacement;
import com.urbanairship.android.layout.property.Position;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.view.ViewCompat;

public class ModalView extends ConstraintLayout {
    private BaseModel model;
    private ModalPresentation presentation;
    private Environment environment;

    private ConstrainedFrameLayout modalFrame;
    private View containerView;
    private int windowTouchSlop;

    @Nullable private OnClickListener clickOutsideListener = null;

    public ModalView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ModalView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ModalView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void init(@NonNull Context context) {
        windowTouchSlop = ViewConfiguration.get(context).getScaledWindowTouchSlop();
    }

    @NonNull
    public static ModalView create(
        @NonNull Context context,
        @NonNull BaseModel model,
        @NonNull ModalPresentation presentation,
        @NonNull Environment environment
    ) {
        ModalView view = new ModalView(context);

        view.setModal(model, presentation, environment);
        return view;
    }

    public void setModal(
        @NonNull BaseModel model,
        @NonNull ModalPresentation presentation,
        @NonNull Environment environment
    ) {
        this.model = model;
        this.presentation = presentation;
        this.environment = environment;

        setId(model.getViewId());
        configureModal();
    }

    public void configureModal() {
        ModalPlacement placement = presentation.getResolvedPlacement(getContext());

        ConstrainedSize size = placement.getSize();
        Position position = placement.getPosition();
        Margin margin = placement.getMargin();
        @ColorInt Integer shadeColor = placement.getShadeColor() != null
            ? placement.getShadeColor().resolve(getContext()) : null;
        makeFrame(size);

        containerView = Thomas.view(getContext(), model, environment);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        lp.gravity = position != null ? position.getGravity() : Gravity.CENTER;
        if (margin != null) {
            lp.setMargins(margin.getStart(), margin.getTop(), margin.getEnd(), margin.getBottom());
        }
        containerView.setLayoutParams(lp);
        modalFrame.addView(containerView);

        addView(modalFrame);

        int viewId = modalFrame.getId();
        ConstraintSet constraints = ConstraintSetBuilder.newBuilder(getContext())
                                                        .constrainWithinParent(viewId)
                                                        .size(size, viewId)
                                                        .margin(margin, viewId)
                                                        .build();

        if (shadeColor != null) {
            setBackgroundColor(shadeColor);
        }

        constraints.applyTo(this);

        if (environment.isIgnoringSafeAreas()) {
           ViewCompat.setOnApplyWindowInsetsListener(modalFrame, (v, insets) ->
               ViewCompat.dispatchApplyWindowInsets(containerView, insets)
           );
        }
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    public void makeFrame(ConstrainedSize size) {
        modalFrame = new ConstrainedFrameLayout(getContext(), size);
        modalFrame.setId(generateViewId());
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_CONSTRAINT, LayoutParams.MATCH_CONSTRAINT);
        modalFrame.setLayoutParams(params);
        modalFrame.setElevation(ResourceUtils.dpToPx(getContext(), 16));
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_UP:
                if (isTouchOutside(event) && clickOutsideListener != null) {
                    clickOutsideListener.onClick(this);
                    return true;
                }
                break;
        }

        return super.onTouchEvent(event);
    }

    public void setOnClickOutsideListener(OnClickListener listener) {
        clickOutsideListener = listener;
    }

    private boolean isTouchOutside(@NonNull MotionEvent event) {
        // Get the bounds of the modal
        Rect r = new Rect();
        containerView.getHitRect(r);
        // Expand the bounds by the amount of slop needed to be considered an outside touch
        r.inset(-windowTouchSlop, -windowTouchSlop);

        return !r.contains((int) event.getX(), (int) event.getY());
    }
}
