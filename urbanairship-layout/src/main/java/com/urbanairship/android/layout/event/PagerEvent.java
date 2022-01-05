/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.PagerModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class PagerEvent extends Event {
    private final long time;

    public PagerEvent(@NonNull EventType type, long time) {
        super(type);
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    /** Event emitted by Pager views, announcing their size and current position. */
    public static final class Init extends PagerEvent {
        private final int size;
        private final int pageIndex;
        private final String pageId;
        private final boolean hasNext;
        private final boolean hasPrev;

        public Init(@NonNull PagerModel model, int pageIndex, @NonNull String pageId, long time) {
            super(EventType.PAGER_INIT, time);
            this.size = model.getItems().size();
            this.pageIndex = pageIndex;
            this.pageId = pageId;
            this.hasNext = pageIndex < size - 1;
            this.hasPrev = pageIndex > 0;
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
         * @return The current page index.
         */
        public int getPageIndex() {
            return pageIndex;
        }

        /**
         * Gets the page Id of the item being displayed.
         *
         * @return The current page Id.
         */
        @NonNull
        public String getPageId() {
            return pageId;
        }


        /**
         * Returns whether or not the pager has a next page that can be scrolled to.
         * @return {@code true} if the pager has a next page.
         */
        public boolean hasNext() {
            return hasNext;
        }

        /**
         * Returns whether or not the pager has a previous page that can be scrolled to.
         * @return {@code true} if the pager has a previous page.
         */
        public boolean hasPrevious() {
            return hasPrev;
        }

        @Override
        public String toString() {
            return "Init{" +
                    "size=" + size +
                    ", pageIndex=" + pageIndex +
                    ", pageId='" + pageId + '\'' +
                    ", hasNext=" + hasNext +
                    ", hasPrev=" + hasPrev +
                    '}';
        }

    }

    /** Event emitted by Pager indicator views on init. */
    public static final class IndicatorInit extends Event.ViewInit {
        public IndicatorInit(@NonNull BaseModel model) {
            super(model);
        }

        @Override
        @NonNull
        public String toString() {
            return "PagerEvent.IndicatorInit{}";
        }
    }

    /** Event emitted by Pager views on scroll to the next or previous page. */
    public static final class Scroll extends PagerEvent {
        private final int pageIndex;
        private final String pageId;
        private final int previousPageIndex;
        private final String previousPageId;
        private final boolean hasNext;
        private final boolean hasPrev;
        private final boolean isInternalScroll;

        public Scroll(@NonNull PagerModel model, int pageIndex, @NonNull String pageId, int previousPageIndex, @NonNull String previousPageId, boolean isInternalScroll, long time) {
            super(EventType.PAGER_SCROLL, time);
            this.pageIndex = pageIndex;
            this.pageId = pageId;
            this.previousPageIndex = previousPageIndex;
            this.previousPageId = previousPageId;
            this.hasNext = pageIndex < model.getItems().size() - 1;
            this.hasPrev = pageIndex > 0;
            this.isInternalScroll = isInternalScroll;
        }

        /**
         * Gets the position of the item being displayed.
         *
         * @return The carousel position.
         */
        public int getPageIndex() {
            return pageIndex;
        }

        /** Gets the position of the previously displayed item. */
        public int getPreviousPageIndex() {
            return previousPageIndex;
        }

        /** Gets the current page Id **/
        @NonNull
        public String getPageId() {
            return pageId;
        }

        /** Gets the previous page Id **/
        @NonNull
        public String getPreviousPageId() {
            return previousPageId;
        }

        /**
         * Returns whether or not the pager has a next page that can be scrolled to.
         * @return {@code true} if the pager has a next page.
         */
        public boolean hasNext() {
            return hasNext;
        }

        /**
         * Returns whether or not the pager has a previous page that can be scrolled to.
         * @return {@code true} if the pager has a previous page.
         */
        public boolean hasPrevious() {
            return hasPrev;
        }

        /** Returns true if this scroll event was the result of programmatic scrolling. */
        public boolean isInternal() {
            return isInternalScroll;
        }

        @Override
        public String toString() {
            return "Scroll{" +
                    "pageIndex=" + pageIndex +
                    ", pageId='" + pageId + '\'' +
                    ", previousPageIndex=" + previousPageIndex +
                    ", previousPageId='" + previousPageId + '\'' +
                    ", hasNext=" + hasNext +
                    ", hasPrev=" + hasPrev +
                    ", isInternalScroll=" + isInternalScroll +
                    '}';
        }
    }
}
