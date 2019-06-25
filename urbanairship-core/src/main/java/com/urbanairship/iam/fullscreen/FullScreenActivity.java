/* Copyright Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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

    @Nullable
    protected FullScreenDisplayContent displayContent;
    private MediaView mediaView;

    @Override
    protected void onCreateMessage(@Nullable Bundle savedInstanceState) {
        if (getMessage() == null) {
            finish();
            return;
        }

        displayContent = getMessage().getDisplayContent();
        if (displayContent == null) {
            finish();
            return;
        }

        @FullScreenDisplayContent.Template
        String template = normalizeTemplate(displayContent);

        setContentView(getTemplate(template));
        hideActionBar();

        TextView heading = findViewById(R.id.heading);
        TextView body = findViewById(R.id.body);
        InAppButtonLayout buttonLayout = findViewById(R.id.buttons);
        this.mediaView = findViewById(R.id.media);
        Button footer = findViewById(R.id.footer);
        ImageButton dismiss = findViewById(R.id.dismiss);
        View contentHolder = findViewById(R.id.content_holder);

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
            InAppViewUtils.loadMediaInfo(mediaView, displayContent.getMedia(), getMessageAssets());
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
                public void onClick(@NonNull View view) {
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
                if (getDisplayHandler() != null) {
                    getDisplayHandler().finished(ResolutionInfo.dismissed(), getDisplayTime());
                }
                finish();
            }
        });

        // Background color
        getWindow().getDecorView().setBackgroundColor(displayContent.getBackgroundColor());

        // Apply the insets but do not consume them. Allows for the dismiss button to also receive the insets.
        if (ViewCompat.getFitsSystemWindows(contentHolder)) {
            ViewCompat.setOnApplyWindowInsetsListener(contentHolder, new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, WindowInsetsCompat insets) {
                    ViewCompat.onApplyWindowInsets(v, insets);
                    return insets;
                }
            });
        }
    }

    @Override
    public void onButtonClicked(@NonNull View view, @NonNull ButtonInfo buttonInfo) {
        if (getDisplayHandler() == null) {
            return;
        }

        InAppActionUtils.runActions(buttonInfo);
        getDisplayHandler().finished(ResolutionInfo.buttonPressed(buttonInfo), getDisplayTime());
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

    /**
     * Gets the normalized template from the display content. The template may differ from the
     * display content's template to facilitate theming.
     *
     * @param displayContent The display content.
     * @return The full screen template.
     */
    @NonNull
    @FullScreenDisplayContent.Template
    protected String normalizeTemplate(@NonNull FullScreenDisplayContent displayContent) {
        String template = displayContent.getTemplate();

        // If we do not have media use TEMPLATE_HEADER_BODY_MEDIA
        if (displayContent.getMedia() == null) {
            return FullScreenDisplayContent.TEMPLATE_HEADER_BODY_MEDIA;
        }

        // If we do not have a header for template TEMPLATE_HEADER_MEDIA_BODY, but we have media,
        // fallback to TEMPLATE_MEDIA_HEADER_BODY to avoid missing padding at the top modal
        if (template.equals(FullScreenDisplayContent.TEMPLATE_HEADER_MEDIA_BODY) && displayContent.getHeading() == null && displayContent.getMedia() != null) {
            return FullScreenDisplayContent.TEMPLATE_MEDIA_HEADER_BODY;
        }

        return template;
    }

}
