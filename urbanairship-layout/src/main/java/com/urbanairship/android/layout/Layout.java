/* Copyright Airship and Contributors */

package com.urbanairship.android.layout;

import android.content.Context;
import android.view.View;

import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.model.CarouselIndicatorModel;
import com.urbanairship.android.layout.model.CarouselModel;
import com.urbanairship.android.layout.model.CheckboxInputModel;
import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.model.InputModel;
import com.urbanairship.android.layout.model.LabelModel;
import com.urbanairship.android.layout.model.LayoutModel;
import com.urbanairship.android.layout.model.LinearLayoutModel;
import com.urbanairship.android.layout.model.MediaModel;
import com.urbanairship.android.layout.model.RadioInputModel;
import com.urbanairship.android.layout.model.ScrollLayoutModel;
import com.urbanairship.android.layout.model.TextInputModel;
import com.urbanairship.android.layout.model.WebViewModel;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.view.BaseView;
import com.urbanairship.android.layout.view.ButtonView;
import com.urbanairship.android.layout.view.CarouselIndicatorView;
import com.urbanairship.android.layout.view.CarouselView;
import com.urbanairship.android.layout.view.CheckboxInputView;
import com.urbanairship.android.layout.view.ContainerLayoutView;
import com.urbanairship.android.layout.view.ImageButtonView;
import com.urbanairship.android.layout.view.LabelView;
import com.urbanairship.android.layout.view.LinearLayoutView;
import com.urbanairship.android.layout.view.MediaView;
import com.urbanairship.android.layout.view.RadioInputView;
import com.urbanairship.android.layout.view.ScrollLayoutView;
import com.urbanairship.android.layout.view.TextInputView;
import com.urbanairship.android.layout.view.WebViewView;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public final class Layout {
    private Layout() {}

    @NonNull
    public static BaseModel model(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();

        switch (ViewType.from(typeString)) {
            case CONTAINER:
            case LINEAR_LAYOUT:
            case SCROLL_LAYOUT:
                return LayoutModel.fromJson(json);
            case MEDIA:
                return MediaModel.fromJson(json);
            case LABEL:
                return LabelModel.fromJson(json);
            case BUTTON:
                return ButtonModel.fromJson(json);
            case IMAGE_BUTTON:
                return ImageButtonModel.fromJson(json);
            case WEB_VIEW:
                return WebViewModel.fromJson(json);
            case CAROUSEL:
                return CarouselModel.fromJson(json);
            case CAROUSEL_INDICATOR:
                return CarouselIndicatorModel.fromJson(json);
            case CHECKBOX_INPUT:
            case RADIO_INPUT:
            case TEXT_INPUT:
                return InputModel.fromJson(json);
        }

        throw new JsonException("Error parsing model! Unrecognized view type: " + typeString);
    }

    @NonNull
    public static View view(@NonNull Context context, @NonNull BaseModel model) {
        switch (model.getType()) {
            case CONTAINER:
                return ContainerLayoutView.create(context, (ContainerLayoutModel) model);
            case LINEAR_LAYOUT:
                return LinearLayoutView.create(context, (LinearLayoutModel) model);
            case SCROLL_LAYOUT:
                return ScrollLayoutView.create(context, (ScrollLayoutModel) model);
            case MEDIA:
                return MediaView.create(context, (MediaModel) model);
            case LABEL:
                return LabelView.create(context, (LabelModel) model);
            case BUTTON:
                return ButtonView.create(context, (ButtonModel) model);
            case IMAGE_BUTTON:
                return ImageButtonView.create(context, (ImageButtonModel) model);
            case WEB_VIEW:
                return WebViewView.create(context, (WebViewModel) model);
            case CAROUSEL:
                return CarouselView.create(context, (CarouselModel) model);
            case CAROUSEL_INDICATOR:
                return CarouselIndicatorView.create(context, (CarouselIndicatorModel) model);
            case CHECKBOX_INPUT:
                return CheckboxInputView.create(context, (CheckboxInputModel) model);
            case RADIO_INPUT:
                return RadioInputView.create(context, (RadioInputModel) model);
            case TEXT_INPUT:
                return TextInputView.create(context, (TextInputModel) model);
        }
        throw new IllegalArgumentException("Error creating view! Unrecognized view type: " + model.getType());
    }

    @NonNull
    public static LayoutViewHolder<?,?> viewHolder(
        @NonNull Context context,
        @NonNull ViewType viewType
    ) {
        switch (viewType) {
            case CONTAINER:
                return new LayoutViewHolder<>(new ContainerLayoutView(context));
            case LINEAR_LAYOUT:
                return new LayoutViewHolder<>(new LinearLayoutView(context));
            case SCROLL_LAYOUT:
                return new LayoutViewHolder<>(new ScrollLayoutView(context));
            case MEDIA:
                return new LayoutViewHolder<>(new MediaView(context));
            case LABEL:
                return new LayoutViewHolder<>(new LabelView(context));
            case BUTTON:
                return new LayoutViewHolder<>(new ButtonView(context));
            case IMAGE_BUTTON:
                return new LayoutViewHolder<>(new ImageButtonView(context));
            case WEB_VIEW:
                return new LayoutViewHolder<>(new WebViewView(context));
            // TODO: not sure if it really makes sense for anything below here to be a carousel item, but
            //       we'll allow whatever for now and see how it goes...
            case CAROUSEL:
                return new LayoutViewHolder<>(new CarouselView(context));
            case CAROUSEL_INDICATOR:
                return new LayoutViewHolder<>(new CarouselIndicatorView(context));
            case CHECKBOX_INPUT:
                return new LayoutViewHolder<>(new CheckboxInputView(context));
            case RADIO_INPUT:
                return new LayoutViewHolder<>(new RadioInputView(context));
            case TEXT_INPUT:
                return new LayoutViewHolder<>(new TextInputView(context));
        }

        throw new IllegalArgumentException("Error creating empty view stub! Unrecognized view type: " + viewType);
    }

    public static class LayoutViewHolder<V extends View & BaseView<M>, M extends BaseModel> extends RecyclerView.ViewHolder {
        private final V view;

        public LayoutViewHolder(@NonNull V itemView) {
            super(itemView);
            itemView.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT));
            view = itemView;
        }

        public void bind(@NonNull BaseModel item) {
            // TODO: not sure the generics are buying us anything if we still have an unchecked cast here... :-/
            //noinspection unchecked
            view.setModel((M) item);
        }
    }
}
