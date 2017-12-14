/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.InAppActionUtils;
import com.urbanairship.iam.InAppMessageActivity;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.view.InAppButtonLayout;
import com.urbanairship.iam.view.InAppViewUtils;
import com.urbanairship.iam.view.MediaView;
import com.urbanairship.widget.UAWebChromeClient;

/**
 * Full screen in-app message activity.
 */
public class FullScreenActivity extends InAppMessageActivity implements InAppButtonLayout.ButtonClickListener {

    protected FullScreenDisplayContent displayContent;

    @Override
    protected void onCreateMessage(@Nullable Bundle savedInstanceState) {
        displayContent = getMessage().getDisplayContent();
        if (displayContent == null) {
            finish();
            return;
        }

        setContentView(getTemplate(displayContent.getTemplate()));
        hideActionBar();

        TextView heading = findViewById(R.id.heading);
        TextView body = findViewById(R.id.body);
        InAppButtonLayout buttonLayout = findViewById(R.id.buttons);
        MediaView mediaView = findViewById(R.id.media);
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

        // Background color
        getWindow().getDecorView().setBackgroundColor(displayContent.getBackgroundColor());
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
     * @param template The fullscreen template.
     * @return The template layout resource ID.
     */
    @LayoutRes
    protected int getTemplate(@NonNull @FullScreenDisplayContent.Template String template) {
        switch (template) {
            case FullScreenDisplayContent.TEMPLATE_HEADER_BODY_MEDIA:
                return R.layout.ua_iam_fullscreen_header_body_media;

            case FullScreenDisplayContent.TEMPLATE_HEADER_MEDIA_BODY:
                return R.layout.ua_iam_fullscreen_header_media_body;

            case FullScreenDisplayContent.TEMPLATE_MEDIA_HEADER_BODY:
            default:
                return R.layout.ua_iam_fullscreen_media_header_body;
        }
    }
}
