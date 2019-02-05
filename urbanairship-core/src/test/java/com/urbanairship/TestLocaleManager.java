package com.urbanairship;

import android.content.Context;
import android.support.annotation.NonNull;

import com.urbanairship.locale.LocaleChangedListener;
import com.urbanairship.locale.LocaleManager;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestLocaleManager extends LocaleManager {

    private List<LocaleChangedListener> localeChangedListeners = new CopyOnWriteArrayList<>();
    private Locale locale;

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

        for (LocaleChangedListener listener : localeChangedListeners) {
            listener.onLocaleChanged(locale);
        }
    }

    @NonNull
    @Override
    public Locale getDefaultLocale(@NonNull Context context) {
        if (locale == null) {
            locale = super.getDefaultLocale(context);
        }
        return locale;
    }
}
