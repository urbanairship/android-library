/* Copyright Airship and Contributors */
package com.urbanairship.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.util.Consumer
import com.urbanairship.Autopilot
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.util.Clock

/**
 * Activity that requests permissions.
 *
 * The activity is currently restricted to only requesting a single permission at a time.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PermissionsActivity public constructor(
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : AppCompatActivity() {

    private val intents = mutableListOf<Intent>()
    private var currentRequest: PermissionRequest? = null
    private var isResumed = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> this.onPermissionResult(isGranted) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Autopilot.automaticTakeOff(application)

        if (savedInstanceState == null) {
            intent?.let { intents.add(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intents.add(intent)
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        processNextIntent()
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
    }

    override fun onDestroy() {
        super.onDestroy()
        currentRequest?.resultReceiver?.send(RESULT_CANCELED, bundleOf())
        currentRequest = null

        for (intent in intents) {
            UALog.v("Permission request cancelled", intent)

            val resultReceiver = intent.getParcelableExtra<ResultReceiver>(RESULT_RECEIVER_EXTRA)
            resultReceiver?.send(RESULT_CANCELED, bundleOf())
        }

        intents.clear()
        requestPermissionLauncher.unregister()
    }

    private fun processNextIntent() {
        if (intents.isEmpty() && currentRequest == null) {
            finish()
            return
        }

        if (!isResumed || currentRequest != null) {
            return
        }

        val intent = intents.removeAt(0)
        val permission = intent.getStringExtra(PERMISSION_EXTRA)
        val resultReceiver = intent.getParcelableExtra<ResultReceiver>(
            RESULT_RECEIVER_EXTRA
        )

        if (permission == null || resultReceiver == null) {
            processNextIntent()
            return
        }

        val beforeShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        currentRequest = PermissionRequest(
            permission = permission,
            startShowRationale = beforeShowRationale,
            startTime = clock.currentTimeMillis(),
            resultReceiver = resultReceiver
        )

        UALog.v("Requesting permission %s", permission)
        requestPermissionLauncher.launch(permission)
    }

    private fun onPermissionResult(isGranted: Boolean) {
        val request = currentRequest ?: return
        currentRequest = null

        val afterShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, request.permission)
        val time = clock.currentTimeMillis() - request.startTime
        UALog.v(
            "Received permission result: permission ${request.permission}, " +
                    "shouldShowRequestPermissionRationale before: ${request.startShowRationale}, " +
                    "shouldShowRequestPermissionRationale after: $afterShowRationale, " +
                    "granted: $isGranted, time: $time"
        )

        val bundle = Bundle()
        if (isGranted) {
            bundle.putString(PERMISSION_STATUS_EXTRA, PermissionStatus.GRANTED.name)
        } else {
            bundle.putString(PERMISSION_STATUS_EXTRA, PermissionStatus.DENIED.name)
            if (time <= SILENT_DISMISS_MAX_TIME_MS && !afterShowRationale && !request.startShowRationale) {
                bundle.putBoolean(SILENTLY_DENIED_EXTRA, true)
            }
        }

        request.resultReceiver.send(RESULT_OK, bundle)
        processNextIntent()
    }

    private class PermissionRequest(
        val permission: String,
        val startShowRationale: Boolean,
        val startTime: Long,
        val resultReceiver: ResultReceiver
    )

    public companion object {

        private const val PERMISSION_EXTRA = "PERMISSION_EXTRA"
        private const val RESULT_RECEIVER_EXTRA = "RESULT_RECEIVER_EXTRA"
        private const val PERMISSION_STATUS_EXTRA = "PERMISSION_STATUS"
        private const val SILENTLY_DENIED_EXTRA = "SILENTLY_DENIED"

        // The only way to know about a silent dismiss if both before and after showRationale are false. However
        // on Android 11+ you can press back to skip or touch outside which will result in a none silent false/false.
        // This amount of time is not a guarantee to catch all but it helps reduce the number false positives.
        private const val SILENT_DISMISS_MAX_TIME_MS: Long = 2000

        @MainThread
        public fun requestPermission(
            context: Context,
            permission: String,
            consumer: Consumer<PermissionRequestResult>
        ) {
            val applicationContext = context.applicationContext
            val handler = Handler(Looper.getMainLooper())
            if (ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED) {
                handler.post { consumer.accept(PermissionRequestResult.granted()) }
                return
            }

            val receiver: ResultReceiver = object : ResultReceiver(handler) {
                public override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                    when (resultCode) {
                        RESULT_OK -> {
                            val status = resultData
                                .getString(PERMISSION_STATUS_EXTRA)
                                ?.let(PermissionStatus::fromString)

                            when(status) {
                                PermissionStatus.GRANTED -> consumer.accept(PermissionRequestResult.granted())
                                else -> {
                                    val isSilentlyDenied = resultData.getBoolean(SILENTLY_DENIED_EXTRA, false)
                                    consumer.accept(PermissionRequestResult.denied(isSilentlyDenied))
                                }
                            }
                        }
                        else -> consumer.accept(PermissionRequestResult.denied(false))
                    }
                }
            }

            val startingIntent = Intent(context, PermissionsActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setPackage(UAirship.getPackageName())
                .putExtra(PERMISSION_EXTRA, permission)
                .putExtra(RESULT_RECEIVER_EXTRA, receiver)

            context.startActivity(startingIntent)
        }
    }
}
