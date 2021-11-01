/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.model.CarouselIndicatorModel;
import com.urbanairship.android.layout.model.CarouselModel;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.property.ButtonBehavior;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Base event. */
public abstract class Event {
    /** Event types. */
    public enum Type {
        /** Button and ImageButton clicks. */
        BUTTON_CLICK,
        /** Carousel initialization event. */
        CAROUSEL_INIT,
        /** Carousel scroll events. */
        CAROUSEL_SCROLL,
        /** Carousel indicator clicks. */
        CAROUSEL_INDICATOR_CLICK
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

    /** Event emitted on Button and ImageButton clicks. */
    public static final class ButtonClick extends Event {
        @NonNull private final String buttonId;
        @Nullable private final ButtonBehavior behavior;
        @Nullable private final JsonMap actions;

        /**
         * Constructs a {@code ButtonClick} event from a {@link ButtonModel}.
         *
         * @param model The {@code ButtonModel} that received the click.
         */
        public ButtonClick(@NonNull ButtonModel model) {
            super(Type.BUTTON_CLICK);
            this.buttonId = model.getId();
            this.behavior = model.getBehavior();
            this.actions = model.getActions();
        }

        /**
         * Constructs a {@code ButtonClick} event from a {@link ImageButtonModel}.
         *
         * @param model The {@code ImageButtonModel} that received the click.
         */
        public ButtonClick(@NonNull ImageButtonModel model) {
            super(Type.BUTTON_CLICK);
            this.buttonId = model.getId();
            this.behavior = model.getBehavior();
            this.actions = model.getActions();
        }

        /**
         * Gets the button identifier.
         *
         * @return The button ID.
         */
        @NonNull
        public String getButtonId() {
            return buttonId;
        }

        /**
         * Gets the button behavior.
         *
         * @return The {@code ButtonBehavior}, or {@code null} if unset.
         */
        @Nullable
        public ButtonBehavior getBehavior() {
            return behavior;
        }

        /**
         * Gets the button actions.
         *
         * @return A {@code JsonMap} of actions, or {@code null} if unset.
         */
        @Nullable
        public JsonMap getActions() {
            return actions;
        }
    }

    /** Event emitted by Carousel views on scroll to the next or previous page. */
    public static final class CarouselScroll extends Event {
        @NonNull private final String carouselId;
        private final int position;

        /**
         * Constructs a {@code CarouselScroll} event.
         *
         * @param model The {@code CarouselModel} that received the scroll.
         * @param position The position of the item being displayed.
         */
        public CarouselScroll(@NonNull CarouselModel model, int position) {
            super(Type.CAROUSEL_SCROLL);
            this.carouselId = model.getIdentifier();
            this.position = position;
        }

        /**
         * Gets the carousel identifier.
         *
         * @return The carousel ID.
         */
        @NonNull
        public String getCarouselId() {
            return carouselId;
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

    /** Event emitted on Carousel Indicator dot clicks. */
    public static final class CarouselIndicatorClick extends Event {
        @NonNull private final String carouselId;
        private final int position;

        /**
         * Constructs a {@code CarouselIndicatorClick} event.
         *
         * @param model The {@code CarouselIndicatorModel} that received the click.
         * @param position The position of the indicator dot that was clicked.
         */
        public CarouselIndicatorClick(@NonNull CarouselIndicatorModel model, int position) {
            super(Type.CAROUSEL_INDICATOR_CLICK);
            this.carouselId = model.getIdentifier();
            this.position = position;
        }

        /**
         * Gets the carousel identifier.
         *
         * @return The carousel ID.
         */
        @NonNull
        public String getCarouselId() {
            return carouselId;
        }

        /**
         * Gets the position of the indicator dot that was clicked.
         *
         * @return The position of the indicator click.
         */
        public int getPosition() {
            return position;
        }
    }

    /** Event emitted by Carousel views, announcing their ID, size, and current position. */
    public static final class CarouselInit extends Event {
        @NonNull private final String carouselId;
        private final int size;
        private final int position;

        /**
         * Constructs a {@code CarouselInit} event.
         *
         * @param model The {@code CarouselModel} that is initializing.
         * @param position The current position of the carousel.
         */
        public CarouselInit(@NonNull CarouselModel model, int position) {
            super(Type.CAROUSEL_INIT);
            this.carouselId = model.getIdentifier();
            this.size = model.getItems().size();
            this.position = position;
        }

        /**
         * Gets the carousel identifier.
         *
         * @return The carousel ID.
         */
        @NonNull
        public String getCarouselId() {
            return carouselId;
        }

        /**
         * Gets the number of items in the Carousel view.
         *
         * @return The number of Carousel items.
         */
        public int getSize() {
            return size;
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
}
