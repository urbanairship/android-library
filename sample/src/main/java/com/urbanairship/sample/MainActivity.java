/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.urbanairship.UAirship;
import com.urbanairship.debug.DebugFragment;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.messagecenter.MessageCenterFragment;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /**
     * Remember the ID of the selected item for the navigation drawer.
     */
    private static final String NAV_ID = "NAV_ID";

    /**
     * Remember the last title of the activity.
     */
    private static final String TITLE = "TITLE";

    /**
     * Remember the sent date of the last received {@link RichPushMessage}.
     */
    private static final String LAST_MESSAGE_SENT_DATE = "LAST_MC_SENT_DATE";

    /**
     * How long to display the Message Center indicator
     */
    private static final int MESSAGE_CENTER_INDICATOR_DURATION_MS = 10000; // 10 seconds

    private DrawerLayout drawer;
    private int currentNavPosition = -1;

    private Snackbar messageCenterSnackbar;
    private long messageCenterLastSentDate;
    private NavigationView navigation;

    private RichPushInbox.Listener inboxListener = new RichPushInbox.Listener() {
        @Override
        public void onInboxUpdated() {
            showMessageCenterIndicator();
            updateUnreadCount();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Action bar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // App drawer
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // Navigation view
        navigation = (NavigationView) findViewById(R.id.nav_view);
        navigation.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                navigate(item.getItemId());
                return true;
            }
        });

        if (savedInstanceState != null) {
            navigate(savedInstanceState.getInt(NAV_ID));
            setTitle(savedInstanceState.getString(TITLE));

            messageCenterLastSentDate = savedInstanceState.getLong(LAST_MESSAGE_SENT_DATE);
        } else {
            navigation.setCheckedItem(R.id.nav_home);
            navigate(R.id.nav_home);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(NAV_ID, currentNavPosition);
        outState.putString(TITLE, String.valueOf(getTitle()));
        outState.putLong(LAST_MESSAGE_SENT_DATE, messageCenterLastSentDate);

    }

    @Override
    public void onResume() {
        super.onResume();

        // Handle any Google Play services errors
        if (PlayServicesUtils.isGooglePlayStoreAvailable(this)) {
            PlayServicesUtils.handleAnyPlayServicesError(this);
        }

        // Handle the "com.urbanairship.VIEW_RICH_PUSH_INBOX" intent action.
        if (RichPushInbox.VIEW_INBOX_INTENT_ACTION.equals(getIntent().getAction())) {
            Fragment fragment = navigate(R.id.nav_message_center);

            if (getIntent().getData() != null && getIntent().getData().getScheme().equalsIgnoreCase(RichPushInbox.MESSAGE_DATA_SCHEME)) {
                String messageId = getIntent().getData().getSchemeSpecificPart();
                if (fragment instanceof MessageCenterFragment) {
                    ((MessageCenterFragment) fragment).setMessageID(messageId);
                }
            }

            // Clear the action so we don't handle it again
            getIntent().setAction(null);
        }

        UAirship.shared().getInbox().addListener(inboxListener);

        showMessageCenterIndicator();
        updateUnreadCount();
    }

    @Override
    public void onPause() {
        super.onPause();
        UAirship.shared().getInbox().removeListener(inboxListener);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (currentNavPosition != R.id.nav_home) {
            navigate(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            this.startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    private Fragment navigate(int id) {
        currentNavPosition = id;
        navigation.setCheckedItem(id);

        Fragment fragment =  getSupportFragmentManager().findFragmentByTag("content_frag" + id);

        if (fragment != null) {
            return fragment;
        }

        switch (id) {
            case R.id.nav_home:
                setTitle(R.string.home_title);
                fragment = new HomeFragment();
                break;
            case R.id.nav_message_center:
                setTitle(R.string.message_center_title);
                fragment = new MessageCenterFragment();


                // Dismiss this Message Center indicator if it's being displayed
                if (messageCenterSnackbar != null && messageCenterSnackbar.isShownOrQueued()){
                    messageCenterSnackbar.dismiss();
                }

                break;
            case R.id.nav_location:
                setTitle(R.string.location_title);
                fragment = new LocationFragment();
                break;

            case R.id.nav_debug:
                setTitle(R.string.debug_title);
                fragment = new DebugFragment();
                break;

            default:
                Log.e(TAG, "Unexpected navigation item");
                return null;
        }

        currentNavPosition = id;

        getSupportFragmentManager().beginTransaction()
                                   .replace(R.id.content_frame, fragment, "content_frag" + id)
                                   .commit();

        drawer.closeDrawer(GravityCompat.START);

        return fragment;
    }

    /**
     * Updates the unread count for the message center in the nav bar.
     */
    private void updateUnreadCount() {
        int unreadCount = UAirship.shared().getInbox().getUnreadCount();
        TextView view = (TextView) navigation.getMenu().findItem(R.id.nav_message_center).getActionView();

        if (unreadCount > 0) {
            view.setVisibility(View.VISIBLE);
            view.setText(String.valueOf(unreadCount));
        } else {
            view.setVisibility(View.GONE);
        }
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

        // Skip showing the indicator if the activity is already showing the Message Center
        if (currentNavPosition == R.id.nav_message_center) {
            return;
        }

        String text = getResources().getQuantityString(R.plurals.mc_indicator_text, unreadMessage.size(), unreadMessage.size());

        //noinspection ResourceType - For the duration field of the snackbar when defining a custom duration
        messageCenterSnackbar = Snackbar.make(findViewById(R.id.coordinatorLayout), text, MESSAGE_CENTER_INDICATOR_DURATION_MS)
                                        .setActionTextColor(ContextCompat.getColor(this, R.color.color_accent))
                                        .setAction(R.string.view, new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                messageCenterSnackbar.dismiss();
                                                UAirship.shared().getInbox().startInboxActivity();
                                            }
                                        });

        messageCenterSnackbar.show();
    }
}
