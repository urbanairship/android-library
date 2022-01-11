/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class WebViewEvent extends Event {

    @NonNull
    private final LayoutData state;

    public WebViewEvent(@NonNull EventType type, @Nullable LayoutData state) {
        super(type);
        this.state = state != null ? state : new LayoutData(null, null);
    }

    @NonNull
    public LayoutData getState() {
        return state;
    }

    public abstract WebViewEvent overrideState(@NonNull FormInfo formInfo);
    public abstract WebViewEvent overrideState(@NonNull PagerData pagerData);

    protected LayoutData copyState(@NonNull FormInfo formInfo) {
        return state.withFormInfo(formInfo);
    }

    protected LayoutData copyState(@NonNull PagerData data) {
        return state.withPagerData(data);
    }

    /** Event bubbled up from WebViews when a close action is triggered via the JS interface. */
    public static final class Close extends WebViewEvent {
        public Close() {
            this(null);
        }

        private Close(@Nullable LayoutData state) {
            super(EventType.WEBVIEW_CLOSE, state);
        }

        @Override
        public WebViewEvent overrideState(@NonNull FormInfo formInfo) {
            return new Close(copyState(formInfo));
        }

        @Override
        public WebViewEvent overrideState(@NonNull PagerData pagerData) {
            return new Close(copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "WebViewEvent.Close{" +
                "state=" + getState() +
                '}';
        }
    }
}
