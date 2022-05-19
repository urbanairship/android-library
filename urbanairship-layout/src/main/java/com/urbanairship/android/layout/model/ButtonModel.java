/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.PagerEvent;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.property.ButtonEnableBehaviorType;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class ButtonModel extends BaseModel implements Accessible, Identifiable {
    @NonNull
    private final String identifier;
    @NonNull
    private final List<ButtonClickBehaviorType> buttonClickBehaviors;
    @NonNull
    private final Map<String, JsonValue> actions;
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
        @NonNull Map<String, JsonValue> actions,
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

    public static List<ButtonClickBehaviorType> buttonClickBehaviorsFromJson(@NonNull JsonMap json) throws JsonException {
        JsonList clickBehaviorsList = json.opt("button_click").optList();
        return ButtonClickBehaviorType.fromList(clickBehaviorsList);
    }

    public static Map<String, JsonValue> actionsFromJson(@NonNull JsonMap json) {
        return json.opt("actions").optMap().getMap();
    }

    public static List<ButtonEnableBehaviorType> buttonEnableBehaviorsFromJson(@NonNull JsonMap json) throws JsonException {
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
    public Map<String, JsonValue> getActions() {
        return actions;
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
        LayoutData layoutData = LayoutData.button(identifier);

        // Report button tap event.
        bubbleEvent(new ReportingEvent.ButtonTap(identifier), layoutData);

        if (hasActions()) {
            bubbleEvent(new ButtonEvent.Actions(this), layoutData);
        }

        for (ButtonClickBehaviorType behavior : buttonClickBehaviors) {
            try {
                bubbleEvent(ButtonEvent.fromBehavior(behavior, this), layoutData);
            } catch (JsonException e) {
                Logger.warn(e, "Skipping button click behavior!");
            }
        }

        // Note: Button dismiss events are reported at the top level when handled.
        // We can't send them directly from here because we need to include
        // the display time, which is tracked by the hosting Activity.
    }

    @NonNull
    public abstract String reportingDescription();

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
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
                return super.onEvent(event, layoutData);
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
