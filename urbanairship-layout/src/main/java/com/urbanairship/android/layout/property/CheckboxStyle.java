/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.android.layout.property.Image.Icon;
import com.urbanairship.android.layout.shape.Shape;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CheckboxStyle extends ToggleStyle {

    @NonNull
    private final Bindings bindings;

    public CheckboxStyle(@NonNull Bindings bindings) {
        super(ToggleType.CHECKBOX);

        this.bindings = bindings;
    }

    @NonNull
    public static CheckboxStyle fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap bindingsJson = json.opt("bindings").optMap();
        Bindings bindings = Bindings.fromJson(bindingsJson);

        return new CheckboxStyle(bindings);
    }

    public static class Bindings {
        @NonNull
        private final Binding selected;
        @NonNull
        private final Binding unselected;

        Bindings(@NonNull Binding selected, @NonNull Binding unselected) {
            this.selected = selected;
            this.unselected = unselected;
        }

        public static Bindings fromJson(@NonNull JsonMap json) throws JsonException {
            JsonMap selectedJson = json.opt("selected").optMap();
            JsonMap unselectedJson = json.opt("unselected").optMap();

            Binding selected = Binding.fromJson(selectedJson);
            Binding unselected = Binding.fromJson(unselectedJson);

            return new Bindings(selected, unselected);
        }

        @NonNull
        public Binding getSelected() {
            return selected;
        }

        @NonNull
        public Binding getUnselected() {
            return unselected;
        }
    }

    public static class Binding {
        @NonNull
        private final List<Shape> shapes;
        @Nullable
        private final Icon icon;

        public Binding(@NonNull List<Shape> shapes, @Nullable Icon icon) {
            this.shapes = shapes;
            this.icon = icon;
        }

        @NonNull
        public static Binding fromJson(@NonNull JsonMap json) throws JsonException {
            JsonList shapesJson = json.opt("shapes").optList();
            JsonMap iconJson = json.opt("icon").optMap();

            List<Shape> shapes = new ArrayList<>();
            for (int i = 0; i < shapesJson.size(); i++) {
                JsonMap shapeJson = shapesJson.get(i).optMap();
                Shape shape = Shape.fromJson(shapeJson);
                shapes.add(shape);
            }
            Icon icon = iconJson.isEmpty() ? null : Icon.fromJson(iconJson);

            return new Binding(shapes, icon);
        }

        @NonNull
        public List<Shape> getShapes() {
            return shapes;
        }

        @Nullable
        public Icon getIcon() {
            return icon;
        }
    }

    @NonNull
    public Bindings getBindings() {
        return bindings;
    }
}
