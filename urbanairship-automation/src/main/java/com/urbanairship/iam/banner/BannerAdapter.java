/* Copyright Airship and Contributors */

package com.urbanairship.iam.banner;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.app.ActivityListener;
import com.urbanairship.app.FilteredActivityListener;
import com.urbanairship.app.SimpleActivityListener;
import com.urbanairship.automation.R;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppActionUtils;
import com.urbanairship.iam.InAppActivityMonitor;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.MediaDisplayAdapter;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.view.InAppViewUtils;
import com.urbanairship.util.ManifestUtils;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

/**
 * Banner display adapter.
 */
public class BannerAdapter extends MediaDisplayAdapter {

    /**
     * Metadata an app can use to specify the banner's container ID per activity.
     */
    @NonNull
    public final static String BANNER_CONTAINER_ID = "com.urbanairship.iam.banner.BANNER_CONTAINER_ID";

    private final BannerDisplayContent displayContent;
    private final static Map<Class, Integer> cachedContainerIds = new HashMap<>();
    private final Predicate<Activity> activityPredicate = new Predicate<Activity>() {
        @Override
        public boolean apply(Activity activity) {
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
        }
    };

    private final ActivityListener listener = new SimpleActivityListener() {
        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            if (activityPredicate.apply(activity)) {
                BannerAdapter.this.onActivityStopped(activity);
            }
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            if (activityPredicate.apply(activity)) {
                BannerAdapter.this.onActivityResumed(activity);
            }
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            if (activityPredicate.apply(activity)) {
                BannerAdapter.this.onActivityPaused(activity);
            }
        }
    };

    private WeakReference<Activity> lastActivity;
    private WeakReference<BannerView> currentView;
    private DisplayHandler displayHandler;

    /**
     * Default constructor.
     *
     * @param displayContent The display content.
     * @param message The in-app message.
     */
    protected BannerAdapter(@NonNull InAppMessage message, @NonNull BannerDisplayContent displayContent) {
        super(message, displayContent.getMedia());
        this.displayContent = displayContent;
    }

    /**
     * Creates a new banner adapter.
     *
     * @param message The in-app message.
     * @return The banner adapter.
     * @throws IllegalArgumentException If the message is not a banner in-app message.
     */
    @NonNull
    public static BannerAdapter newAdapter(@NonNull InAppMessage message) {
        BannerDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new BannerAdapter(message, displayContent);
    }

    @MainThread
    @Override
    @CallSuper
    public boolean isReady(@NonNull Context context) {
        if (!super.isReady(context)) {
            return false;
        }

        return !InAppActivityMonitor.shared(context)
                                    .getResumedActivities(activityPredicate)
                                    .isEmpty();
    }

    @MainThread
    @Override
    public void onDisplay(@NonNull Context context, @NonNull DisplayHandler displayHandler) {
        Logger.info("BannerAdapter - Displaying in-app message.");

        this.displayHandler = displayHandler;
        InAppActivityMonitor.shared(context).addActivityListener(listener);
        display(context);
    }

    /**
     * Called when the banner is finished displaying.
     *
     * @param context The context.
     */
    @CallSuper
    @MainThread
    protected void onDisplayFinished(@NonNull Context context) {
        InAppActivityMonitor.shared(context).removeActivityListener(listener);
    }

    /**
     * Inflates the banner view.
     *
     * @param activity The activity.
     * @param viewGroup The container view.
     * @return The banner view.
     */
    @NonNull
    protected BannerView onCreateView(@NonNull Activity activity, @NonNull ViewGroup viewGroup) {
        return new BannerView(activity, displayContent, getAssets());
    }

    /**
     * Called after the banner view is created.
     *
     * @param view The banner view.
     * @param activity The activity.
     * @param viewGroup The container view.
     */
    protected void onViewCreated(@NonNull BannerView view, @NonNull Activity activity, @NonNull ViewGroup viewGroup) {
        if (getLastActivity() != activity) {
            if (BannerDisplayContent.PLACEMENT_BOTTOM.equals(displayContent.getPlacement())) {
                view.setAnimations(R.animator.ua_iam_slide_in_bottom, R.animator.ua_iam_slide_out_bottom);
            } else {
                view.setAnimations(R.animator.ua_iam_slide_in_top, R.animator.ua_iam_slide_out_top);
            }
        }

        view.setListener(new BannerView.Listener() {
            @Override
            public void onButtonClicked(@NonNull BannerView view, @NonNull ButtonInfo buttonInfo) {
                InAppActionUtils.runActions(buttonInfo);
                displayHandler.finished(ResolutionInfo.buttonPressed(buttonInfo), view.getTimer().getRunTime());
                onDisplayFinished(view.getContext());
            }

            @Override
            public void onBannerClicked(@NonNull BannerView view) {
                if (!displayContent.getActions().isEmpty()) {
                    InAppActionUtils.runActions(displayContent.getActions());
                    displayHandler.finished(ResolutionInfo.messageClicked(), view.getTimer().getRunTime());
                }

                onDisplayFinished(view.getContext());
            }

            @Override
            public void onTimedOut(@NonNull BannerView view) {
                displayHandler.finished(ResolutionInfo.timedOut(), view.getTimer().getRunTime());
                onDisplayFinished(view.getContext());
            }

            @Override
            public void onUserDismissed(@NonNull BannerView view) {
                displayHandler.finished(ResolutionInfo.dismissed(), view.getTimer().getRunTime());
                onDisplayFinished(view.getContext());
            }
        });
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
     * Attempts to display the banner.
     *
     * @param context THe application context.
     */
    private void display(@NonNull Context context) {
        List<Activity> activityList = InAppActivityMonitor.shared(context).getResumedActivities(activityPredicate);
        if (activityList.isEmpty()) {
            return;
        }

        Activity activity = activityList.get(0);

        ViewGroup container = getContainerView(activity);
        if (container == null) {
            return;
        }

        final BannerView view = onCreateView(activity, container);
        onViewCreated(view, activity, container);

        if (view.getParent() == null) {
            if (container.getId() == android.R.id.content) {
                // Android stops dispatching insets to the remaining children if a child
                // consumes the insets. To work around this, we are inserting the view
                // at index 0, but setting the Z value larger than the other children
                // so it's drawn on top.
                view.setZ(InAppViewUtils.getLargestChildZValue(container) + 1);
                container.addView(view, 0);
            } else {
                container.addView(view);
            }
        }

        lastActivity = new WeakReference<>(activity);
        currentView = new WeakReference<>(view);
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

    @MainThread
    private void onActivityResumed(@NonNull Activity activity) {
        BannerView currentView = getCurrentView();

        if (currentView == null || !ViewCompat.isAttachedToWindow(currentView)) {
            display(activity);
        } else if (activity == getLastActivity()) {
            currentView.onResume();
        }
    }

    @MainThread
    private void onActivityStopped(@NonNull Activity activity) {
        if (activity != getLastActivity()) {
            return;
        }

        BannerView view = getCurrentView();
        if (view != null) {
            this.currentView = null;
            this.lastActivity = null;
            view.dismiss(false);
            display(activity.getApplicationContext());
        }
    }

    @MainThread
    private void onActivityPaused(@NonNull Activity activity) {
        if (activity != getLastActivity()) {
            return;
        }

        BannerView currentView = getCurrentView();
        if (currentView != null) {
            currentView.onPause();
        }
    }

    @MainThread
    @Nullable
    private BannerView getCurrentView() {
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
