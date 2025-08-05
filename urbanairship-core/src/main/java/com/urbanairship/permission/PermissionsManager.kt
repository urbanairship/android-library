/* Copyright Airship and Contributors */
package com.urbanairship.permission

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.core.util.Consumer
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.UALog
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleActivityListener
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Airship permission manager.
 *
 * Airship will provide the default handling for notifications. Other [Permission] needs to
 * be handled by the application by setting a delegate with [PermissionDelegate].
 */
public class PermissionsManager internal constructor(
    private val context: Context,
    private val activityMonitor: ActivityMonitor,
    private val systemSettingsLauncher: SystemSettingsLauncher,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {

    private val permissionsJob = SupervisorJob()
    private val permissionsScope = CoroutineScope(dispatcher + permissionsJob)

    private val permissionDelegateMap: MutableMap<Permission, PermissionDelegate?> = mutableMapOf()
    private val airshipEnablers: MutableList<Consumer<Permission>> = CopyOnWriteArrayList()
    private val onPermissionStatusChangedListeners: MutableList<OnPermissionStatusChangedListener> = CopyOnWriteArrayList()

    /// All modified on the main thread
    private val permissionStatusMap = MutableStateFlow(emptyMap<Permission, PermissionStatus>())
    private val pendingRequestResults: MutableMap<Permission, Flow<PermissionRequestResult>> = mutableMapOf()
    private val pendingCheckResults: MutableMap<Permission, Flow<PermissionStatus>> = mutableMapOf()

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(context: Context): this(context, GlobalActivityMonitor.shared(context), SystemSettingsLauncher())

    init {
        permissionsScope.launch {
            activityMonitor.resumedActivities().collect { activity ->
                if (activity.javaClass != PermissionsActivity::class.java) {
                    configuredPermissions.forEach { suspendingCheckPermissionStatus(it) }
                }
            }
        }
    }


    @MainThread
    private fun updatePermissionStatus(permission: Permission, status: PermissionStatus) {
        val previous = permissionStatusMap.getAndUpdate {
            it.toMutableMap().apply { put(permission, status) }
        }

        if (previous[permission] != null && previous[permission] != status) {
            for (listener in this.onPermissionStatusChangedListeners) {
                listener.onPermissionStatusChanged(permission, status)
            }
        }
    }

    /**
     * Gets the set of permissions that have a delegate.
     *
     * @return The set of configured permissions.
     */
    public val configuredPermissions: Set<Permission>
        get() {
            synchronized(permissionDelegateMap) {
                return permissionDelegateMap.keys
            }
        }

    /**
     * Sets a delegate to handle permissions.
     *
     * @param permission The permission.
     * @param delegate The delegate.
     */
    public fun setPermissionDelegate(permission: Permission, delegate: PermissionDelegate?) {
        synchronized(permissionDelegateMap) {
            permissionDelegateMap[permission] = delegate
            checkPermissionStatus(permission)
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addAirshipEnabler(onEnable: Consumer<Permission>) {
        airshipEnablers.add(onEnable)
    }

    /**
     * Adds a permission status changed listener.
     *
     * @param listener The listener to remove.
     */
    public fun addOnPermissionStatusChangedListener(listener: OnPermissionStatusChangedListener) {
        onPermissionStatusChangedListeners.add(listener)
    }

    /**
     * Removes a permission status changed listener.
     *
     * @param listener The listener to remove.
     */
    public fun removeOnPermissionStatusChangedListener(listener: OnPermissionStatusChangedListener) {
        onPermissionStatusChangedListeners.remove(listener)
    }

    /**
     * Checks the current permission status.
     *
     * @param permission The permission.
     * @param callback The callback with the result.
     */
    public fun checkPermissionStatus(
        permission: Permission,
        callback: Consumer<PermissionStatus?>
    ) {
        checkPermissionStatus(permission).addResultCallback {
            callback.accept(it)
        }
    }

    /**
     * Checks the current permission status.
     *
     * @param permission The permission.
     * @return A pending result.
     */
    public fun checkPermissionStatus(permission: Permission): PendingResult<PermissionStatus?> {
        val pendingResult = PendingResult<PermissionStatus?>()
        permissionsScope.launch {
            pendingResult.setResult(suspendingCheckPermissionStatus(permission))
        }
        return pendingResult
    }

    /**
     * Returns a flow of permission status changes for the specified permission.
     *
     * @param permission The permission.
     * @return A pending result.
     */
    public fun permissionsUpdate(permission: Permission): Flow<PermissionStatus> {
        return permissionStatusMap.map { it[permission] }.filterNotNull().distinctUntilChanged()
    }

    /**
     * Requests a permission.
     *
     * @param permission The permission.
     * @param enableAirshipUsageOnGrant If granted, any Airship features that need the permission will
     * be enabled, e.g., enabling [com.urbanairship.PrivacyManager.Feature.PUSH] and
     * [com.urbanairship.push.PushManager.setUserNotificationsEnabled] if the push permission
     * is granted.
     * @param callback The callback.
     */
    @JvmOverloads
    public fun requestPermission(
        permission: Permission,
        enableAirshipUsageOnGrant: Boolean = false,
        fallback: PermissionPromptFallback = PermissionPromptFallback.None,
        callback: Consumer<PermissionRequestResult?>
    ) {
        permissionsScope.launch {
            suspendingRequestPermission(permission, enableAirshipUsageOnGrant)
        }

        requestPermission(permission, enableAirshipUsageOnGrant, fallback).addResultCallback {
            callback.accept(it)
        }
    }

    /**
     * Requests a permission. If a delegate is not set to handle the permission [PermissionStatus.NOT_DETERMINED] will
     * be returned.
     *
     * @param permission The permission.
     * @param enableAirshipUsageOnGrant If granted, any Airship features that need the permission will
     * be enabled, e.g., enabling [com.urbanairship.PrivacyManager.Feature.PUSH] and
     * [com.urbanairship.push.PushManager.setUserNotificationsEnabled] if the push permission
     * is granted.
     * @return A pending result.
     */
    @JvmOverloads
    public fun requestPermission(
        permission: Permission,
        enableAirshipUsageOnGrant: Boolean = false,
        fallback: PermissionPromptFallback = PermissionPromptFallback.None,
    ): PendingResult<PermissionRequestResult?> {
        val pendingResult = PendingResult<PermissionRequestResult?>()
        permissionsScope.launch {
            pendingResult.setResult(
                result = suspendingRequestPermission(permission, enableAirshipUsageOnGrant, fallback)
            )

        }
        return pendingResult
    }

    /**
     * Requests a permission. If a delegate is not set to handle the permission [PermissionStatus.NOT_DETERMINED] will
     * be returned.
     *
     * @param permission The permission.
     * @param enableAirshipUsageOnGrant If granted, any Airship features that need the permission will
     * be enabled, e.g., enabling [com.urbanairship.PrivacyManager.Feature.PUSH] and
     * [com.urbanairship.push.PushManager.setUserNotificationsEnabled] if the push permission
     * is granted.
     * @return a [PermissionRequestResult].
     */
    public suspend fun suspendingRequestPermission(
        permission: Permission,
        enableAirshipUsageOnGrant: Boolean = false,
        fallback: PermissionPromptFallback = PermissionPromptFallback.None
    ): PermissionRequestResult {
        val result = withContext(Dispatchers.Main.immediate) {
            val delegate = getDelegate(permission)
                ?: return@withContext PermissionRequestResult.notDetermined()

            val existing = pendingRequestResults[permission]
            if (existing != null) {
                return@withContext existing.first()
            }

            UALog.d { "Requesting permission for $permission" }

            val flow = delegate.requestPermissionFlow(context, scope = permissionsScope)
            pendingRequestResults[permission] = flow

            val result = flow.first()
            updatePermissionStatus(permission, result.permissionStatus)

            if (pendingRequestResults[permission] == flow) {
                pendingRequestResults.remove(permission)
            }

            UALog.d { "Permission $permission request result: $result" }

            if (result.permissionStatus == PermissionStatus.GRANTED && enableAirshipUsageOnGrant) {
                airshipEnablers.forEach { it.accept(permission) }
            }

            return@withContext result
        }

        if (!result.isSilentlyDenied) {
            return result
        }

        return when(fallback) {
            is PermissionPromptFallback.None -> result
            is PermissionPromptFallback.Callback -> {
                fallback.callback()
                val updatedStatus = suspendingCheckPermissionStatus(permission)
                if (updatedStatus == PermissionStatus.GRANTED) {
                    PermissionRequestResult.granted()
                } else {
                    result
                }
            }
            is PermissionPromptFallback.SystemSettings -> {
                if (launchSettingsForPermission(permission)) {
                    waitForResume()
                    if (suspendingCheckPermissionStatus(permission) == PermissionStatus.GRANTED) {
                        PermissionRequestResult.granted()
                    } else {
                        result
                    }
                } else {
                    result
                }
            }
        }
    }

    /**
     * Checks the current permission status.
     *
     * @param permission The permission.
     * @return A [PermissionStatus].
     */
    public suspend fun suspendingCheckPermissionStatus(
        permission: Permission,
    ): PermissionStatus {
        return withContext(Dispatchers.Main.immediate) {
            val delegate = getDelegate(permission)
                ?: return@withContext PermissionStatus.NOT_DETERMINED

            val existing = pendingCheckResults[permission]
            if (existing != null) {
                return@withContext existing.first()
            }

            UALog.d { "Checking permission status for $permission" }

            val flow = delegate.checkPermissionFlow(context, scope = permissionsScope)
            pendingCheckResults[permission] = flow

            val result = flow.first()
            updatePermissionStatus(permission, result)

            if (pendingCheckResults[permission] == flow) {
                pendingCheckResults.remove(permission)
            }

            UALog.d { "Permission $permission request result: $result" }

            return@withContext result
        }
    }

    private fun getDelegate(permission: Permission): PermissionDelegate? {
        synchronized(permissionDelegateMap) {
            return permissionDelegateMap[permission]
        }
    }

    @MainThread
    private fun launchSettingsForPermission(permission: Permission): Boolean {
        return if (permission == Permission.DISPLAY_NOTIFICATIONS) {
            systemSettingsLauncher.openAppNotificationSettings(context)
        } else {
            systemSettingsLauncher.openAppSettings(context)
        }
    }

    private suspend fun waitForResume() {
        activityMonitor.resumedActivities().first()
    }
}

private fun PermissionDelegate.requestPermissionFlow(context: Context, scope: CoroutineScope): Flow<PermissionRequestResult> {
    val stateFlow = MutableStateFlow<PermissionRequestResult?>(null)
    scope.launch {
        var isResumed = AtomicBoolean(false)
        stateFlow.value = suspendCoroutine { continuation ->
            requestPermission(context) { requestResult ->
                if (isActive && isResumed.compareAndSet(false, true)) {
                    continuation.resume(requestResult)
                }
            }
        }
    }
    return stateFlow.mapNotNull { it }
}

private fun PermissionDelegate.checkPermissionFlow(context: Context, scope: CoroutineScope): Flow<PermissionStatus> {
    val stateFlow = MutableStateFlow<PermissionStatus?>(null)
    scope.launch {
        var isResumed = AtomicBoolean(false)
        stateFlow.value = suspendCancellableCoroutine { continuation ->
            checkPermissionStatus(context) { permissionStatus ->
                if (isActive && isResumed.compareAndSet(false, true)) {
                    continuation.resume(permissionStatus)
                }
            }
        }
    }
    return stateFlow.mapNotNull { it }
}

private suspend fun ActivityMonitor.resumedActivities() = callbackFlow<Activity> {
    val listener = object : SimpleActivityListener() {
        override fun onActivityResumed(activity: Activity) {
            this@callbackFlow.trySend(activity)
        }
    }

    addActivityListener(listener)

    awaitClose {
        removeActivityListener(listener)
    }
}
