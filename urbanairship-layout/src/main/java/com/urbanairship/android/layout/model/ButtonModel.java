/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.property.ButtonEnableBehaviorType;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class ButtonModel extends BaseModel implements Accessible, Identifiable {
    @NonNull
    private final String identifier;
    @NonNull
    private final List<ButtonClickBehaviorType> buttonClickBehaviors;
    @NonNull
    private final List<JsonMap> actions;
    @NonNull
    private final List<ButtonEnableBehaviorType> enableBehaviors;
    @Nullable
    private final String contentDescription;

    @Nullable
    private Listener viewListener = null;
    private boolean isEnabled = true;

    protected ButtonModel(
        @NonNull ViewType type,
        @NonNull String identifier,
        @NonNull List<ButtonClickBehaviorType> buttonClickBehaviors,
        @NonNull List<JsonMap> actions,
        @NonNull List<ButtonEnableBehaviorType> enableBehaviors,
        @Nullable Color backgroundColor,
        @Nullable Border border,
        @Nullable String contentDescription
    ) {
        super(type, backgroundColor, border);

        this.identifier = identifier;
        this.buttonClickBehaviors = buttonClickBehaviors;
        this.actions = actions;
        this.enableBehaviors = enableBehaviors;
        this.contentDescription = contentDescription;
    }

    public static List<ButtonClickBehaviorType> buttonClickBehaviorsFromJson(@NonNull JsonMap json) {
        JsonList clickBehaviorsList = json.opt("button_click").optList();
        return ButtonClickBehaviorType.fromList(clickBehaviorsList);
    }

    public static List<JsonMap> actionsFromJson(@NonNull JsonMap json) {
        JsonList actionsJson = json.opt("actions").optList();
        if (actionsJson.isEmpty()) {
            return Collections.emptyList();
        }

        List<JsonMap> actions = new ArrayList<>(actionsJson.size());
        for (JsonValue value : actionsJson) {
            actions.add(value.optMap());
        }
        return actions;
    }

    public static List<ButtonEnableBehaviorType> buttonEnableBehaviorsFromJson(@NonNull JsonMap json) {
        JsonList enableBehaviorsList = json.opt("enabled").optList();
        return ButtonEnableBehaviorType.fromList(enableBehaviorsList);
    }

    @Override
    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @Override
    @Nullable
    public String getContentDescription() {
        return contentDescription;
    }

    @NonNull
    public List<ButtonClickBehaviorType> getButtonClickBehaviors() {
        return buttonClickBehaviors;
    }

    @NonNull
    public List<JsonMap> getActions() {
        return actions;
    }

    @NonNull
    public List<ButtonEnableBehaviorType> getButtonEnableBehaviors() {
        return enableBehaviors;
    }

    public void setViewListener(@Nullable Listener viewListener) {
        this.viewListener = viewListener;

        if (viewListener != null) {
            viewListener.setEnabled(isEnabled());
        }
    }

    private boolean hasActions() {
        return actions.size() > 0;
    }

    private boolean isEnabled() {
        return enableBehaviors.isEmpty() || isEnabled;
    }

    public void onClick() {
        for (ButtonClickBehaviorType behavior : buttonClickBehaviors) {
            bubbleEvent(ButtonEvent.fromBehavior(behavior, this));
        }

        if (hasActions()) {
            bubbleEvent(new ButtonEvent.Actions(this));
        }
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event.getType());
        switch (event.getType()) {
            case FORM_VALIDATION:
               return handleFormSubmitUpdate((FormEvent.ValidationUpdate) event);
            case PAGER_INIT:
                PagerEvent.Init init = (PagerEvent.Init) event;
                return handlePagerScroll(init.hasNext(), init.hasPrevious());
            case PAGER_SCROLL:
                PagerEvent.Scroll scroll = (PagerEvent.Scroll) event;
                return handlePagerScroll(scroll.hasNext(), scroll.hasPrevious());
            default:
                return super.onEvent(event);
        }
    }

    private boolean handleFormSubmitUpdate(FormEvent.ValidationUpdate update) {
        if (enableBehaviors.contains(ButtonEnableBehaviorType.FORM_VALIDATION)) {
            isEnabled = update.isValid();
            if (viewListener != null) {
                viewListener.setEnabled(update.isValid());
            }
            return true;
        }

        return false;
    }

    private boolean handlePagerScroll(boolean hasNext, boolean hasPrevious) {
        if (enableBehaviors.contains(ButtonEnableBehaviorType.PAGER_NEXT)) {
            isEnabled = hasNext;
            if (viewListener != null) {
                viewListener.setEnabled(hasNext);
            }
        }
        if (enableBehaviors.contains(ButtonEnableBehaviorType.PAGER_PREVIOUS)) {
            isEnabled = hasPrevious;
            if (viewListener != null) {
                viewListener.setEnabled(hasPrevious);
            }
        }

        // Always return false so other views can react to pager scroll events.
        return false;
    }

    public interface Listener {
        void setEnabled(boolean isEnabled);
    }
}
