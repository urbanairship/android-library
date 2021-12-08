/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import android.os.Bundle;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.ModalPresentation;
import com.urbanairship.android.layout.view.ModalView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ModalActivity extends AppCompatActivity {

    // Asset loader
    public static final String EXTRA_DISPLAY_ARGS_LOADER = "com.urbanairship.android.layout.ui.EXTRA_DISPLAY_ARGS_LOADER";

    @Nullable
    private ModalView modalView;

    @Nullable
    private DisplayArgsLoader loader;

    @Nullable
    private ThomasListener listener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.loader = getIntent().getParcelableExtra(EXTRA_DISPLAY_ARGS_LOADER);
        if (this.loader == null) {
            Logger.error("Missing layout args loader");
            finish();
            return;
        }

        try {
            DisplayArgs args = this.loader.getDisplayArgs();
            if (!(args.getPayload().getPresentation() instanceof ModalPresentation)) {
                Logger.error("Not a modal presentation");
                finish();
                return;
            }

            this.listener = args.getListener();

            ModalPresentation presentation = (ModalPresentation) args.getPayload().getPresentation();
            BaseModel view = args.getPayload().getView();

            Environment environment = new ViewEnvironment(this, args.getWebViewClientFactory(), args.getImageCache());
            this.modalView = ModalView.create(this, view, presentation, environment);
            this.modalView.setLayoutParams(new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            setContentView(this.modalView);

            if (presentation.isDismissOnTouchOutside()) {
                this.modalView.setOnClickOutsideListener(v -> finish());
            }
            view.addListener(this.eventListener);

            // Add thomas listener last so its the last thing to receive events
            if (this.listener != null) {
                view.addListener(new ThomasListenerProxy(this.listener));
            }
        } catch (@NonNull DisplayArgsLoader.LoadException e) {
            Logger.error("Failed to load model!", e);
            finish();
        }
    }

    // Passive listener that cancels the modal
    private final EventListener eventListener = event -> {
        switch (event.getType()) {
            case BUTTON_BEHAVIOR_CANCEL:
            case BUTTON_BEHAVIOR_DISMISS:
                finish();
                return false;
        }
        return false;
    };

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // TODO: Need to notify listener on dismiss but only if this is going to finish the activity
//        if (this.listener != null) {
//            this.listener.onDismiss();
//        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loader != null && isFinishing()) {
            loader.dispose();
        }
    }

}
