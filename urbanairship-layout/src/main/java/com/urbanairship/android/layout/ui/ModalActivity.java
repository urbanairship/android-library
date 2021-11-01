/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import android.os.Bundle;
import android.util.Log;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.display.ModalDisplay;
import com.urbanairship.android.layout.view.ModalView;
import com.urbanairship.json.JsonException;

import java.io.IOException;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import radiography.Radiography;
import radiography.ScanScopes;
import radiography.ViewStateRenderers;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.urbanairship.android.layout.util.ResourceUtils.readJsonAsset;

public class ModalActivity extends AppCompatActivity {
    // TODO: this should be an IAA ID that we can load a layout for...
    public static final String EXTRA_MODAL_ASSET = "com.urbanairship.android.automation.renderer.EXTRA_MODAL_ASSET";

    @Nullable
    private ModalView modalView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String fileName = getIntent().getStringExtra(EXTRA_MODAL_ASSET);
        try {
            ModalDisplay modal = ModalDisplay.fromJson(Objects.requireNonNull(readJsonAsset(this, "sample_layouts/" + fileName)));

            modalView = ModalView.create(this, modal);
            modalView.setLayoutParams(new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            setContentView(modalView);

            // TODO: remove me! (for debugging)
            modalView.getViewTreeObserver().addOnGlobalLayoutListener(() -> Logger.verbose(
                Radiography.scan(ScanScopes.singleViewScope(modalView), ViewStateRenderers.DefaultsIncludingPii))
            );

            modalView.setOnClickOutsideListener(v -> finish());

            modal.setListener(modalListener);
        } catch (@NonNull JsonException | IOException e) {
            Log.e(getClass().getSimpleName(), "Failed to load modal!", e);
        }
    }

    private final ModalDisplay.Listener modalListener = new ModalDisplay.Listener() {
        @Override
        public void onCancel() {
            finish();
        }

        @Override
        public void onDismiss() {
            finish();
        }
    };
}
