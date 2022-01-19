package com.urbanairship.sample;

import android.content.Context;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.automation.Schedule;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TestAutopilot extends SampleAutopilot {

    @Override
    public void onAirshipReady(@NonNull UAirship airship) {
        super.onAirshipReady(airship);
        InAppAutomation.shared().getInAppMessageManager().setDisplayInterval(0, TimeUnit.MILLISECONDS);
        InAppAutomation.shared().cancelSchedules(Schedule.TYPE_IN_APP_MESSAGE);
    }

    @Nullable
    @Override
    public AirshipConfigOptions createAirshipConfigOptions(@NonNull Context context) {
        return AirshipConfigOptions.newBuilder()
                                   .setAppKey("APPKEYAPPKEYAPPKEYAPPK")
                                   .setAppSecret("APPSECRETAPPSECRETAPPS")
                                   .build();
    }

}
