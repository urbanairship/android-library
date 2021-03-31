/* Copyright Airship and Contributors */

package com.urbanairship.chat;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;

import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Airship Chat.
 */
public class AirshipChat extends AirshipComponent {

    private final Executor EXECUTOR = AirshipExecutors.newSerialExecutor();

    private static final String ENABLED_KEY = "com.urbanairship.chat.CHAT";

    private UAirship airship;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @param namedUser The named user.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AirshipChat(@NonNull Context context,
                       @NonNull PreferenceDataStore dataStore,
                       @NonNull AirshipChannel airshipChannel,
                       @NonNull NamedUser namedUser) {
        super(context, dataStore);
    }

    @Override
    protected void onAirshipReady(@NonNull UAirship airship) {
        super.onAirshipReady(airship);
        this.airship = airship;
        update();
    }

    /**
     * Gets the the tracker.
     *
     * @return The tracker.
     */
    @NonNull
    public static AirshipChat shared() {
        return UAirship.shared().requireComponent(AirshipChat.class);
    }

    @Override
    protected void init() {
        super.init();

        Logger.verbose("init!");
    }

    /**
     * Returns {@code true} if chat is enabled, {@code false} if its disabled.
     *
     * @return {@code true} if chat is enabled, {@code false} if its disabled.
     */
    public boolean isEnabled() {
        return getDataStore().getBoolean(ENABLED_KEY, false);
    }

    /**
     * Enables or disables chat.
     * <p>
     * The value is persisted in shared preferences.
     *
     * @param isEnabled {@code true} to enable chat, otherwise {@code false}.
     */
    public void setEnabled(boolean isEnabled) {
        synchronized (this) {
            getDataStore().put(ENABLED_KEY, isEnabled);

            if (isEnabled) {
                update();
            }
        }
    }

    private void update() {
        final UAirship airship = this.airship;
        if (airship == null) {
            return;
        }

        if (isEnabled()) {
            GlobalActivityMonitor.shared(getContext()).addApplicationListener(applicationListener);
        } else {
            GlobalActivityMonitor.shared(getContext()).removeApplicationListener(applicationListener);
            return;
        }
    }

    @Override
    protected void tearDown() {
        super.tearDown();
    }

    private final ApplicationListener applicationListener = new ApplicationListener() {
        @Override
        public void onForeground(long milliseconds) {
            // TODO: connect socket
        }

        @Override
        public void onBackground(long milliseconds) {
            // TODO: disconnect socket
        }
    };
}
