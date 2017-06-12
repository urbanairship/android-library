/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.support.annotation.NonNull;
import android.view.View;

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
    public FragmentTransaction setPrimaryNavigationFragment(Fragment fragment) {
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
    public FragmentTransaction setReorderingAllowed(boolean b) {
        return this;
    }

    @Override
    public FragmentTransaction runOnCommit(Runnable runnable) {
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

    @Override
    public void commitNow() {

    }

    @Override
    public void commitNowAllowingStateLoss() {

    }
}
