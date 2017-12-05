/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.modal;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.InAppActionUtils;
import com.urbanairship.iam.InAppMessageActivity;
import com.urbanairship.iam.InAppMessageCache;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.view.BackgroundDrawableBuilder;
import com.urbanairship.iam.view.BorderRadius;
import com.urbanairship.iam.view.BoundedLinearLayout;
import com.urbanairship.iam.view.InAppButtonLayout;
import com.urbanairship.iam.view.InAppViewUtils;
import com.urbanairship.messagecenter.ImageLoader;

import java.lang.ref.WeakReference;

/**
 * Modal in-app message activity.
 */
public class ModalActivity extends InAppMessageActivity implements InAppButtonLayout.ButtonClickListener {


    @Override
    protected void onCreateMessage(@Nullable Bundle savedInstanceState) {
        final ModalDisplayContent displayContent = getMessage().getDisplayContent();
        if (displayContent == null) {
            finish();
            return;
        }

        setContentView(R.layout.ua_iam_modal);

        // Inflate the content before finding other views
        ViewStub content = findViewById(R.id.modal_content);
        content.setLayoutResource(getTemplate(displayContent.getTemplate()));
        content.inflate();

        BoundedLinearLayout modal = findViewById(R.id.modal);
        TextView heading = findViewById(R.id.heading);
        TextView body = findViewById(R.id.body);
        InAppButtonLayout buttonLayout = findViewById(R.id.buttons);
        ImageView imageView = findViewById(R.id.media);
        Button footer = findViewById(R.id.footer);
        ImageButton dismiss = findViewById(R.id.dismiss);

        // Heading
        if (displayContent.getHeading() != null) {
            InAppViewUtils.applyTextInfo(heading, displayContent.getHeading());
        } else {
            heading.setVisibility(View.GONE);
        }

        // Body
        if (displayContent.getBody() != null) {
            InAppViewUtils.applyTextInfo(body, displayContent.getBody());
        } else {
            body.setVisibility(View.GONE);
        }

        // Media
        // TODO: Support video and youtube
        if (displayContent.getMedia() != null && displayContent.getMedia().getType().equals(MediaInfo.TYPE_IMAGE)) {
            String imageLocation = displayContent.getMedia().getUrl();
            if (getCache() != null) {
                imageLocation = getCache().getBundle().getString(InAppMessageCache.MEDIA_CACHE_KEY, imageLocation);
                int width = getCache().getBundle().getInt(InAppMessageCache.IMAGE_WIDTH_CACHE_KEY);
                int height = getCache().getBundle().getInt(InAppMessageCache.IMAGE_HEIGHT_CACHE_KEY);

                if (width > 0 && height > 0) {
                    // We need to scale the image view before the first draw so the scroll view
                    // will be the right size.
                    scaleImageView(imageView, width, height);
                }
            }

            ImageLoader.shared(this).load(imageLocation, 0, imageView);
            imageView.setContentDescription(displayContent.getMedia().getDescription());

        } else {
            imageView.setVisibility(View.GONE);
        }

        // Button Layout
        if (!displayContent.getButtons().isEmpty()) {
            buttonLayout.setButtons(displayContent.getButtonLayout(), displayContent.getButtons());
            buttonLayout.setButtonClickListener(this);
        } else {
            buttonLayout.setVisibility(View.GONE);
        }

        // Footer
        if (displayContent.getFooter() != null) {
            InAppViewUtils.applyButtonInfo(footer, displayContent.getFooter(), 0);
            footer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onButtonClicked(view, displayContent.getFooter());
                }
            });
        } else {
            footer.setVisibility(View.GONE);
        }


        final Drawable background = BackgroundDrawableBuilder.newBuilder(this)
                                                             .setBackgroundColor(displayContent.getBackgroundColor())
                                                             .setBorderRadius(displayContent.getBorderRadius(), BorderRadius.ALL)
                                                             .build();

        ViewCompat.setBackground(modal, background);
        if (displayContent.getBorderRadius() > 0) {
            BorderRadius.applyBorderRadiusPadding(modal, displayContent.getBorderRadius(), BorderRadius.ALL);
        }

        // DismissButton
        dismiss.getDrawable().setColorFilter(displayContent.getDismissButtonColor(), PorterDuff.Mode.MULTIPLY);
        dismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getDisplayHandler().finished(ResolutionInfo.dismissed(getDisplayTime()));
                finish();
            }
        });

    }

    @Override
    public void onButtonClicked(View view, ButtonInfo buttonInfo) {
        InAppActionUtils.runActions(buttonInfo);
        if (buttonInfo.getBehavior().equals(ButtonInfo.BEHAVIOR_CANCEL)) {
            getDisplayHandler().cancelFutureDisplays();
        }

        getDisplayHandler().finished(ResolutionInfo.buttonPressed(buttonInfo, getDisplayTime()));
        finish();
    }


    /**
     * Gets the layout for the given template.
     *
     * @param template The modal template.
     * @return The template layout resource ID.
     */
    @LayoutRes
    protected int getTemplate(@NonNull @ModalDisplayContent.Template String template) {
        switch (template) {
            case ModalDisplayContent.TEMPLATE_HEADER_BODY_MEDIA:
                return R.layout.ua_iam_modal_header_body_media;

            case ModalDisplayContent.TEMPLATE_HEADER_MEDIA_BODY:
                return R.layout.ua_iam_modal_header_media_body;

            case ModalDisplayContent.TEMPLATE_MEDIA_HEADER_BODY:
            default:
                return R.layout.ua_iam_modal_media_header_body;
        }
    }

    /**
     * Helper method to scale the image view to fix the image.
     *
     * @param imageView The image view.
     * @param imageWidth The image width.
     * @param imageHeight The image height.
     */
    private void scaleImageView(ImageView imageView, final int imageWidth, final int imageHeight) {
        if (imageView.getWidth() == 0) {
            final WeakReference<ImageView> weakReference = new WeakReference<ImageView>(imageView);
            imageView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ImageView imageView = weakReference.get();
                    if (imageView != null) {
                        scaleImageView(imageView, imageWidth, imageHeight);
                        imageView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    return false;
                }
            });
        }

        float scale = (float) imageView.getWidth() / (float) imageWidth;
        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        params.height = Math.round(scale * imageHeight);
        imageView.setLayoutParams(params);
    }
}
