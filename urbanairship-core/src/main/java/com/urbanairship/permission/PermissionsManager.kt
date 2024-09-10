/* Copyright Airship and Contributors */
package com.urbanairship.permission

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.core.util.Consumer
import com.urbanairship.PendingResult
import com.urbanairship.UALog
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleActivityListener
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Airship permission manager.
 *
 * Airship will provide the default handling for notifications. Other [Permission] needs to
 * be handled by the application by setting a delegate with [PermissionDelegate].
 */
public class PermissionsManager internal constructor(
    private var context: Context,
    activityMonitor: ActivityMonitor
) {
    private val permissionsJob = SupervisorJob()
    private val permissionsScope = CoroutineScope(Dispatchers.Main + permissionsJob)

    private val permissionDelegateMap: MutableMap<Permission, PermissionDelegate?> = mutableMapOf()
    private val airshipEnablers: MutableList<Consumer<Permission>> = CopyOnWriteArrayList()
    private val onPermissionStatusChangedListeners: MutableList<OnPermissionStatusChangedListener> = CopyOnWriteArrayList()

    /// All modified on the main thread
    private val permissionStatusMap: MutableMap<Permission, PermissionStatus> = mutableMapOf()
    private val pendingRequestResults: MutableMap<Permission, Flow<PermissionRequestResult>> = mutableMapOf()
    private val pendingCheckResults: MutableMap<Permission, Flow<PermissionStatus>> = mutableMapOf()

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(context: Context): this(context, GlobalActivityMonitor.shared(context))

    init {
        activityMonitor.addActivityListener(object : SimpleActivityListener() {
            override fun onActivityResumed(activity: Activity) {
                updatePermissions()
            }
        })
    }

    @MainThread
    private fun updatePermissions() {
        for (permission in configuredPermissions) {
            checkPermissionStatus(permission) { status ->
                updatePermissionStatus(permission, status)
            }
        }
    }

    @MainThread
    private fun updatePermissionStatus(permission: Permission, status: PermissionStatus) {
        val previous = permissionStatusMap[permission]
        if (previous != null && previous != status) {
            for (listener in this.onPermissionStatusChangedListeners) {
                listener.onPermissionStatusChanged(permission, status)
            }
        }
        permissionStatusMap[permission] = status
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
        callback: Consumer<PermissionStatus>
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
            pendingResult.result = suspendingCheckPermissionRequest(permission)
        }
        return pendingResult
    }

    /**
     * Requests a permission.
     *
     * @param permission The permission.
     * @param callback The callback.
     */
    public fun requestPermission(
        permission: Permission, callback: Consumer<PermissionRequestResult?>
    ) {
        requestPermission(permission, false, callback)
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
    public fun requestPermission(
        permission: Permission,
        enableAirshipUsageOnGrant: Boolean,
        callback: Consumer<PermissionRequestResult?>
    ) {
        requestPermission(permission, enableAirshipUsageOnGrant).addResultCallback {
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
        enableAirshipUsageOnGrant: Boolean = false
    ): PendingResult<PermissionRequestResult?> {
        val pendingResult = PendingResult<PermissionRequestResult?>()
        permissionsScope.launch {
            pendingResult.result =
                suspendingPermissionRequest(permission, enableAirshipUsageOnGrant)
        }
        return pendingResult
    }

    private suspend fun suspendingPermissionRequest(
        permission: Permission,
        enableAirshipUsageOnGrant: Boolean = false
    ): PermissionRequestResult {
        return withContext(Dispatchers.Main.immediate) {
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
    }

    private suspend fun suspendingCheckPermissionRequest(
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

}


private fun PermissionDelegate.requestPermissionFlow(context: Context, scope: CoroutineScope): Flow<PermissionRequestResult> {
    val stateFlow = MutableStateFlow<PermissionRequestResult?>(null)
    scope.launch {
        stateFlow.value = suspendCoroutine { continuation ->
            requestPermission(context) { requestResult ->
                continuation.resume(requestResult)
            }
        }
    }
    return stateFlow.mapNotNull { it }
}

private fun PermissionDelegate.checkPermissionFlow(context: Context, scope: CoroutineScope): Flow<PermissionStatus> {
    val stateFlow = MutableStateFlow<PermissionStatus?>(null)
    scope.launch {
        stateFlow.value = suspendCoroutine { continuation ->
            checkPermissionStatus(context) { permissionStatus ->
                continuation.resume(permissionStatus)
            }
        }
    }
    return stateFlow.mapNotNull { it }
}
