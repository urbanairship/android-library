package com.urbanairship.android.layout.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.urbanairship.android.layout.event.ReportingEvent.ReportType.FORM_RESULT;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.lifecycle.LifecycleOwner;

import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.UAirship;
import com.urbanairship.android.layout.BannerPresentation;
import com.urbanairship.android.layout.ModelEnvironment;
import com.urbanairship.android.layout.ModelFactoryException;
import com.urbanairship.android.layout.ModelProvider;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.display.DisplayArgs;
import com.urbanairship.android.layout.environment.DefaultViewEnvironment;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.EventSource;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.android.layout.info.ViewInfo;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.property.BannerPlacement;
import com.urbanairship.android.layout.property.VerticalPosition;
import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.DisplayTimer;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.app.ActivityListener;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.SimpleActivityListener;
import com.urbanairship.channel.AttributeEditor;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.webkit.AirshipWebViewClient;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class LayoutBanner implements EventListener, EventSource {

    /**
     * Metadata an app can use to specify the banner's container ID per activity.
     */
    @NonNull
    public final static String BANNER_CONTAINER_ID = "com.urbanairship.iam.banner.BANNER_CONTAINER_ID";

    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    private final static Map<Class, Integer> cachedContainerIds = new HashMap<>();

    private WeakReference<Activity> lastActivity;
    private WeakReference<ThomasBannerView> currentView;

    private Context context;
    private ActivityMonitor activityMonitor;

    @Nullable
    private ThomasListener externalListener;

    @Nullable
    private BannerPresentation presentation;
    private DisplayTimer displayTimer;

    private Factory<AirshipWebViewClient> webViewClientFactory;
    private ImageCache imageCache;
    private LayoutInfo basePayload;

    private final Predicate<Activity> activityPredicate = activity -> {
        try {
            if (getContainerView(activity) == null) {
                Logger.error("BannerAdapter - Unable to display in-app message. No view group found.");
                return false;
            }
        } catch (Exception e) {
            Logger.error("Failed to find container view.", e);
            return false;
        }

        return true;
    };

    private final ActivityListener activityListener = new SimpleActivityListener() {
        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            if (activityPredicate.apply(activity)) {
                LayoutBanner.this.onActivityStopped(activity);
            }
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            if (activityPredicate.apply(activity)) {
                LayoutBanner.this.onActivityResumed(activity);
            }
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            if (activityPredicate.apply(activity)) {
                LayoutBanner.this.onActivityPaused(activity);
            }
        }
    };

    public LayoutBanner(Context context, ActivityMonitor activityMonitor, DisplayArgs args) {
        this.context = context;
        this.activityMonitor = activityMonitor;

        externalListener = args.getListener();

        presentation = (BannerPresentation) args.getPayload().getPresentation();
        webViewClientFactory = args.getWebViewClientFactory();
        imageCache = args.getImageCache();
        basePayload = args.getPayload();

        displayTimer = new DisplayTimer((LifecycleOwner) context, 0);

        this.activityMonitor.addActivityListener(activityListener);
    }

    /**
     * Called when the banner is finished displaying.
     *
     * @param context The context.
     */
    @CallSuper
    @MainThread
    protected void onDisplayFinished(@NonNull Context context) {
        activityMonitor.removeActivityListener(activityListener);
    }

    /**
     * Attempts to display the banner.
     *
     */
    public void display() {
        List<Activity> activityList = activityMonitor.getResumedActivities(activityPredicate);
        if (activityList.isEmpty()) {
            return;
        }
        Activity activity = activityList.get(0);

        BannerPlacement placement = presentation.getDefaultPlacement();

        if (placement.shouldIgnoreSafeArea()) {
            WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        }

        ViewEnvironment environment = new DefaultViewEnvironment(
                (ComponentActivity) activity,
                webViewClientFactory,
                imageCache,
                displayTimer,
                placement.shouldIgnoreSafeArea()
        );


        ViewInfo view = basePayload.getView();

        ModelProvider modelProvider = new ModelProvider();

        ModelEnvironment modelEnvironment = new ModelEnvironment(modelProvider, new HashMap<>());

        try {
            BaseModel model = modelProvider.create(view, modelEnvironment);
            model.setListener(this);

            // Add thomas listener last so its the last thing to receive events
            if (this.externalListener != null) {
                setListener(new ThomasListenerProxy(this.externalListener));
            }

            ThomasBannerView bannerView = new ThomasBannerView(context, model, presentation, environment);
            bannerView.setLayoutParams(new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            if (getLastActivity() != activity) {
                if (presentation.getDefaultPlacement().getPosition() != null && VerticalPosition.BOTTOM.equals(presentation.getDefaultPlacement().getPosition().getVertical())) {
                    bannerView.setAnimations(R.animator.ua_layout_slide_in_bottom, R.animator.ua_layout_slide_out_bottom);
                } else {
                    bannerView.setAnimations(R.animator.ua_layout_slide_in_top, R.animator.ua_layout_slide_out_top);
                }
            }

            bannerView.setListener(new ThomasBannerView.Listener() {

                @Override
                public void onTimedOut(@NonNull ThomasBannerView view) {
                    onDisplayFinished(context);
                }

                @Override
                public void onDismissed(@NonNull View view) {
                    onDisplayFinished(context);
                }

                @Override
                public void onDragStateChanged(@NonNull View view, int state) {
                    switch (state) {
                        case ViewDragHelper.STATE_DRAGGING:
                            bannerView.getTimer().stop();
                            break;
                        case ViewDragHelper.STATE_IDLE:
                            if (bannerView.isResumed()) {
                                bannerView.getTimer().start();
                            }
                            break;
                    }
                }
            });

            ViewGroup container = getContainerView(activity);
            if (container == null) {
                return;
            }

            if (bannerView.getParent() == null) {
                container.addView(bannerView);
            }

            lastActivity = new WeakReference<>(activity);
            currentView = new WeakReference<>(bannerView);

        } catch (ModelFactoryException e) {
            Logger.error("Failed to load model!", e);
            return;
        }
    }

    private void dismiss() {
        ThomasBannerView view = getCurrentView();
        if (view != null) {
            view.dismiss(false);
        }
    }

    /**
     * Gets the banner's container view.
     *
     * @param activity The activity.
     * @return The banner's container view or null.
     */
    @Nullable
    protected ViewGroup getContainerView(@NonNull Activity activity) {
        int containerId = getContainerId(activity);
        View view = null;
        if (containerId != 0) {
            view = activity.findViewById(containerId);
        }

        if (view == null) {
            view = activity.findViewById(android.R.id.content);
        }

        if (view instanceof ViewGroup) {
            return (ViewGroup) view;
        }

        return null;
    }

    /**
     * Gets the Banner fragment's container ID.
     * <p>
     * The default implementation checks the activities metadata for {@link #BANNER_CONTAINER_ID}.
     *
     * @param activity The activity.
     * @return The container ID or 0 if its not defined.
     */
    private int getContainerId(@NonNull Activity activity) {
        synchronized (cachedContainerIds) {
            Integer cachedId = cachedContainerIds.get(activity.getClass());
            if (cachedId != null) {
                return cachedId;
            }

            int containerId = 0;

            ActivityInfo info = ManifestUtils.getActivityInfo(activity.getClass());
            if (info != null && info.metaData != null) {
                containerId = info.metaData.getInt(BANNER_CONTAINER_ID, containerId);
            }

            cachedContainerIds.put(activity.getClass(), containerId);
            return containerId;
        }
    }

    @Override
    public boolean onEvent(@NonNull Event event, @NonNull LayoutData layoutData) {
        Logger.verbose("onEvent: %s, layoutData: %s", event, layoutData);

        switch (event.getType()) {
            case BUTTON_BEHAVIOR_CANCEL:
            case BUTTON_BEHAVIOR_DISMISS:
                reportDismissFromButton((ButtonEvent) event, layoutData);
                dismiss();
                return true;

            case WEBVIEW_CLOSE:
                reportDismissFromOutside(layoutData);
                dismiss();
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
        listeners.add(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void setListener(EventListener listener) {
        listeners.clear();
        listeners.add(listener);
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

    @MainThread
    private void onActivityResumed(@NonNull Activity activity) {
        ThomasBannerView currentView = getCurrentView();

        if (currentView == null || !ViewCompat.isAttachedToWindow(currentView)) {
            display();
        } else if (activity == getLastActivity()) {
            currentView.onResume();
        }
    }

    @MainThread
    private void onActivityStopped(@NonNull Activity activity) {
        if (activity != getLastActivity()) {
            return;
        }

        ThomasBannerView view = getCurrentView();
        if (view != null) {
            this.currentView = null;
            this.lastActivity = null;
            view.dismiss(false);
            display();
        }
    }

    @MainThread
    private void onActivityPaused(@NonNull Activity activity) {
        if (activity != getLastActivity()) {
            return;
        }

        ThomasBannerView currentView = getCurrentView();
        if (currentView != null) {
            currentView.onPause();
        }
    }

    @MainThread
    @Nullable
    private ThomasBannerView getCurrentView() {
        if (currentView == null) {
            return null;
        }

        return currentView.get();
    }

    @MainThread
    @Nullable
    private Activity getLastActivity() {
        if (lastActivity == null) {
            return null;
        }

        return lastActivity.get();
    }
}
