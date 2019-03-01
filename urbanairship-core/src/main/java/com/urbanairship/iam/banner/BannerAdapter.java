/* Copyright Urban Airship and Contributors */

package com.urbanairship.iam.banner;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ViewGroup;

import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.R;
import com.urbanairship.app.ActivityListener;
import com.urbanairship.app.FilteredActivityListener;
import com.urbanairship.app.SimpleActivityListener;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppActionUtils;
import com.urbanairship.iam.InAppActivityMonitor;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.MediaDisplayAdapter;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.util.ManifestUtils;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<Class, Integer> cachedContainerIds = new HashMap<>();
    private final Predicate<Activity> activityPredicate = new Predicate<Activity>() {
        @Override
        public boolean apply(Activity activity) {
            int id = getContainerId(activity);
            if (id == 0 || activity.findViewById(id) == null || !(activity.findViewById(id) instanceof ViewGroup)) {
                Logger.error("BannerAdapter - Unable to display in-app message. Missing view with id: %s", id);
                return false;
            }
            return true;
        }
    };

    private final ActivityListener listener = new FilteredActivityListener(new SimpleActivityListener() {
        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            super.onActivityStopped(activity);
            BannerAdapter.this.onActivityStopped(activity);
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            super.onActivityResumed(activity);
            BannerAdapter.this.onActivityResumed(activity);
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            super.onActivityPaused(activity);
            BannerAdapter.this.onActivityPaused(activity);
        }

    }, activityPredicate);

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

    private void display(@NonNull Context context) {
        List<Activity> activityList = InAppActivityMonitor.shared(context).getResumedActivities(activityPredicate);
        if (activityList.isEmpty()) {
            return;
        }

        Activity activity = activityList.get(0);

        int id = getContainerId(activity);
        BannerView view = createView(activity);
        attachView(view, activity, id);

        lastActivity = new WeakReference<>(activity);
        currentView = new WeakReference<>(view);
        view.setListener(new BannerView.Listener() {
            @Override
            public void onButtonClicked(BannerView view, ButtonInfo buttonInfo) {
                InAppActionUtils.runActions(buttonInfo);
                displayHandler.finished(ResolutionInfo.buttonPressed(buttonInfo), view.getTimer().getRunTime());
                onDisplayFinished(view.getContext());
            }

            @Override
            public void onBannerClicked(BannerView view) {
                if (!displayContent.getActions().isEmpty()) {
                    InAppActionUtils.runActions(displayContent.getActions());
                    displayHandler.finished(ResolutionInfo.messageClicked(), view.getTimer().getRunTime());
                }

                onDisplayFinished(view.getContext());
            }

            @Override
            public void onTimedOut(BannerView view) {
                displayHandler.finished(ResolutionInfo.messageClicked(), view.getTimer().getRunTime());
                onDisplayFinished(view.getContext());
            }

            @Override
            public void onUserDismissed(BannerView view) {
                displayHandler.finished(ResolutionInfo.dismissed(), view.getTimer().getRunTime());
                onDisplayFinished(view.getContext());
            }
        });
    }

    protected void attachView(@NonNull BannerView bannerView, @NonNull Activity activity, int containerId) {
        ViewGroup viewGroup = activity.getWindow().getDecorView().findViewById(containerId);
        viewGroup.addView(bannerView);
    }

    protected BannerView createView(@NonNull Activity activity) {
        BannerView view = new BannerView(activity, displayContent, getAssets());
        if (getLastActivity() != activity) {
            if (BannerDisplayContent.PLACEMENT_BOTTOM.equals(displayContent.getPlacement())) {
                view.setAnimations(R.animator.ua_iam_slide_in_bottom, R.animator.ua_iam_slide_out_bottom);
            } else {
                view.setAnimations(R.animator.ua_iam_slide_in_top, R.animator.ua_iam_slide_out_top);
            }
        }

        return view;
    }

    @CallSuper
    @MainThread
    protected void onDisplayFinished(@NonNull Context context) {
        InAppActivityMonitor.shared(context).removeActivityListener(listener);
    }

    /**
     * Gets the Banner fragment's container ID.
     * <p>
     * The default implementation checks the activities metadata for {@link #BANNER_CONTAINER_ID}
     * and falls back to `android.R.id.content`.
     *
     * @param activity The activity.
     * @return The container ID.
     */
    protected int getContainerId(@NonNull Activity activity) {
        Integer cachedId = cachedContainerIds.get(activity.getClass());
        if (cachedId != null) {
            return cachedId;
        }

        int containerId = android.R.id.content;

        ActivityInfo info = ManifestUtils.getActivityInfo(activity.getClass());
        if (info != null && info.metaData != null) {
            containerId = info.metaData.getInt(BANNER_CONTAINER_ID, containerId);
        }

        cachedContainerIds.put(activity.getClass(), containerId);
        return containerId;
    }

    @MainThread
    private void onActivityResumed(@NonNull Activity activity) {
        BannerView view = getCurrentView();

        if (view == null) {
            display(activity);
            return;
        }

        if (view.getContext() == activity) {
            view.onResume();
        }
    }

    @MainThread
    private void onActivityStopped(@NonNull Activity activity) {
        BannerView view = getCurrentView();
        if (view != null && view.getContext() == activity) {
            view.dismiss(false);
            currentView = null;
            display(activity);
        }
    }

    @MainThread
    private void onActivityPaused(@NonNull Activity activity) {
        BannerView view = getCurrentView();

        if (view != null && view.getContext() == activity) {
            view.onPause();
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
