/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.display;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.model.LayoutModel;
import com.urbanairship.android.layout.property.ButtonBehavior;
import com.urbanairship.android.layout.property.ModalPlacement;
import com.urbanairship.android.layout.property.ModalPlacementSelector;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ModalDisplay implements Display, EventListener {
    @NonNull
    private final Info info;
    @NonNull
    private final LayoutModel layout;

    @Nullable
    private Listener listener = null;

    @NonNull
    public static ModalDisplay fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap modalJson = json.opt("modal").optMap();
        JsonMap layoutJson = json.opt("layout").optMap();

        Info info = Info.fromJson(modalJson);
        LayoutModel layout = LayoutModel.fromJson(layoutJson);

        return new ModalDisplay(info, layout);
    }

    public ModalDisplay(@NonNull Info info, @NonNull LayoutModel layout) {
        this.info = info;
        this.layout = layout;

        layout.addListener(this);
    }

    @NonNull
    public LayoutModel getLayout() {
        return layout;
    }

    @NonNull
    public Info getInfo() {
        return info;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        if (event.getType() == Event.Type.BUTTON_CLICK) {
            ButtonBehavior behavior = ((Event.ButtonClick) event).getBehavior();
            if (behavior != null) {
                switch (behavior) {
                    case CANCEL:
                        if (listener != null) { listener.onCancel(); }
                        return true;
                    case DISMISS:
                        if (listener != null) { listener.onDismiss(); }
                        return true;
                }
                // TODO: handle button actions here, or emit to activity?
            }
        }

        Logger.verbose("%s - onEvent: trickle down %s", "ModalDisplay", event.getType().name());
        if (getLayout().trickleEvent(event)) { return true; }

        Logger.debug("%s - onEvent: %s unhandled!" + event.getType().name());
        return true;
    }

    public static class Info {
        @NonNull
        private final ModalPlacement defaultPlacement;
        @Nullable
        private final List<ModalPlacementSelector> placementSelectors;

        Info(@NonNull ModalPlacement defaultPlacement, @Nullable List<ModalPlacementSelector> placementSelectors) {
            this.defaultPlacement = defaultPlacement;
            this.placementSelectors = placementSelectors;
        }

        @NonNull
        public static Info fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap defaultPlacementJson = json.opt("defaultPlacement").optMap();
            JsonList placementSelectorsJson = json.opt("placementSelectors").optList();

            ModalPlacement defaultPlacement = ModalPlacement.fromJson(defaultPlacementJson);
            List<ModalPlacementSelector> placementSelectors =
                placementSelectorsJson.isEmpty() ? null : ModalPlacementSelector.fromJsonList(placementSelectorsJson);

            return new Info(defaultPlacement, placementSelectors);
        }

        @NonNull
        public ModalPlacement getDefaultPlacement() {
            return defaultPlacement;
        }

        @Nullable
        public List<ModalPlacementSelector> getPlacementSelectors() {
            return placementSelectors == null ? null : new ArrayList<>(placementSelectors);
        }
    }

    public interface Listener {
        void onCancel();
        void onDismiss();
    }
}
