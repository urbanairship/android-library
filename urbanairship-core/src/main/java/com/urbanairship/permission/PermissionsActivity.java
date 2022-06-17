/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.ArrayList;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

/**
 * Activity that requests permissions.
 *
 * The activity is currently restricted to only requesting a single permission at a time.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PermissionsActivity extends AppCompatActivity {

    @NonNull
    private static final String PERMISSION_EXTRA = "PERMISSION_EXTRA";

    @NonNull
    private static final String RESULT_RECEIVER_EXTRA = "RESULT_RECEIVER_EXTRA";

    @NonNull
    private static final String PERMISSION_STATUS_EXTRA = "PERMISSION_STATUS";

    @NonNull
    private static final String SILENTLY_DENIED_EXTRA = "SILENTLY_DENIED";

    // The only way to know about a silent dismiss if both before and after showRationale are false. However
    // on Android 11+ you can press back to skip or touch outside which will result in a none silent false/false.
    // This amount of time is not a guarantee to catch all but it helps reduce the number false positives.
    private static final long SILENT_DISMISS_MAX_TIME_MS = 2000;

    private List<Intent> intents = new ArrayList<>();
    private PermissionRequest currentRequest;
    private boolean isResumed = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::onPermissionResult);

    @Override
    public final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (savedInstanceState == null) {
            addIntent(getIntent());
        }
    }

    private void addIntent(@Nullable Intent intent) {
        if (intent != null) {
            intents.add(intent);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        intents.add(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        processNextIntent();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentRequest != null) {
            currentRequest.resultReceiver.send(RESULT_CANCELED, new Bundle());
            currentRequest = null;
        }

        for (Intent intent : intents) {
            Logger.verbose("Permission request cancelled", intent);

            ResultReceiver resultReceiver = intent.getParcelableExtra(RESULT_RECEIVER_EXTRA);
            if (resultReceiver != null) {
                resultReceiver.send(RESULT_CANCELED, new Bundle());
            }
        }

        intents.clear();
        this.requestPermissionLauncher.unregister();
    }

    private void processNextIntent() {
        if (intents.isEmpty() && currentRequest == null) {
            finish();
            return;
        }

        if (!isResumed || currentRequest != null) {
            return;
        }

        Intent intent = intents.remove(0);
        String permission = intent.getStringExtra(PERMISSION_EXTRA);
        ResultReceiver resultReceiver = intent.getParcelableExtra(RESULT_RECEIVER_EXTRA);

        if (permission == null || resultReceiver == null) {
            processNextIntent();
            return;
        }

        boolean beforeShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
        currentRequest = new PermissionRequest(
                permission,
                beforeShowRationale,
                System.currentTimeMillis(),
                resultReceiver
        );

        Logger.verbose("Requesting permission %s", permission);
        this.requestPermissionLauncher.launch(permission);
    }

    private void onPermissionResult(Boolean isGranted) {
        PermissionRequest request = currentRequest;
        if (request == null) {
            return;
        }
        currentRequest = null;

        boolean afterShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, request.permission);
        long time = System.currentTimeMillis() - request.startTime;
        Logger.verbose(
                "Received permission result: permission %s, shouldShowRequestPermissionRationale before: %s, shouldShowRequestPermissionRationale after: %s, granted: %s, time: %s",
                request.permission,
                request.startShowRationale,
                afterShowRationale,
                isGranted,
                time
        );

        Bundle bundle = new Bundle();
        if (isGranted) {
            bundle.putString(PERMISSION_STATUS_EXTRA, PermissionStatus.GRANTED.name());
        } else {
            bundle.putString(PERMISSION_STATUS_EXTRA, PermissionStatus.DENIED.name());
            if (time <= SILENT_DISMISS_MAX_TIME_MS && !afterShowRationale && !request.startShowRationale) {
                bundle.putBoolean(SILENTLY_DENIED_EXTRA, true);
            }
        }

        request.resultReceiver.send(RESULT_OK, bundle);
        processNextIntent();
    }

    private static class PermissionRequest {

        final String permission;
        final boolean startShowRationale;
        final long startTime;
        final ResultReceiver resultReceiver;

        public PermissionRequest(String permission,
                                 boolean startShowRationale,
                                 long startTime,
                                 ResultReceiver resultReceiver) {
            this.permission = permission;
            this.startShowRationale = startShowRationale;
            this.startTime = startTime;
            this.resultReceiver = resultReceiver;
        }

    }

    static boolean started = false;

    @MainThread
    public static void requestPermission(@NonNull Context context, @NonNull String permission, @NonNull Consumer<PermissionRequestResult> consumer) {
        context = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            handler.post(() -> consumer.accept(PermissionRequestResult.granted()));
            return;
        }

        ResultReceiver receiver = new ResultReceiver(handler) {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                started = false;
                if (resultCode == RESULT_OK) {
                    PermissionStatus status = PermissionStatus.valueOf(resultData.getString(PERMISSION_STATUS_EXTRA));

                    if (status == PermissionStatus.GRANTED) {
                        consumer.accept(PermissionRequestResult.granted());
                    } else {
                        boolean isSilentlyDenied = resultData.getBoolean(SILENTLY_DENIED_EXTRA, false);
                        consumer.accept(PermissionRequestResult.denied(isSilentlyDenied));
                    }
                } else {
                    consumer.accept(PermissionRequestResult.denied(false));
                }
            }
        };

        Intent startingIntent = new Intent(context, PermissionsActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setPackage(UAirship.getPackageName())
                .putExtra(PERMISSION_EXTRA, permission)
                .putExtra(RESULT_RECEIVER_EXTRA, receiver);

        context.startActivity(startingIntent);
    }

}
