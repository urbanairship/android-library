package com.urbanairship.sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

import java.util.List;


/**
 * Main application entry point.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Remember the sent date of the last received [RichPushMessage].
     */
    private final static String LAST_MESSAGE_SENT_DATE = "LAST_MC_SENT_DATE";

    private RichPushInbox.Listener inboxListener = () -> showMessageCenterIndicator();

    private Snackbar messageCenterSnackbar;
    private long messageCenterLastSentDate;
    private BottomNavigationView navigationView = null;
    private MultiNavigationHelper helper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        navigationView = findViewById(R.id.navigation);

        if (savedInstanceState != null) {
            messageCenterLastSentDate = savedInstanceState.getLong(LAST_MESSAGE_SENT_DATE);
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        helper = MultiNavigationHelper.newHelper(R.id.nav_host_container,
                getSupportFragmentManager(),
                navigationView.getSelectedItemId(),
                R.navigation.nav_home,
                R.navigation.nav_inbox,
                R.navigation.nav_settings,
                R.navigation.ua_debug_navigation);

        navigationView.setOnNavigationItemReselectedListener(menuItem -> {
            helper.resetCurrentGraph();
        });

        navigationView.setOnNavigationItemSelectedListener(menuItem -> {
            // BottomNavigation items use the same Ids as the navigation graphs
            helper.navigate(menuItem.getItemId());
            return true;
        });

        helper.getCurrentNavController().observe(this, navController -> {
            if (navController == null) {
                return;
            }

            // Sync the selected item with the nav controller
            if (navigationView.getSelectedItemId() != navController.getGraph().getId()) {
                navigationView.setSelectedItemId(navController.getGraph().getId());
            }
        });

        if (savedInstanceState == null) {
            helper.deepLink(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        helper.deepLink(intent);
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

        UAirship.shared().getInbox().addListener(inboxListener);
        showMessageCenterIndicator();
    }

    @Override
    public void onPause() {
        super.onPause();
        UAirship.shared().getInbox().removeListener(inboxListener);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return helper.navigateUp();
    }

    @Override
    public void onBackPressed() {
        if (helper.popBackStack()) {
            return;
        }

        if (navigationView.getSelectedItemId() != R.id.home) {
            helper.navigate(R.id.home);
            return;
        }

        super.onBackPressed();
    }

    /**
     * Shows a Message Center indicator.
     */
    private void showMessageCenterIndicator() {
        List<RichPushMessage> unreadMessage = UAirship.shared().getInbox().getUnreadMessages();

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
                                        .setActionTextColor(ContextCompat.getColor(this, R.color.color_accent))
                                        .setAction(R.string.view, v -> {
                                            messageCenterSnackbar.dismiss();
                                            navigationView.setSelectedItemId(R.id.inbox);
                                        });

        messageCenterSnackbar.show();
    }

}
