/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.modal;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.InAppActionUtils;
import com.urbanairship.iam.InAppMessageActivity;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.view.BackgroundDrawableBuilder;
import com.urbanairship.iam.view.BorderRadius;
import com.urbanairship.iam.view.BoundedLinearLayout;
import com.urbanairship.iam.view.InAppButtonLayout;
import com.urbanairship.iam.view.InAppViewUtils;
import com.urbanairship.iam.view.MediaView;
import com.urbanairship.widget.UAWebChromeClient;

/**
 * Modal in-app message activity.
 */
public class ModalActivity extends InAppMessageActivity implements InAppButtonLayout.ButtonClickListener {

    private MediaView mediaView;

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
        this.mediaView = findViewById(R.id.media);
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
        if (displayContent.getMedia() != null) {
            mediaView.setChromeClient(new UAWebChromeClient(this));
            InAppViewUtils.loadMediaInfo(mediaView, displayContent.getMedia(), getCache());
        } else {
            mediaView.setVisibility(View.GONE);
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

        // Devices older than kitkat require software rendering, but video requires hardware
        // acceleration. Instead of clipping the media, older devices use a view that does not
        // require clipping.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            modal.setClipPathBorderRadius(displayContent.getBorderRadius());
        }

        // DismissButton
        Drawable dismissDrawable = DrawableCompat.wrap(dismiss.getDrawable()).mutate();
        DrawableCompat.setTint(dismissDrawable, displayContent.getDismissButtonColor());
        dismiss.setImageDrawable(dismissDrawable);
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

    @Override
    protected void onResume() {
        super.onResume();
        this.mediaView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.mediaView.onPause();
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

}
