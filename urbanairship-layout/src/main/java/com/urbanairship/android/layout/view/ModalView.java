/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.display.ModalDisplay;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.ModalPlacement;
import com.urbanairship.android.layout.property.ModalPlacementSelector;
import com.urbanairship.android.layout.property.Orientation;
import com.urbanairship.android.layout.property.Position;
import com.urbanairship.android.layout.property.Size;
import com.urbanairship.android.layout.property.WindowSize;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.ResourceUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

public class ModalView extends ConstraintLayout {
    private ModalDisplay modal;
    private ViewGroup modalFrame;
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
        setId(generateViewId());
        windowTouchSlop = ViewConfiguration.get(getContext()).getScaledWindowTouchSlop();

        modalFrame = new FrameLayout(context);
        modalFrame.setId(generateViewId());
        modalFrame.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        modalFrame.setElevation(ResourceUtils.dpToPx(getContext(), 16));

        addView(modalFrame);
    }

    @NonNull
    public static ModalView create(@NonNull Context context, @NonNull ModalDisplay modal) {
        ModalView view = new ModalView(context);
        view.setModal(modal);
        return view;
    }

    public void setModal(@NonNull ModalDisplay modal) {
        this.modal = modal;
        configureModal();
    }

    @NonNull
    public ModalDisplay getModal() {
        return modal;
    }

    public void configureModal() {
        ModalDisplay.Info info = modal.getInfo();

        ModalPlacement placement = determinePlacement(info);
        setPlacement(placement);

        View containerView = Layout.view(getContext(), modal.getLayout());
        // TODO: make sure this makes sense for all combos of modal placement / sizes...
        containerView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        modalFrame.addView(containerView);
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

    @NonNull
    private ModalPlacement determinePlacement(@NonNull ModalDisplay.Info info) {
        List<ModalPlacementSelector> placementSelectors = info.getPlacementSelectors();
        ModalPlacement defaultPlacement = info.getDefaultPlacement();

        if (placementSelectors == null || placementSelectors.isEmpty()) {
            return defaultPlacement;
        }

        Orientation orientation = ResourceUtils.getOrientation(getContext());
        WindowSize windowSize = ResourceUtils.getWindowSize(getContext());

        // TODO: no idea if this is the actual logic we want here...
        // Collect placement selectors that match either both or only one of the criteria. If any selectors match
        // both, choose the first one. If any selectors match one, the first match will be used if no selectors
        // match both. If there are no full or partial matches, use the default placement.
        List<ModalPlacementSelector> bothMatch = new ArrayList<>();
        List<ModalPlacementSelector> oneMatch = new ArrayList<>();
        for (ModalPlacementSelector selector : placementSelectors) {
            if (selector.getOrientation() == orientation && selector.getWindowSize() == windowSize) {
                bothMatch.add(selector);
            } else if (selector.getOrientation() == orientation || selector.getWindowSize() == windowSize) {
                oneMatch.add(selector);
            }
        }

        ModalPlacementSelector bestSelector = null;
        if (!bothMatch.isEmpty()) {
            bestSelector = bothMatch.get(0);
        } else if (!oneMatch.isEmpty()) {
            bestSelector = oneMatch.get(0);
        }

        return bestSelector != null ? bestSelector.getPlacement() : defaultPlacement;
    }

    private void setPlacement(@NonNull ModalPlacement placement) {
        Size size = placement.getSize();
        Position position = placement.getPosition();
        Margin margin = placement.getMargin();
        @ColorInt Integer backgroundColor = placement.getBackgroundColor();

        int viewId = modalFrame.getId();
        ConstraintSet constraints =
            ConstraintSetBuilder.newBuilder(getContext())
                .constrainWithinParent(viewId)
                .size(size, viewId)
                .position(position, viewId)
                .margin(margin, viewId)
                .build();

        if (backgroundColor != null) {
            modalFrame.setBackgroundColor(backgroundColor);
        } else {
            modalFrame.setBackgroundColor(ResourceUtils.getColorAttr(getContext(), android.R.attr.colorBackground));
        }

        constraints.applyTo(this);
    }

    private boolean isTouchOutside(@NonNull MotionEvent event) {
        // Get the bounds of the modal
        Rect r = new Rect();
        modalFrame.getHitRect(r);
        // Expand the bounds by the amount of slop needed to be considered an outside touch
        r.inset(-windowTouchSlop, -windowTouchSlop);

        return !r.contains((int) event.getX(), (int) event.getY());
    }
}
