package com.urbanairship.push.badges;

public interface BadgeStorage {
    int getBadgeCount();

    void setBadgeCount(int value);

    int shiftWith(int howMany);
}
