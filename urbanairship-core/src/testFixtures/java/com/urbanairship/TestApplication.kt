/* Copyright Airship and Contributors */
package com.urbanairship

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Method
import org.robolectric.TestLifecycleApplication

@SuppressLint("VisibleForTests")
public class TestApplication public constructor() : Application(), TestLifecycleApplication {


    override fun onCreate() {
        super.onCreate()
    }

    override fun onTerminate() {
        super.onTerminate()
    }

    override fun beforeTest(method: Method?) {
    }

    override fun prepareTest(test: Any?) {
    }

    override fun afterTest(method: Method?) {
    }

    public companion object {
        public fun getApplication(): TestApplication {
            return ApplicationProvider.getApplicationContext<Context>() as TestApplication
        }
    }
}
