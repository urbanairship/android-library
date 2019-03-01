package com.urbanairship.locale;

import android.content.Context;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link LocaleManager}
 */
public class LocaleManagerTest extends BaseTestCase {

    private LocaleManager localeManager;
    private Context context;

    @Before
    public void setup() {
        context = TestApplication.getApplication();
        localeManager = new LocaleManager(context);
    }

    @Test
    public void testGetLocale() {
        Locale de = new Locale("de");
        context.getResources().getConfiguration().setLocale(de);

        assertEquals(de, localeManager.getDefaultLocale());
    }

    @Test
    public void testNotifyLocaleChanged() {
        Locale en = new Locale("en");
        context.getResources().getConfiguration().setLocale(en);

        LocaleChangedListener listener = mock(LocaleChangedListener.class);
        localeManager.addListener(listener);

        localeManager.notifyLocaleChanged();

        verify(listener).onLocaleChanged(en);
    }

}