/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.model.LabelButtonModel;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.NonNull;

/** Base event. */
public abstract class Event {
    /** Event types. */
    public enum Type {
        /** LabelButton and ImageButton clicks. */
        BUTTON_CLICK,
        /** Pager initialization event. */
        PAGER_INIT,
        /** Pager scroll events. */
        PAGER_SCROLL

        // TODO: add form event types
    }

    @NonNull
    private final Type type;

    private Event(@NonNull Type type) {
        this.type = type;
    }

    /**
     * Returns the {@code Type} of this event.
     *
     * @return the event type.
     */
    @NonNull
    public Type getType() {
        return type;
    }

    /** Event emitted on LabelButton and ImageButton clicks. */
    public static final class ButtonClick extends Event {
        @NonNull private final String identifier;
        @NonNull private final List<ButtonClickBehaviorType> behavior;
        @NonNull private final List<JsonMap> actions;

        /**
         * Constructs a {@code ButtonClick} event from a {@link LabelButtonModel} or {@link ImageButtonModel}.
         *
         * @param model The {@code ButtonModel} that received the click.
         */
        public ButtonClick(@NonNull ButtonModel model) {
            super(Type.BUTTON_CLICK);
            this.identifier = model.getIdentifier();
            this.behavior = model.getButtonClickBehaviors();
            this.actions = model.getActions();
        }

        /**
         * Gets the button identifier.
         *
         * @return The button ID.
         */
        @NonNull
        public String getButtonIdentifier() {
            return identifier;
        }

        /**
         * Gets the button behavior.
         *
         * @return The {@code ButtonBehavior}, or {@code null} if unset.
         */
        @NonNull
        public List<ButtonClickBehaviorType> getButtonClickBehaviors() {
            return behavior;
        }

        /**
         * Gets the button actions.
         *
         * @return A {@code JsonMap} of actions, or {@code null} if unset.
         */
        @NonNull
        public List<JsonMap> getActions() {
            return actions;
        }
    }

    /** Event emitted by Pager views on scroll to the next or previous page. */
    public static final class PagerScroll extends Event {
        private final int position;

        /**
         * Constructs a {@code PagerScroll} event.
         *
         * @param position The position of the item being displayed.
         */
        public PagerScroll(int position) {
            super(Type.PAGER_SCROLL);
            this.position = position;
        }

        /**
         * Gets the position of the item being displayed.
         *
         * @return The carousel position.
         */
        public int getPosition() {
            return position;
        }
    }

    /** Event emitted by Pager views, announcing their size and current position. */
    public static final class PagerInit extends Event {
        private final int size;
        private final int position;

        /**
         * Constructs a {@code PagerInit} event.
         *
         * @param position The current position of the carousel.
         */
        public PagerInit(int size, int position) {
            super(Type.PAGER_INIT);
            this.size = size;
            this.position = position;
        }

        /**
         * Gets the number of items in the Pager view.
         *
         * @return The number of Pager items.
         */
        public int getSize() {
            return size;
        }

        /**
         * Gets the position of the item being displayed.
         *
         * @return The current Pager position.
         */
        public int getPosition() {
            return position;
        }
    }
}
