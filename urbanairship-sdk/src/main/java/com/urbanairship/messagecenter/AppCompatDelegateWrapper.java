/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatDelegate;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Wrapper for {@link AppCompatDelegate}.
 */
class AppCompatDelegateWrapper {

    private AppCompatDelegate delegate;

    /**
     * Creates an {@code AppCompatDelegateWrapper}.
     *
     * @param activity The activity.
     * @return Instance of {@code AppCompatDelegateWrapper}.
     */
    static AppCompatDelegateWrapper create(Activity activity) {
        AppCompatDelegateWrapper delegateWrapper = new AppCompatDelegateWrapper();
        delegateWrapper.delegate = AppCompatDelegate.create(activity, null);
        return delegateWrapper;
    }

    void onCreate(Bundle savedInstanceState) {
        if (delegate != null) {
            delegate.installViewFactory();
            delegate.onCreate(savedInstanceState);
        }
    }

    void onPostCreate(Bundle savedInstanceState) {
        delegate.onPostCreate(savedInstanceState);
    }

    MenuInflater getMenuInflater() {
        return delegate.getMenuInflater();
    }

    void setContentView(int layoutResId) {
        delegate.setContentView(layoutResId);
    }

    void setContentView(View view) {
        delegate.setContentView(view);
    }

    void setContentView(View view, ViewGroup.LayoutParams params) {
        delegate.setContentView(view, params);
    }

    void addContentView(View view, ViewGroup.LayoutParams params) {
        delegate.addContentView(view, params);
    }

    void onConfigurationChanged(Configuration newConfig) {
        delegate.onConfigurationChanged(newConfig);
    }

    void onPostResume() {
        delegate.onPostResume();
    }

    void onStop() {
        delegate.onStop();
    }

    void invalidateOptionsMenu() {
        delegate.invalidateOptionsMenu();
    }

    void setTitle(CharSequence title) {
        delegate.setTitle(title);
    }

    void onDestroy() {
        delegate.onDestroy();
    }

    ActionBar getSupportActionBar() {
        return delegate.getSupportActionBar();
    }
}
