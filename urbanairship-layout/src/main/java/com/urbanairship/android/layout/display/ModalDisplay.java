/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.display;

import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.property.ModalPlacement;
import com.urbanairship.android.layout.property.ModalPlacementSelector;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ModalDisplay implements Display {
    @NonNull
    private final Info info;
    @NonNull
    private final ContainerLayoutModel layout;

    @NonNull
    public static ModalDisplay fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap modalJson = json.opt("modal").optMap();
        JsonMap layoutJson = json.opt("layout").optMap();

        Info info = Info.fromJson(modalJson);
        // TODO: update this to BaseLayoutModel... web has 'layout' as either a LinearLayout or ScrollLayout
        ContainerLayoutModel layout = ContainerLayoutModel.fromJson(layoutJson);

        return new ModalDisplay(info, layout);
    }

    public ModalDisplay(@NonNull Info info, @NonNull ContainerLayoutModel layout) {
        this.info = info;
        this.layout = layout;
    }

    @NonNull
    public ContainerLayoutModel getLayout() {
        return layout;
    }

    @NonNull
    public Info getInfo() {
        return info;
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
}
