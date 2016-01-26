/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
