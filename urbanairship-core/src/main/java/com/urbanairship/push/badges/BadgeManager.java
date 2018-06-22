package com.urbanairship.push.badges;

import android.content.Context;
import android.support.annotation.NonNull;

import com.urbanairship.push.PushMessage;

import me.leolin.shortcutbadger.ShortcutBadger;

public class BadgeManager {
    private static final String DEFAULT_EXTRA_BADGE_KEY = "com.urbanairship.push.BADGE_COUNT";

    private Context context;
    private BadgeStorage badgeStorage;
    private String badgeExtraKey = DEFAULT_EXTRA_BADGE_KEY;

    public BadgeManager(Context context, BadgeStorage badgeStorage) {
        this.context = context.getApplicationContext();
        this.badgeStorage = badgeStorage;
    }

    public void setBadgeExtraKey(String badgeExtraKey) {
        this.badgeExtraKey = badgeExtraKey;
    }

    public String getBadgeExtraKey() {
        return badgeExtraKey;
    }

    public void handlePushReceived(@NonNull PushMessage message) {
        try {
            String badgeCountMsg = message.getExtra(badgeExtraKey, null);
            if (badgeCountMsg == null) {
                return;
            }
            boolean isIncrement = badgeCountMsg.indexOf("+") == 0;
            boolean isDecrement = badgeCountMsg.indexOf("-") == 0;
            int value = Integer.parseInt(isIncrement || isDecrement ? badgeCountMsg.substring(1) : badgeCountMsg);

            int newValue = 0;
            if (isIncrement) {
                newValue = badgeStorage.shiftWith(value);
            } else if (isDecrement) {
                newValue = badgeStorage.shiftWith(-value);
            } else {
                newValue = value;
            }
            setCount(newValue);
        } catch (Exception e) {
            // something went wrong during parsing, do nothing with badge counter
        }
    }

    public int getCount() {
        return badgeStorage.getBadgeCount();
    }

    public void setCount(int count) {
        badgeStorage.setBadgeCount(count);
        ShortcutBadger.applyCount(context, count);
    }

    public void clearCount() {
        badgeStorage.setBadgeCount(0);
        ShortcutBadger.removeCount(context);
    }

    public boolean isBadgeCounterSupported() {
        return ShortcutBadger.isBadgeCounterSupported(context);
    }
}
