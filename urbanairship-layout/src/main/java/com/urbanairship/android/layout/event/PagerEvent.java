/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.PagerModel;

import androidx.annotation.NonNull;

public abstract class PagerEvent extends Event {

    public PagerEvent(@NonNull EventType type) {
        super(type);
    }

    /** Event emitted by Pager views, announcing their size and current position. */
    public static final class Init extends Event {
        private final int size;
        private final int position;

        /**
         * Constructs a {@code PagerInit} event.
         *
         * @param position The current position of the carousel.
         */
        public Init(@NonNull PagerModel model, int position) {
            super(EventType.PAGER_INIT);
            this.size = model.getItems().size();
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

    /** Event emitted by Pager indicator views on init. */
    public static final class IndicatorInit extends Event.ViewInit {
        public IndicatorInit(@NonNull BaseModel model) {
            super(model);
        }
    }

    /** Event emitted by Pager views on scroll to the next or previous page. */
    public static final class Scroll extends PagerEvent {
        private final int position;

        /**
         * Constructs a {@code PagerScroll} event.
         *
         * @param position The position of the item being displayed.
         */
        public Scroll(int position) {
            super(EventType.PAGER_SCROLL);
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
}
