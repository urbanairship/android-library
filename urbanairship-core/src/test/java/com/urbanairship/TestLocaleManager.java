package com.urbanairship;

import androidx.annotation.NonNull;

import com.urbanairship.locale.LocaleChangedListener;
import com.urbanairship.locale.LocaleManager;

import org.robolectric.RuntimeEnvironment;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestLocaleManager extends LocaleManager {

    private List<LocaleChangedListener> localeChangedListeners = new CopyOnWriteArrayList<>();
    private Locale locale;

    public TestLocaleManager() {
        super(RuntimeEnvironment.application);
    }

    @Override
    public void addListener(@NonNull LocaleChangedListener listener) {
        localeChangedListeners.add(listener);
    }

    @Override
    public void removeListener(@NonNull LocaleChangedListener listener) {
        localeChangedListeners.remove(listener);
    }

    public void setDefaultLocale(Locale locale) {
        this.locale = locale;
    }

    public void notifyLocaleChange() {
        for (LocaleChangedListener listener : localeChangedListeners) {
            listener.onLocaleChanged(getDefaultLocale());
        }
    }

    @NonNull
    @Override
    public Locale getDefaultLocale() {
        if (locale == null) {
            locale = new Locale("en", "US");
        }
        return locale;
    }

}
