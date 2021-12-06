/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import android.os.Bundle;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.model.ModalPresentation;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.view.ModalView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class ModalActivity extends AppCompatActivity {
    // Asset loader
    public static final String EXTRA_DISPLAY_ARGS_LOADER = "com.urbanairship.android.layout.ui.EXTRA_DISPLAY_ARGS_LOADER";

    @Nullable
    private ModalView modalView;

    @Nullable
    private DisplayArgsLoader loader;

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
            DisplayArgs args = this.loader.getLayoutArgs();
            if (!(args.getPayload().getPresentation() instanceof ModalPresentation)) {
                Logger.error("Not a modal presentation");
                finish();
                return;
            }

            ModalPresentation presentation = (ModalPresentation) args.getPayload().getPresentation();
            BaseModel view = args.getPayload().getView();

            modalView = ModalView.create(this, view, presentation);
            modalView.setLayoutParams(new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            setContentView(modalView);

            if (presentation.isDismissOnTouchOutside()) {
                modalView.setOnClickOutsideListener(v -> finish());
            }
            view.addListener(eventListener);

            // Add loader listener last so its the last thing to receive events
            if (args.getEventListener() != null) {
                view.addListener(args.getEventListener());
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
    protected void onDestroy() {
        super.onDestroy();
        if (loader != null && isFinishing()) {
            loader.dispose();
        }
    }
}
