package com.urbanairship;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PrivacyManagerTest extends BaseTestCase {

    private PrivacyManager privacyManager;
    private PreferenceDataStore dataStore;

    @Before
    public void setup() {
        this.dataStore = PreferenceDataStore.inMemoryStore(TestApplication.getApplication());
        this.privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_NONE);
    }

    @Test
    public void testDefaults() {
        assertEquals(PrivacyManager.FEATURE_NONE, privacyManager.getEnabledFeatures());

        privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);
        assertEquals(PrivacyManager.FEATURE_ALL, privacyManager.getEnabledFeatures());
    }

    @Test
    public void testOverridesPersisted() {
        assertEquals(PrivacyManager.FEATURE_NONE, privacyManager.getEnabledFeatures());
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_ALL);

        privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_NONE);
        assertEquals(PrivacyManager.FEATURE_ALL, privacyManager.getEnabledFeatures());
    }

    @Test
    public void testEnable() {
        privacyManager.enable(PrivacyManager.FEATURE_CONTACTS, PrivacyManager.FEATURE_CHAT);
        assertEquals(PrivacyManager.FEATURE_CONTACTS | PrivacyManager.FEATURE_CHAT, privacyManager.getEnabledFeatures());

        privacyManager.enable(PrivacyManager.FEATURE_CHAT);
        assertEquals(PrivacyManager.FEATURE_CONTACTS | PrivacyManager.FEATURE_CHAT, privacyManager.getEnabledFeatures());

        privacyManager.enable(PrivacyManager.FEATURE_NONE);
        assertEquals(PrivacyManager.FEATURE_CONTACTS | PrivacyManager.FEATURE_CHAT, privacyManager.getEnabledFeatures());

        privacyManager.enable(PrivacyManager.FEATURE_ANALYTICS);
        assertEquals(PrivacyManager.FEATURE_CONTACTS | PrivacyManager.FEATURE_CHAT | PrivacyManager.FEATURE_ANALYTICS, privacyManager.getEnabledFeatures());

        privacyManager.enable(PrivacyManager.FEATURE_ALL);
        assertEquals(PrivacyManager.FEATURE_ALL, privacyManager.getEnabledFeatures());
    }

    @Test
    public void testDisable() {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_CONTACTS, PrivacyManager.FEATURE_CHAT);

        privacyManager.disable(PrivacyManager.FEATURE_NONE);
        assertEquals(PrivacyManager.FEATURE_CONTACTS | PrivacyManager.FEATURE_CHAT, privacyManager.getEnabledFeatures());

        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);
        assertEquals(PrivacyManager.FEATURE_CONTACTS | PrivacyManager.FEATURE_CHAT, privacyManager.getEnabledFeatures());

        privacyManager.disable(PrivacyManager.FEATURE_CONTACTS);
        assertEquals(PrivacyManager.FEATURE_CHAT , privacyManager.getEnabledFeatures());

        privacyManager.disable(PrivacyManager.FEATURE_ALL);
        assertEquals(PrivacyManager.FEATURE_NONE, privacyManager.getEnabledFeatures());
    }

    @Test
    public void testIsEnabled() {
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
        assertFalse(privacyManager.isEnabled(PrivacyManager.FEATURE_CHAT));
        assertFalse(privacyManager.isEnabled(PrivacyManager.FEATURE_ALL));
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_NONE));

        privacyManager.enable(PrivacyManager.FEATURE_CHAT);
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_CHAT));
        assertFalse(privacyManager.isEnabled(PrivacyManager.FEATURE_ALL));
        assertFalse(privacyManager.isEnabled(PrivacyManager.FEATURE_NONE));
        assertFalse(privacyManager.isEnabled(PrivacyManager.FEATURE_CHAT, PrivacyManager.FEATURE_ANALYTICS));

        privacyManager.enable(PrivacyManager.FEATURE_ALL);
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_CHAT));
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS, PrivacyManager.FEATURE_ANALYTICS));
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_ALL));
        assertFalse(privacyManager.isEnabled(PrivacyManager.FEATURE_NONE));
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS, PrivacyManager.FEATURE_NONE));
    }

    @Test
    public void testIsAnyFeatureEnabled() {
        assertFalse(privacyManager.isAnyFeatureEnabled());

        privacyManager.enable(PrivacyManager.FEATURE_CHAT);
        assertTrue(privacyManager.isAnyFeatureEnabled());

        privacyManager.disable(PrivacyManager.FEATURE_CHAT);
        assertFalse(privacyManager.isAnyFeatureEnabled());
    }

    @Test
    public void testIsAnyEnabled() {
        assertFalse(privacyManager.isAnyEnabled(PrivacyManager.FEATURE_ANALYTICS));

        privacyManager.enable(PrivacyManager.FEATURE_CHAT, PrivacyManager.FEATURE_PUSH);
        assertFalse(privacyManager.isAnyEnabled(PrivacyManager.FEATURE_ANALYTICS));
        assertTrue(privacyManager.isAnyEnabled(PrivacyManager.FEATURE_ANALYTICS, PrivacyManager.FEATURE_PUSH));
    }

    @Test
    public void testMigrateDataCollectionEnabled() {
        assertEquals(PrivacyManager.FEATURE_NONE, privacyManager.getEnabledFeatures());
        dataStore.put(PrivacyManager.DATA_COLLECTION_ENABLED_KEY, true);

        privacyManager.migrateData();
        assertEquals(PrivacyManager.FEATURE_ALL, privacyManager.getEnabledFeatures());

        assertFalse(dataStore.isSet(PrivacyManager.DATA_COLLECTION_ENABLED_KEY));

        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
        privacyManager.migrateData();
        assertEquals(PrivacyManager.FEATURE_NONE, privacyManager.getEnabledFeatures());
    }

    @Test
    public void testMigrateDataCollectionDisabled() {
        privacyManager.enable(PrivacyManager.FEATURE_ALL);
        dataStore.put(PrivacyManager.DATA_COLLECTION_ENABLED_KEY, false);

        privacyManager.migrateData();
        assertEquals(PrivacyManager.FEATURE_NONE, privacyManager.getEnabledFeatures());

        assertFalse(dataStore.isSet(PrivacyManager.DATA_COLLECTION_ENABLED_KEY));

        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
        privacyManager.migrateData();
        assertEquals(PrivacyManager.FEATURE_NONE, privacyManager.getEnabledFeatures());
    }

    @Test
    public void testMigrateModuleEnableFlagsWhenDisabled() {
        dataStore.put(PrivacyManager.CHAT_ENABLED_KEY, false);
        dataStore.put(PrivacyManager.PUSH_ENABLED_KEY, false);
        dataStore.put(PrivacyManager.ANALYTICS_ENABLED_KEY, false);
        dataStore.put(PrivacyManager.PUSH_TOKEN_REGISTRATION_ENABLED_KEY, false);
        dataStore.put(PrivacyManager.IAA_ENABLED_KEY, false);

        privacyManager.enable(PrivacyManager.FEATURE_ALL);
        privacyManager.migrateData();

        assertFalse(dataStore.isSet(PrivacyManager.CHAT_ENABLED_KEY));
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_ENABLED_KEY));
        assertFalse(dataStore.isSet(PrivacyManager.ANALYTICS_ENABLED_KEY));
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_TOKEN_REGISTRATION_ENABLED_KEY));
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_ENABLED_KEY));

        assertFalse(privacyManager.isAnyEnabled(PrivacyManager.FEATURE_PUSH, PrivacyManager.FEATURE_CHAT, PrivacyManager.FEATURE_ANALYTICS, PrivacyManager.FEATURE_IN_APP_AUTOMATION));

        privacyManager.enable(PrivacyManager.FEATURE_ALL);
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH, PrivacyManager.FEATURE_CHAT, PrivacyManager.FEATURE_ANALYTICS, PrivacyManager.FEATURE_IN_APP_AUTOMATION));
    }

    @Test
    public void testMigrateModuleEnableFlagsWhenEnabled() {
        dataStore.put(PrivacyManager.CHAT_ENABLED_KEY, true);
        dataStore.put(PrivacyManager.PUSH_ENABLED_KEY, true);
        dataStore.put(PrivacyManager.ANALYTICS_ENABLED_KEY, true);
        dataStore.put(PrivacyManager.PUSH_TOKEN_REGISTRATION_ENABLED_KEY, true);
        dataStore.put(PrivacyManager.IAA_ENABLED_KEY, true);

        privacyManager.enable(PrivacyManager.FEATURE_NONE);
        privacyManager.migrateData();

        assertFalse(dataStore.isSet(PrivacyManager.CHAT_ENABLED_KEY));
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_ENABLED_KEY));
        assertFalse(dataStore.isSet(PrivacyManager.ANALYTICS_ENABLED_KEY));
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_TOKEN_REGISTRATION_ENABLED_KEY));
        assertFalse(dataStore.isSet(PrivacyManager.PUSH_ENABLED_KEY));

        assertFalse(privacyManager.isAnyEnabled(PrivacyManager.FEATURE_PUSH, PrivacyManager.FEATURE_CHAT, PrivacyManager.FEATURE_ANALYTICS, PrivacyManager.FEATURE_IN_APP_AUTOMATION));

        privacyManager.enable(PrivacyManager.FEATURE_ALL);
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_PUSH, PrivacyManager.FEATURE_CHAT, PrivacyManager.FEATURE_ANALYTICS, PrivacyManager.FEATURE_IN_APP_AUTOMATION));
    }

    @Test
    public void testListener() {
        PrivacyManager.Listener listener = mock(PrivacyManager.Listener.class);

        privacyManager.addListener(listener);

        privacyManager.enable(PrivacyManager.FEATURE_CHAT);
        privacyManager.disable(PrivacyManager.FEATURE_CHAT);
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_ALL);

        verify(listener, times(3)).onEnabledFeaturesChanged();
    }

    @Test
    public void testListenerOnlyCalledOnChange() {
        PrivacyManager.Listener listener = mock(PrivacyManager.Listener.class);

        privacyManager.addListener(listener);

        privacyManager.disable(PrivacyManager.FEATURE_ALL);
        privacyManager.disable(PrivacyManager.FEATURE_CHAT);
        verify(listener, never()).onEnabledFeaturesChanged();

        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_ALL);
        privacyManager.enable(PrivacyManager.FEATURE_PUSH);

        verify(listener, times(1)).onEnabledFeaturesChanged();

    }


}
