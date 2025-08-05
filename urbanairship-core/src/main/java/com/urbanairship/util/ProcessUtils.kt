/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by Airship to remove work manager dependencies and added isMainProcess check.
 */
package com.urbanairship.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.urbanairship.UALog

/**
 * @hide
 */
internal object ProcessUtils {

    @JvmStatic
    public fun isMainProcess(application: Application): Boolean {
        val mainProcessName = application.applicationInfo.processName ?: application.packageName
        return getProcessName(application) == mainProcessName
    }

    /**
     * @return The name of the active process.
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    public fun getProcessName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 28) {
            return Application.getProcessName()
        }

        // Try using ActivityThread to determine the current process name.
        try {
            val activityThread = Class.forName(
                "android.app.ActivityThread", false, ProcessUtils::class.java.classLoader
            )
            val packageName: Any?
            if (Build.VERSION.SDK_INT >= 18) {
                val currentProcessName = activityThread.getDeclaredMethod("currentProcessName")
                currentProcessName.isAccessible = true
                packageName = currentProcessName.invoke(null)
            } else {
                val getActivityThread = activityThread.getDeclaredMethod(
                    "currentActivityThread"
                )
                getActivityThread.isAccessible = true
                val getProcessName = activityThread.getDeclaredMethod("getProcessName")
                getProcessName.isAccessible = true
                packageName = getProcessName.invoke(getActivityThread.invoke(null))
            }
            if (packageName is String) {
                return packageName
            }
        } catch (exception: Throwable) {
            UALog.d("Unable to check ActivityThread for processName", exception)
        }

        // Fallback to the most expensive way
        val pid = Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        return am?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
    }
}
