/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class WebViewEvent extends Event {

    public WebViewEvent(@NonNull EventType type) {
        super(type);
    }

    /** Event bubbled up from WebViews when a close action is triggered via the JS interface. */
    public static final class Close extends WebViewEvent {
        public Close() {
            this(null);
        }

        private Close(@Nullable LayoutData state) {
            super(EventType.WEBVIEW_CLOSE);
        }

    }
}
