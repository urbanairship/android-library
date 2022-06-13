/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

/**
 * An activity that is used by the Action framework to enable starting other activities
 * for results. Ordinarily this class should not be instantiated directly. Instead, see
 * {@link com.urbanairship.util.HelperActivity#startActivityForResult(android.content.Context, android.content.Intent)}.
 *
 * @deprecated Will be removed in SDK 17. Use your own activity to request results.
 */
@Deprecated
public class HelperActivity extends AppCompatActivity {

    /**
     * Intent extra holding the permissions.
     */
    @NonNull
    public static final String PERMISSIONS_EXTRA = "com.urbanairship.util.helperactivity.PERMISSIONS_EXTRA";

    /**
     * Intent extra holding an activity result receiver.
     */
    @NonNull
    public static final String RESULT_RECEIVER_EXTRA = "com.urbanairship.util.helperactivity.RESULT_RECEIVER_EXTRA";

    /**
     * Intent extra holding activity result intent.
     */
    @NonNull
    public static final String RESULT_INTENT_EXTRA = "com.urbanairship.util.helperactivity.RESULT_INTENT_EXTRA";

    /**
     * Intent extra holding the intent for an activity to be started.
     */
    @NonNull
    public static final String START_ACTIVITY_INTENT_EXTRA = "com.urbanairship.util.START_ACTIVITY_INTENT_EXTRA";

    private List<Intent> intents = new ArrayList<>();

    @Override
    public final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("HelperActivity - unable to create activity, takeOff not called.");
            finish();
            return;
        }

        if (savedInstanceState == null) {
            addIntent(getIntent());
            processNextIntent();
        }
    }

    private void addIntent(@Nullable Intent intent) {
        if (intent != null) {
            intents.add(intent);
        }
    }

    private void processNextIntent() {
        if (intents.isEmpty()) {
            finish();
            return;
        }

        Intent intent = intents.get(0);
        Intent startActivityIntent = intent.getParcelableExtra(START_ACTIVITY_INTENT_EXTRA);
        String[] permissions = intent.getStringArrayExtra(PERMISSIONS_EXTRA);

        if (startActivityIntent != null) {
            startActivityForResult(startActivityIntent, 0);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permissions != null) {
            requestPermissions(permissions, 0);
        } else {
            Logger.error("HelperActivity - Started without START_ACTIVITY_INTENT_EXTRA or PERMISSIONS_EXTRA extra.");
            intents.remove(0);
            processNextIntent();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        intents.add(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (intents.isEmpty()) {
            finish();
            return;
        }

        ResultReceiver resultReceiver = intents.remove(0).getParcelableExtra(RESULT_RECEIVER_EXTRA);
        if (resultReceiver != null) {
            Bundle bundledData = new Bundle();
            bundledData.putParcelable(RESULT_INTENT_EXTRA, data);
            resultReceiver.send(resultCode, bundledData);
        }

        super.onActivityResult(requestCode, resultCode, data);
        processNextIntent();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (intents.isEmpty()) {
            finish();
            return;
        }

        ResultReceiver resultReceiver = intents.remove(0).getParcelableExtra(RESULT_RECEIVER_EXTRA);

        if (resultReceiver != null) {
            Bundle bundledData = new Bundle();
            bundledData.putIntArray(RESULT_INTENT_EXTRA, grantResults);
            resultReceiver.send(Activity.RESULT_OK, bundledData);
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        processNextIntent();
    }

    @MainThread
    private static void requestPermissions(@NonNull Context context, @NonNull String[] permissions, @Nullable Consumer<int[]> consumer) {
        context = context.getApplicationContext();
        boolean permissionsDenied = false;

        final int[] result = new int[permissions.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = ContextCompat.checkSelfPermission(context, permissions[i]);
            if (result[i] == PackageManager.PERMISSION_DENIED) {
                permissionsDenied = true;
            }
        }

        Handler handler = new Handler(Looper.getMainLooper());

        if (!permissionsDenied || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (consumer != null) {
                handler.post(() -> consumer.accept(result));
            }
            return;
        }

        ResultReceiver receiver = new ResultReceiver(handler) {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                int[] receiverResults = resultData.getIntArray(HelperActivity.RESULT_INTENT_EXTRA);
                if (receiverResults == null) {
                    receiverResults = new int[0];
                }

                if (consumer != null) {
                    consumer.accept(receiverResults);
                }
            }
        };

        Intent startingIntent = new Intent(context, HelperActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(UAirship.getPackageName())
                .putExtra(HelperActivity.PERMISSIONS_EXTRA, permissions)
                .putExtra(HelperActivity.RESULT_RECEIVER_EXTRA, receiver);

        context.startActivity(startingIntent);
    }

    /**
     * Requests permissions.
     *
     * @param context The application context.
     * @param permissions The permissions to request.
     * @return The result from requesting permissions.
     */
    @WorkerThread
    @NonNull
    public static int[] requestPermissions(@NonNull Context context, @NonNull String... permissions) {
        final int[] result = new int[permissions.length];

        synchronized (result) {
            requestPermissions(context, permissions, receiverResults -> {
                if (receiverResults != null && receiverResults.length == result.length) {
                    System.arraycopy(receiverResults, 0, result, 0, result.length);
                }

                synchronized (result) {
                    result.notify();
                }
            });

            try {
                result.wait();
            } catch (InterruptedException e) {
                Logger.error(e, "Thread interrupted when waiting for result from activity.");
                Thread.currentThread().interrupt();
            }
        }

        return result;
    }

    /**
     * Starts an activity for a result.
     *
     * @param context The application context.
     * @param intent The activity to start.
     * @return The result of the activity in a ActivityResult object.
     */
    @NonNull
    @WorkerThread
    public static ActivityResult startActivityForResult(@NonNull Context context, @NonNull Intent intent) {
        context = context.getApplicationContext();
        final ActivityResult result = new ActivityResult();

        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                result.setResult(resultCode, (Intent) resultData.getParcelable(HelperActivity.RESULT_INTENT_EXTRA));
                synchronized (result) {
                    result.notify();
                }
            }
        };

        Intent startingIntent = new Intent(context, HelperActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(UAirship.getPackageName())
                .putExtra(HelperActivity.START_ACTIVITY_INTENT_EXTRA, intent)
                .putExtra(HelperActivity.RESULT_RECEIVER_EXTRA, receiver);

        synchronized (result) {
            context.startActivity(startingIntent);
            try {
                result.wait();
            } catch (InterruptedException e) {
                Logger.error(e, "Thread interrupted when waiting for result from activity.");
                Thread.currentThread().interrupt();
                return new ActivityResult();
            }
        }

        return result;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (Intent intent : intents) {
            ResultReceiver resultReceiver = intent.getParcelableExtra(RESULT_RECEIVER_EXTRA);
            if (resultReceiver != null) {
                resultReceiver.send(RESULT_CANCELED, new Bundle());
            }
        }

        intents.clear();
    }

    /**
     * Wraps the result code and data from starting an activity
     * for a result.
     */
    public static class ActivityResult {

        private int resultCode = Activity.RESULT_CANCELED;
        private Intent intent;

        /**
         * Gets the result intent.
         *
         * @return The result intent from the activity.
         */
        @Nullable
        public Intent getIntent() {
            return intent;
        }

        /**
         * Gets the result code from the activity.
         *
         * @return The result code from the activity.
         */
        public int getResultCode() {
            return resultCode;
        }

        private void setResult(int resultCode, @Nullable Intent intent) {
            this.resultCode = resultCode;
            this.intent = intent;
        }

    }

}
