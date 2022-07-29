package com.urbanairship;

public class TestUtils {
    public static void setAirshipInstance(UAirship airship) {
        UAirship.sharedAirship = airship;
    }
}
