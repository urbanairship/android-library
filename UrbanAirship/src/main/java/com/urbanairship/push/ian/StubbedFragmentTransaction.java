/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.push.ian;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.View;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class StubbedFragmentTransaction extends FragmentTransaction {
    @NonNull
    @Override
    public FragmentTransaction add(Fragment fragment, String tag) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction add(int containerViewId, Fragment fragment) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction add(int containerViewId, Fragment fragment, String tag) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction replace(int containerViewId, Fragment fragment) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction replace(int containerViewId, Fragment fragment, String tag) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction remove(Fragment fragment) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction hide(Fragment fragment) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction show(Fragment fragment) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction detach(Fragment fragment) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction attach(Fragment fragment) {
        return this;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @NonNull
    @Override
    public FragmentTransaction setCustomAnimations(int enter, int exit) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction setCustomAnimations(int enter, int exit, int popEnter, int popExit) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction setTransition(int transit) {
        return this;
    }

    @Override
    public FragmentTransaction addSharedElement(View sharedElement, String name) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction setTransitionStyle(int styleRes) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction addToBackStack(String name) {
        return this;
    }

    @Override
    public boolean isAddToBackStackAllowed() {
        return false;
    }

    @NonNull
    @Override
    public FragmentTransaction disallowAddToBackStack() {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction setBreadCrumbTitle(int res) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction setBreadCrumbTitle(CharSequence text) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction setBreadCrumbShortTitle(int res) {
        return this;
    }

    @NonNull
    @Override
    public FragmentTransaction setBreadCrumbShortTitle(CharSequence text) {
        return this;
    }

    @Override
    public int commit() {
        return 0;
    }

    @Override
    public int commitAllowingStateLoss() {
        return 0;
    }
}
