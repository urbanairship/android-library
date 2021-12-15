/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.android.layout.shape.Shape;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;

/** Styling info for score views. */
public abstract class ScoreStyle {
    @NonNull
    private final ScoreType type;

    ScoreStyle(@NonNull ScoreType type) {
        this.type = type;
    }

    @NonNull
    public static ScoreStyle fromJson(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();
        switch (ScoreType.from(typeString)) {
            case NUMBER_RANGE:
                return NumberRange.fromJson(json);
        }
        throw new JsonException("Failed to parse ScoreStyle! Unknown type: " + typeString);
    }


    public static class NumberRange extends ScoreStyle {
        private final int start;
        private final int end;
        private final int spacing;
        @NonNull
        private final Bindings bindings;

        public NumberRange(
            int start,
            int end,
            int spacing,
            @NonNull Bindings bindings
        ) {
            super(ScoreType.NUMBER_RANGE);
            this.start = start;
            this.end = end;
            this.spacing = spacing;
            this.bindings = bindings;
        }

        @NonNull
        public static ScoreStyle fromJson(JsonMap json) throws JsonException {
            int start = json.opt("start").getInt(0);
            int end = json.opt("end").getInt(10);
            int spacing = json.opt("spacing").getInt(0);
            JsonMap bindingsJson = json.opt("bindings").optMap();
            Bindings bindings = Bindings.fromJson(bindingsJson);

            return new NumberRange(start, end, spacing, bindings);
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        @Dimension(unit = Dimension.DP)
        public int getSpacing() {
            return spacing;
        }

        @NonNull
        public Bindings getBindings() {
            return bindings;
        }
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

        @NonNull
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
        @NonNull
        private final TextAppearance textAppearance;

        public Binding(@NonNull List<Shape> shapes, @NonNull TextAppearance textAppearance) {
            this.shapes = shapes;
            this.textAppearance = textAppearance;
        }

        public static Binding fromJson(@NonNull JsonMap json) throws JsonException {
            JsonList shapesJson = json.opt("shapes").optList();
            JsonMap textAppearanceJson = json.opt("text_appearance").optMap();

            List<Shape> shapes = new ArrayList<>();
            for (int i = 0; i < shapesJson.size(); i++) {
                JsonMap shapeJson = shapesJson.get(i).optMap();
                Shape shape = Shape.fromJson(shapeJson);
                shapes.add(shape);
            }
            TextAppearance textAppearance = TextAppearance.fromJson(textAppearanceJson);

            return new Binding(shapes, textAppearance);
        }

        @NonNull
        public List<Shape> getShapes() {
            return shapes;
        }

        @NonNull
        public TextAppearance getTextAppearance() {
            return textAppearance;
        }
    }

    @NonNull
    public ScoreType getType() {
        return type;
    }
}
