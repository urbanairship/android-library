package com.urbanairship.push.badges;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferenceBadgeStorage implements BadgeStorage {
    private static final String PREFS_NAME = "UrbanAirshipBadgeCounter";
    private static final String KEY_BADGE_COUNT = "KEY_BADGE_COUNT";

    private SharedPreferences prefs;

    public SharedPreferenceBadgeStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public int getBadgeCount() {
        return prefs.getInt(KEY_BADGE_COUNT, 0);
    }

    @Override
    public void setBadgeCount(int value) {
        prefs.edit()
                .putInt(KEY_BADGE_COUNT, value)
                .apply();
    }

    @Override
    public int shiftWith(int howMany) {
        int newValue = getBadgeCount() + howMany;
        if (newValue < 0) {
            newValue = 0;
        }
        setBadgeCount(newValue);
        return newValue;
    }
}
