package com.urbanairship;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PrivacyManagerTest extends BaseTestCase {

    private PrivacyManager privacyManager;
    private PreferenceDataStore dataStore;

    @Before
    public void setup() {
        this.dataStore = new PreferenceDataStore(TestApplication.getApplication());
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
    public void testIsAnyEnabled() {
        assertFalse(privacyManager.isAnyFeatureEnabled());

        privacyManager.enable(PrivacyManager.FEATURE_CHAT);
        assertTrue(privacyManager.isAnyFeatureEnabled());

        privacyManager.disable(PrivacyManager.FEATURE_CHAT);
        assertFalse(privacyManager.isAnyFeatureEnabled());
    }

    @Test
    public void testListener() {
        PrivacyManager.Listener listener = mock(PrivacyManager.Listener.class);

        privacyManager.addListener(listener);

        privacyManager.enable(PrivacyManager.FEATURE_CHAT);
        privacyManager.disable(PrivacyManager.FEATURE_CONTACTS);
        privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_ALL);


        verify(listener, times(3)).onEnabledFeaturesChanged();
    }

}
