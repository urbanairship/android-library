/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.ScoreEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ScoreStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.Accessible.contentDescriptionFromJson;
import static com.urbanairship.android.layout.model.Validatable.requiredFromJson;
import static com.urbanairship.android.layout.reporting.AttributeName.attributeNameFromJson;

/**
 * Model for Score views.
 *
 * Must be a descendant of {@code FormController} or {@code NpsFormController}.
 *
 * Numbers will have an equal height/width and will scale to fill the container.
 * With auto width, the container will be up to 320dp. The top-level background and border apply to the entire widget.
 */
public class ScoreModel extends BaseModel implements Identifiable, Accessible, Validatable {
    @NonNull
    private final String identifier;
    @NonNull
    private final ScoreStyle style;
    @Nullable
    private final AttributeName attributeName;
    private final boolean isRequired;
    @Nullable
    private final String contentDescription;

    @Nullable
    private Integer selectedScore = null;

    public ScoreModel(
        @NonNull String identifier,
        @NonNull ScoreStyle style,
        @Nullable AttributeName attributeName,
        boolean isRequired,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border) {
        super(ViewType.SCORE, backgroundColor, border);

        this.identifier = identifier;
        this.style = style;
        this.attributeName = attributeName;
        this.isRequired = isRequired;
        this.contentDescription = contentDescription;
    }

    @NonNull
    public static ScoreModel fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = Identifiable.identifierFromJson(json);
        JsonMap styleJson = json.opt("style").optMap();
        ScoreStyle style = ScoreStyle.fromJson(styleJson);
        AttributeName attributeName = attributeNameFromJson(json);
        boolean required = requiredFromJson(json);
        String contentDescription = contentDescriptionFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new ScoreModel(identifier, style, attributeName, required, contentDescription, backgroundColor, border);
    }

    @Override
    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @Override
    @Nullable
    public String getContentDescription() {
        return contentDescription;
    }

    @Override
    public boolean isRequired() {
        return isRequired;
    }

    @NonNull
    public ScoreStyle getStyle() {
        return style;
    }

    @Nullable
    public Integer getSelectedScore() {
        return selectedScore;
    }

    @Override
    public boolean isValid() {
        return (selectedScore != null && selectedScore > -1) || !isRequired;
    }

    public void onConfigured() {
        bubbleEvent(new ScoreEvent.Init(identifier, isValid()), LayoutData.empty());
    }

    public void onAttachedToWindow() {
        bubbleEvent(new Event.ViewAttachedToWindow(this), LayoutData.empty());
    }

    public void onScoreChange(int score) {
        selectedScore = score;

        bubbleEvent(new FormEvent.DataChange(new FormData.Score(identifier, score), isValid(), attributeName, JsonValue.wrap(score)), LayoutData.empty());
    }
}
