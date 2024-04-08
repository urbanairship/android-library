package com.urbanairship.sample;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.messagecenter.InboxListener;
import com.urbanairship.messagecenter.Message;
import com.urbanairship.messagecenter.MessageCenter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import static java.util.Objects.requireNonNull;

/**
 * Main application entry point.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Remember the sent date of the last received [RichPushMessage].
     */
    private static final String LAST_MESSAGE_SENT_DATE = "LAST_MC_SENT_DATE";

    private static final Set<Integer> TOP_LEVEL_DESTINATIONS = new HashSet<Integer>() {{
        add(R.id.homeFragment);
        add(R.id.inboxFragment);
        add(R.id.settingsFragment);
        add(R.id.debugFragment);
    }};

    private final InboxListener inboxListener = this::showMessageCenterIndicator;

    private Snackbar messageCenterSnackbar;
    private long messageCenterLastSentDate;
    private BottomNavigationView navigationView = null;
    private NavController navController = null;
    private AppBarConfiguration appBarConfiguration = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment =
                (NavHostFragment) requireNonNull(getSupportFragmentManager().findFragmentById(R.id.nav_host_container));

        navController = navHostFragment.getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(TOP_LEVEL_DESTINATIONS).build();
        navigationView = findViewById(R.id.navigation);

        NavigationUI.setupWithNavController(navigationView, navController);

        if (savedInstanceState != null) {
            messageCenterLastSentDate = savedInstanceState.getLong(LAST_MESSAGE_SENT_DATE);
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (savedInstanceState != null) {
            navController.handleDeepLink(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        navController.handleDeepLink(intent);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(LAST_MESSAGE_SENT_DATE, messageCenterLastSentDate);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Handle any Google Play services errors
        if (PlayServicesUtils.isGooglePlayStoreAvailable(this)) {
            PlayServicesUtils.handleAnyPlayServicesError(this);
        }

        MessageCenter.shared().getInbox().addListener(inboxListener);
        showMessageCenterIndicator();
    }

    @Override
    public void onPause() {
        super.onPause();
        MessageCenter.shared().getInbox().removeListener(inboxListener);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration);
    }

    /**
     * Shows a Message Center indicator.
     */
    private void showMessageCenterIndicator() {
        List<Message> unreadMessage = MessageCenter.shared().getInbox().getUnreadMessages();

        // Skip showing the indicator if we have no unread messages or no new messages since the last display
        if (unreadMessage.isEmpty() || messageCenterLastSentDate >= unreadMessage.get(0).getSentDateMS()) {
            return;
        }

        // Track the message sent date to track if we have a new message
        messageCenterLastSentDate = unreadMessage.get(0).getSentDateMS();

        // Skip showing the indicator if its already displaying
        if (messageCenterSnackbar != null && messageCenterSnackbar.isShownOrQueued()) {
            return;
        }

        String text = getResources().getQuantityString(R.plurals.mc_indicator_text, unreadMessage.size(), unreadMessage.size());

        //noinspection ResourceType - For the duration field of the snackbar when defining a custom duration
        messageCenterSnackbar = Snackbar.make(findViewById(R.id.nav_host_container), text, Snackbar.LENGTH_LONG)
                                        .setActionTextColor(ContextCompat.getColor(this, R.color.accent))
                                        .setAction(R.string.view, v -> {
                                            messageCenterSnackbar.dismiss();
                                            navigationView.setSelectedItemId(R.id.inbox);
                                        });

        messageCenterSnackbar.show();
    }
}
