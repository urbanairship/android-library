package com.urbanairship.sample;

import android.content.Context;
import android.util.Log;

import com.urbanairship.json.JsonMap;
import com.urbanairship.liveupdate.LiveUpdate;
import com.urbanairship.liveupdate.LiveUpdateEvent;
import com.urbanairship.liveupdate.LiveUpdateCustomHandler;
import com.urbanairship.liveupdate.LiveUpdateResult;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;

// TODO(live-update): Implement a custom live update handler to feed data to a widget.
public class CustomLiveUpdate implements LiveUpdateCustomHandler {
    @Override
    @NotNull
    public LiveUpdateResult<Void> onUpdate(@NonNull Context context, @NonNull LiveUpdateEvent event, @NonNull LiveUpdate update) {

        Log.d("CustomLiveUpdate", "onUpdate: action=" + event + ", update=" + update);

        if (event == LiveUpdateEvent.END) {
            // Dismiss the live update on STOP. The default behavior will leave the Live Update
            // active until the dismissal time is reached.
            return LiveUpdateResult.cancel();
        }

        JsonMap content = update.getContent();
        int teamOneScore = content.opt("team_one_score").getInt(0);
        int teamTwoScore = content.opt("team_two_score").getInt(0);
        String statusUpdate = content.opt("status_update").optString();

        return LiveUpdateResult.ok();
    }
}
