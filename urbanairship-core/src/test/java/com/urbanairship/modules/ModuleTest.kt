/* Copyright Airship and Contributors */
package com.urbanairship.modules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.modules.Module.Companion.singleComponent
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ModuleTest {

    private var context: Context = ApplicationProvider.getApplicationContext()
    private var dataStore = PreferenceDataStore.inMemoryStore(context)

    @Test
    public fun testGetComponents() {
        val component = TestComponent(context, dataStore)
        val module = singleComponent(component)
        TestCase.assertEquals(setOf(component), module.components)
    }

    public class TestComponent(context: Context, dataStore: PreferenceDataStore) :
        AirshipComponent(context, dataStore)
}
