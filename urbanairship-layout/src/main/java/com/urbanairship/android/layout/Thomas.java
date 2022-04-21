/* Copyright Airship and Contributors */

package com.urbanairship.android.layout;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.urbanairship.android.layout.display.DisplayArgsLoader;
import com.urbanairship.android.layout.display.DisplayException;
import com.urbanairship.android.layout.display.DisplayRequest;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.CheckboxController;
import com.urbanairship.android.layout.model.CheckboxModel;
import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.model.EmptyModel;
import com.urbanairship.android.layout.model.FormController;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.model.LabelButtonModel;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.model.LayoutModel;
import com.urbanairship.android.layout.model.LinearLayoutModel;
import com.urbanairship.android.layout.model.MediaModel;
import com.urbanairship.android.layout.model.ModalPresentation;
import com.urbanairship.android.layout.model.NpsFormController;
import com.urbanairship.android.layout.model.PagerController;
import com.urbanairship.android.layout.model.PagerIndicatorModel;
import com.urbanairship.android.layout.model.PagerModel;
import com.urbanairship.android.layout.model.RadioInputController;
import com.urbanairship.android.layout.model.RadioInputModel;
import com.urbanairship.android.layout.model.ScoreModel;
import com.urbanairship.android.layout.model.ScrollLayoutModel;
import com.urbanairship.android.layout.model.TextInputModel;
import com.urbanairship.android.layout.model.ToggleModel;
import com.urbanairship.android.layout.model.WebViewModel;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.ui.ModalActivity;
import com.urbanairship.android.layout.view.CheckboxView;
import com.urbanairship.android.layout.view.ContainerLayoutView;
import com.urbanairship.android.layout.view.EmptyView;
import com.urbanairship.android.layout.view.ImageButtonView;
import com.urbanairship.android.layout.view.LabelButtonView;
import com.urbanairship.android.layout.view.LabelView;
import com.urbanairship.android.layout.view.LinearLayoutView;
import com.urbanairship.android.layout.view.MediaView;
import com.urbanairship.android.layout.view.PagerIndicatorView;
import com.urbanairship.android.layout.view.PagerView;
import com.urbanairship.android.layout.view.RadioInputView;
import com.urbanairship.android.layout.view.ScoreView;
import com.urbanairship.android.layout.view.ScrollLayoutView;
import com.urbanairship.android.layout.view.TextInputView;
import com.urbanairship.android.layout.view.ToggleView;
import com.urbanairship.android.layout.view.WebViewView;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Entry point and related helper methods for rendering layouts based on our internal DSL.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class Thomas {
    @VisibleForTesting
    static final int MAX_SUPPORTED_VERSION = 2;
    @VisibleForTesting
    static final int MIN_SUPPORTED_VERSION = 1;

    private Thomas() {}

    /**
     * Validates that a payload can be displayed.
     * @param payload The payload.
     * @return {@code true} if valid, otherwise {@code false}.
     */
    public static boolean isValid(@NonNull BasePayload payload) {
        if (!(payload.getVersion() >= MIN_SUPPORTED_VERSION && payload.getVersion() <= MAX_SUPPORTED_VERSION)) {
            return false;
        }

        if (!(payload.getPresentation() instanceof ModalPresentation)) {
          return false;
        }

        return true;
    }

    @NonNull
    public static DisplayRequest prepareDisplay(@NonNull BasePayload payload) throws DisplayException {
        if (!isValid(payload)) {
            throw new DisplayException("Payload is not valid: " + payload.getPresentation());
        }

        if (payload.getPresentation() instanceof ModalPresentation) {
            return new DisplayRequest(payload, (context, args) -> {
                Intent intent = new Intent(context, ModalActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(ModalActivity.EXTRA_DISPLAY_ARGS_LOADER, DisplayArgsLoader.newLoader(args));
                context.startActivity(intent);
            });
        } else {
            throw new DisplayException("Presentation not supported: " + payload.getPresentation());
        }
    }

    @NonNull
    public static BaseModel model(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();
        switch (ViewType.from(typeString)) {
            case CONTAINER:
            case LINEAR_LAYOUT:
            case SCROLL_LAYOUT:
            case PAGER_CONTROLLER:
            case FORM_CONTROLLER:
            case NPS_FORM_CONTROLLER:
            case CHECKBOX_CONTROLLER:
            case RADIO_INPUT_CONTROLLER:
                return LayoutModel.fromJson(json);

            case MEDIA:
                return MediaModel.fromJson(json);
            case LABEL:
                return LabelModel.fromJson(json);
            case LABEL_BUTTON:
                return LabelButtonModel.fromJson(json);
            case IMAGE_BUTTON:
                return ImageButtonModel.fromJson(json);
            case EMPTY_VIEW:
                return EmptyModel.fromJson(json);
            case WEB_VIEW:
                return WebViewModel.fromJson(json);
            case PAGER:
                return PagerModel.fromJson(json);
            case PAGER_INDICATOR:
                return PagerIndicatorModel.fromJson(json);

            case CHECKBOX:
                return CheckboxModel.fromJson(json);
            case TOGGLE:
                return ToggleModel.fromJson(json);
            case RADIO_INPUT:
                return RadioInputModel.fromJson(json);
            case TEXT_INPUT:
                return TextInputModel.fromJson(json);
            case SCORE:
                return ScoreModel.fromJson(json);
        }
        throw new JsonException("Error parsing model! Unrecognized view type: " + typeString);
    }

    @NonNull
    public static View view(@NonNull Context context, @NonNull BaseModel model, @NonNull Environment environment) {
        switch (model.getType()) {
            case CONTAINER:
                return ContainerLayoutView.create(context, (ContainerLayoutModel) model, environment);
            case LINEAR_LAYOUT:
                return LinearLayoutView.create(context, (LinearLayoutModel) model, environment);
            case SCROLL_LAYOUT:
                return ScrollLayoutView.create(context, (ScrollLayoutModel) model, environment);

            // Controllers don't have views, so we skip over them and inflate their child view instead.
            case PAGER_CONTROLLER:
                return view(context, ((PagerController) model).getView(), environment);
            case FORM_CONTROLLER:
                return view(context, ((FormController) model).getView(), environment);
            case NPS_FORM_CONTROLLER:
                return view(context, ((NpsFormController) model).getView(), environment);
            case CHECKBOX_CONTROLLER:
                return view(context, ((CheckboxController) model).getView(), environment);
            case RADIO_INPUT_CONTROLLER:
                return view(context, ((RadioInputController) model).getView(), environment);

            case MEDIA:
                return MediaView.create(context, (MediaModel) model, environment);
            case LABEL:
                return LabelView.create(context, (LabelModel) model, environment);
            case LABEL_BUTTON:
                return LabelButtonView.create(context, (LabelButtonModel) model, environment);
            case IMAGE_BUTTON:
                return ImageButtonView.create(context, (ImageButtonModel) model, environment);
            case EMPTY_VIEW:
                return EmptyView.create(context, (EmptyModel) model, environment);
            case WEB_VIEW:
                return WebViewView.create(context, (WebViewModel) model, environment);
            case PAGER:
                return PagerView.create(context, (PagerModel) model, environment);
            case PAGER_INDICATOR:
                return PagerIndicatorView.create(context, (PagerIndicatorModel) model, environment);

            case CHECKBOX:
                return CheckboxView.create(context, (CheckboxModel) model, environment);
            case TOGGLE:
                return ToggleView.create(context, (ToggleModel) model, environment);
            case RADIO_INPUT:
                return RadioInputView.create(context, (RadioInputModel) model, environment);
            case TEXT_INPUT:
                return TextInputView.create(context, (TextInputModel) model, environment);
            case SCORE:
                return ScoreView.create(context, (ScoreModel) model, environment);
        }
        throw new IllegalArgumentException("Error creating view! Unrecognized view type: " + model.getType());
    }
}
