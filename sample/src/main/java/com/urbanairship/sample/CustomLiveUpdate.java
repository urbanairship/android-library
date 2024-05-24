package com.urbanairship.sample;

import android.content.Context;
import android.util.Log;

import com.urbanairship.json.JsonMap;
import com.urbanairship.liveupdate.CallbackLiveUpdateCustomHandler;
import com.urbanairship.liveupdate.LiveUpdate;
import com.urbanairship.liveupdate.LiveUpdateEvent;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;

public class CustomLiveUpdate implements CallbackLiveUpdateCustomHandler {
    @Override
    public void onUpdate(@NotNull Context context, @NotNull LiveUpdateEvent event, @NotNull LiveUpdate update, @NotNull LiveUpdateResultCallback resultCallback) {
        Log.d("CustomLiveUpdate", "onUpdate: action=" + event + ", update=" + update);

        if (event == LiveUpdateEvent.END) {
            // Dismiss the live update on STOP. The default behavior will leave the Live Update
            // active until the dismissal time is reached.
            resultCallback.cancel();
        }

        JsonMap content = update.getContent();
        int teamOneScore = content.opt("team_one_score").getInt(0);
        int teamTwoScore = content.opt("team_two_score").getInt(0);
        String statusUpdate = content.opt("status_update").optString();

        resultCallback.ok();
    }

}
