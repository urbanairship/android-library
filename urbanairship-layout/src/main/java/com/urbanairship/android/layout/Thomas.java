/* Copyright Airship and Contributors */

package com.urbanairship.android.layout;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.android.layout.display.DisplayArgsLoader;
import com.urbanairship.android.layout.display.DisplayException;
import com.urbanairship.android.layout.display.DisplayRequest;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.CheckboxController;
import com.urbanairship.android.layout.model.CheckboxModel;
import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.model.EmptyModel;
import com.urbanairship.android.layout.model.FormController;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.model.LabelButtonModel;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.model.LinearLayoutModel;
import com.urbanairship.android.layout.model.MediaModel;
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
import com.urbanairship.android.layout.ui.LayoutBanner;
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
import com.urbanairship.app.GlobalActivityMonitor;

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
    public static boolean isValid(@NonNull LayoutInfo payload) {
        if (!(payload.getVersion() >= MIN_SUPPORTED_VERSION && payload.getVersion() <= MAX_SUPPORTED_VERSION)) {
            return false;
        }

        if (payload.getPresentation() instanceof ModalPresentation
                || payload.getPresentation() instanceof BannerPresentation) {
          return true;
        }

        return false;
    }

    @NonNull
    public static DisplayRequest prepareDisplay(@NonNull LayoutInfo payload) throws DisplayException {
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
        } else if (payload.getPresentation() instanceof BannerPresentation) {
            return new DisplayRequest(payload, (context, args) -> {
                LayoutBanner layoutBanner = new LayoutBanner(context, GlobalActivityMonitor.shared(context), args);
                layoutBanner.display();
            });
        } else {
            throw new DisplayException("Presentation not supported: " + payload.getPresentation());
        }
    }

    @NonNull
    public static View view(@NonNull Context context, @NonNull BaseModel model, @NonNull ViewEnvironment environment) {
        switch (model.getViewType()) {
            case CONTAINER:
                return new ContainerLayoutView(context, (ContainerLayoutModel) model, environment);
            case LINEAR_LAYOUT:
                return new LinearLayoutView(context, (LinearLayoutModel) model, environment);
            case SCROLL_LAYOUT:
                return new ScrollLayoutView(context, (ScrollLayoutModel) model, environment);

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
                return new MediaView(context, (MediaModel) model, environment);
            case LABEL:
                return new LabelView(context, (LabelModel) model, environment);
            case LABEL_BUTTON:
                return new LabelButtonView(context, (LabelButtonModel) model, environment);
            case IMAGE_BUTTON:
                return new ImageButtonView(context, (ImageButtonModel) model, environment);
            case EMPTY_VIEW:
                return new EmptyView(context, (EmptyModel) model, environment);
            case WEB_VIEW:
                return new WebViewView(context, (WebViewModel) model, environment);
            case PAGER:
                return new PagerView(context, (PagerModel) model, environment);
            case PAGER_INDICATOR:
                return new PagerIndicatorView(context, (PagerIndicatorModel) model, environment);

            case CHECKBOX:
                return new CheckboxView(context, (CheckboxModel) model, environment);
            case TOGGLE:
                return new ToggleView(context, (ToggleModel) model, environment);
            case RADIO_INPUT:
                return new RadioInputView(context, (RadioInputModel) model, environment);
            case TEXT_INPUT:
                return new TextInputView(context, (TextInputModel) model, environment);
            case SCORE:
                return new ScoreView(context, (ScoreModel) model, environment);
        }
        throw new IllegalArgumentException("Error creating view! Unrecognized view type: " + model.getViewType());
    }
}
