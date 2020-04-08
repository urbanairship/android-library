/* Copyright Airship and Contributors */

package com.urbanairship.modules;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.actions.ActionRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ModuleTest extends BaseTestCase {

    private Context context;
    private PreferenceDataStore dataStore;
    private ActionRegistry actionRegistry;

    @Before
    public void setup() {
        this.context = ApplicationProvider.getApplicationContext();
        this.dataStore = new PreferenceDataStore(context);
        this.actionRegistry = mock(ActionRegistry.class);
    }

    @Test
    public void testGetComponents() {
        TestComponent component = new TestComponent(context, dataStore);
        Module module = Module.singleComponent(component, 0);
        assertEquals(Collections.singleton(component), module.getComponents());
    }

    @Test
    public void testRegisterActions() {
        TestComponent component = new TestComponent(context, dataStore);
        Module module = Module.singleComponent(component, 100);

        module.registerActions(context, actionRegistry);
        verify(actionRegistry).registerActions(context, 100);
    }

    public static class TestComponent extends AirshipComponent {
        public TestComponent(@NonNull Context context, @NonNull PreferenceDataStore dataStore) {
            super(context, dataStore);
        }
    }
}
