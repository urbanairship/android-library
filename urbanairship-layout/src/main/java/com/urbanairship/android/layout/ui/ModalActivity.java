/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.display.DisplayArgs;
import com.urbanairship.android.layout.display.DisplayArgsLoader;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.EventSource;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.ModalPresentation;
import com.urbanairship.android.layout.property.ModalPlacement;
import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.DisplayTimer;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.util.ActionsRunner;
import com.urbanairship.android.layout.view.ModalView;
import com.urbanairship.channel.AttributeEditor;
import com.urbanairship.json.JsonValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.urbanairship.android.layout.event.ReportingEvent.ReportType.FORM_RESULT;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ModalActivity extends AppCompatActivity implements EventListener, EventSource {

    // Asset loader
    public static final String EXTRA_DISPLAY_ARGS_LOADER = "com.urbanairship.android.layout.ui.EXTRA_DISPLAY_ARGS_LOADER";

    private static final String KEY_DISPLAY_TIME = "display_time";

    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    @Nullable
    private DisplayArgsLoader loader;

    @Nullable
    private ThomasListener externalListener;

    private DisplayTimer displayTimer;
    private boolean disableBackButton = false;
    private ModalView modalView;

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

            this.externalListener = args.getListener();

            ModalPresentation presentation = (ModalPresentation) args.getPayload().getPresentation();

            long restoredTime = savedInstanceState != null ? savedInstanceState.getLong(KEY_DISPLAY_TIME) : 0;
            this.displayTimer = new DisplayTimer(this, restoredTime);

            ModalPlacement placement = presentation.getResolvedPlacement(this);
            setOrientationLock(placement);

            if (placement.shouldIgnoreSafeArea()) {
                WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
                getWindow().setStatusBarColor(R.color.system_bar_scrim_dark);
                getWindow().setNavigationBarColor(R.color.system_bar_scrim_dark);
            }

            Environment environment = new ViewEnvironment(
                    this,
                    args.getWebViewClientFactory(),
                    args.getImageCache(),
                    displayTimer,
                    placement.shouldIgnoreSafeArea()
            );

            BaseModel view = args.getPayload().getView();
            view.setListener(this);

            // Add thomas listener last so its the last thing to receive events
            if (this.externalListener != null) {
                setListener(new ThomasListenerProxy(this.externalListener));
            }

            modalView = ModalView.create(this, view, presentation, environment);
            modalView.setLayoutParams(new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            if (presentation.isDismissOnTouchOutside()) {
                modalView.setOnClickOutsideListener(v -> {
                    onEvent(new ReportingEvent.DismissFromOutside(displayTimer.getTime()), LayoutData.empty());
                    finish();
                });
            }

            disableBackButton = presentation.isDisableBackButton();

            setContentView(modalView);
        } catch (@NonNull DisplayArgsLoader.LoadException e) {
            Logger.error("Failed to load model!", e);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loader != null && isFinishing()) {
            loader.dispose();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_DISPLAY_TIME, displayTimer.getTime());
    }

    @Override
    public void onBackPressed() {
        if (!disableBackButton) {
            super.onBackPressed();
            reportDismissFromOutside(null);
        }
    }

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        Logger.verbose("onEvent: %s, layoutData: %s", event, layoutData);
        switch (event.getType()) {
            case BUTTON_BEHAVIOR_CANCEL:
            case BUTTON_BEHAVIOR_DISMISS:
                reportDismissFromButton((ButtonEvent) event, layoutData);
                finish();
                return true;

            case WEBVIEW_CLOSE:
                reportDismissFromOutside(layoutData);
                return true;

            case REPORTING_EVENT:
                if (((ReportingEvent) event).getReportType() == FORM_RESULT) {
                    applyAttributeUpdates((ReportingEvent.FormResult) event);
                }
                break;
        }

        for (EventListener listener : listeners) {
            if (listener.onEvent(event, layoutData)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addListener(EventListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        this.listeners.remove(listener);
    }

    @Override
    public void setListener(EventListener listener) {
        this.listeners.clear();
        this.listeners.add(listener);
    }

    private void reportDismissFromButton(@NonNull ButtonEvent event, @NonNull LayoutData layoutData) {
        // Re-wrap the event as a reporting event and run it back through so we'll notify the external listener.
        onEvent(new ReportingEvent.DismissFromButton(
                        event.getIdentifier(),
                        event.getReportingDescription(),
                        event.isCancel(),
                        displayTimer.getTime()),
                layoutData);
    }

    private void reportDismissFromOutside(@NonNull LayoutData state) {
        onEvent(new ReportingEvent.DismissFromOutside(displayTimer.getTime()), state);
    }

    private void applyAttributeUpdates(ReportingEvent.FormResult result) {
        AttributeEditor contactEditor = UAirship.shared().getContact().editAttributes();
        AttributeEditor channelEditor = UAirship.shared().getChannel().editAttributes();

        for (Map.Entry<AttributeName, JsonValue> entry : result.getAttributes().entrySet()) {
            AttributeName key = entry.getKey();
            String attribute = key.isContact() ? key.getContact() : key.getChannel();
            JsonValue value = entry.getValue();
            if (attribute == null || value == null || value.isNull()) {
                continue;
            }

            Logger.debug("Setting %s attribute: \"%s\" => %s",
                    key.isChannel() ? "channel" : "contact", attribute, value.toString());

            AttributeEditor editor = key.isContact() ? contactEditor : channelEditor;
            setAttribute(editor, attribute, value);
        }

        contactEditor.apply();
        channelEditor.apply();
    }

    private void setAttribute(@NonNull AttributeEditor editor, @NonNull String attribute, @NonNull JsonValue value) {
        if (value.isString()) {
            editor.setAttribute(attribute, value.optString());
        } else if (value.isDouble()) {
            editor.setAttribute(attribute, value.getDouble(-1));
        } else if (value.isFloat()) {
            editor.setAttribute(attribute, value.getFloat(-1));
        } else if (value.isInteger()) {
            editor.setAttribute(attribute, value.getInt(-1));
        } else if (value.isLong()) {
            editor.setAttribute(attribute, value.getLong(-1));
        }
    }

    private void setOrientationLock(@NonNull ModalPlacement placement) {
        try {
            if (placement.getOrientationLock() != null) {
                if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
                    switch (placement.getOrientationLock()) {
                        case PORTRAIT:
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            break;
                        case LANDSCAPE:
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            break;
                    }
                } else {
                    // Orientation locking isn't allowed on API 26 for transparent activities,
                    // so we'll do the best we can and inherit the parent activity's orientation.
                    // If the parent activity is locked to an orientation, we'll be locked to that
                    // orientation, too. Otherwise, rotation will be allowed even though the layout
                    // requested it not be.
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_BEHIND);
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Unable to set orientation lock.");
        }
    }

}
