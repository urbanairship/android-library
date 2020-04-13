/* Copyright Airship and Contributors */

/*
 * Based off of:
 * https://github.com/googlesamples/android-architecture-components/blob/master/NavigationAdvancedSample/app/src/main/java/com/example/android/navigationadvancedsample/NavigationExtensions.kt
 *
 * License:
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.urbanairship.sample;

import android.content.Intent;
import android.util.SparseArray;

import androidx.annotation.IdRes;
import androidx.annotation.NavigationRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

/**
 * Helps showing multiple navigation graphs in a single view.
 *
 * Hopefully this class will no longer be necessary in a future version of the navigation component.
 */
public class MultiNavigationHelper {

    private MutableLiveData<NavController> selectedNavController = new MutableLiveData<>();
    private final int containerId;
    private String rootFragmentTag;
    private final FragmentManager fragmentManager;
    private SparseArray<String> fragmentTagMap = new SparseArray<>();

    /**
     * Factory method.
     *
     * @param containerId The container Id to load the nav host fragments into.
     * @param fragmentManager The fragment manager.
     * @param selectedGraphId The current selected graph Id.
     * @param navigationResources The navigation graph resources.
     * @return The navigation helper.
     */
    public static MultiNavigationHelper newHelper(@IdRes int containerId,
                                                  @NonNull FragmentManager fragmentManager,
                                                  @IdRes int selectedGraphId,
                                                  @NavigationRes int... navigationResources) {

        MultiNavigationHelper helper = new MultiNavigationHelper(containerId, fragmentManager);
        helper.init(selectedGraphId, navigationResources);
        return helper;
    }

    /**
     * Creates a new navigation helper.
     *
     * @param containerId The container Id.
     * @param fragmentManager The fragment manager.
     */
    private MultiNavigationHelper(@IdRes int containerId, @NonNull FragmentManager fragmentManager) {
        this.containerId = containerId;
        this.fragmentManager = fragmentManager;
    }

    /**
     * Inits the helper
     *
     * @param selectedGraphId The current graph Id.
     * @param navigationResources The navigation resources.
     */
    private void init(@IdRes int selectedGraphId, @NavigationRes int... navigationResources) {
        for (int i = 0; i < navigationResources.length; i++) {
            String fragmentTag = getFragmentTag(navigationResources[i]);
            if (i == 0) {
                rootFragmentTag = fragmentTag;
            }

            NavHostFragment navHostFragment = obtainNavHostFragment(fragmentTag, navigationResources[i]);
            int graphId = navHostFragment.getNavController().getGraph().getId();
            fragmentTagMap.put(graphId, fragmentTag);

            if (selectedGraphId == graphId) {
                FragmentTransaction transaction = fragmentManager.beginTransaction()
                                                                 .attach(navHostFragment);
                if (i == 0) {
                    transaction.setPrimaryNavigationFragment(navHostFragment);
                }
                transaction.commitNow();

                selectedNavController.setValue(navHostFragment.getNavController());
            } else {
                fragmentManager.beginTransaction()
                               .detach(navHostFragment)
                               .commitNow();
            }
        }

        fragmentManager.addOnBackStackChangedListener(() -> {
            // Reset the graph if the currentDestination is not valid (happens when the back
            // stack is popped after using the back button).
            NavController navController = selectedNavController.getValue();
            if (navController != null && navController.getCurrentDestination() == null) {
                navController.navigate(navController.getGraph().getId());
            }
        });
    }

    /**
     * Resets the current selected graph to the start destination.
     */
    public void resetCurrentGraph() {
        NavController navController = getCurrentNavController().getValue();
        if (navController != null) {
            navController.popBackStack(navController.getGraph().getStartDestination(), false);
        }
    }

    /**
     * Navigates up.
     *
     * @return {@code true} if the graph was able to navigate up, otherwise {@code false}.
     */
    public boolean navigateUp() {
        if (getCurrentNavController().getValue() == null) {
            return false;
        }

        return getCurrentNavController().getValue().navigateUp();
    }

    /**
     * Attempts to pop the controller's back stack.
     *
     * @return {@code true} if the stack was popped, otherwise {@code false}.
     */
    public boolean popBackStack() {
        if (getCurrentNavController().getValue() == null) {
            return false;
        }

        return getCurrentNavController().getValue().popBackStack();
    }

    /**
     * Gets the current nav controller live data.
     *
     * @return The current nav controller live data.
     */
    public LiveData<NavController> getCurrentNavController() {
        return selectedNavController;
    }

    /**
     * Called to handle deep linking from an intent.
     *
     * @param intent The intent.
     */
    public void deepLink(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        for (int i = 0; i < fragmentTagMap.size(); i++) {
            NavHostFragment navHostFragment = findNavHostFragment(fragmentTagMap.valueAt(i));
            if (navHostFragment != null && navHostFragment.getNavController().handleDeepLink(intent)) {
                navigate(navHostFragment.getNavController().getGraph().getId());
            }
        }
    }

    /**
     * Navigates to the navigation graph.
     *
     * @param graphId The navigation's graph Id.
     */
    public void navigate(@IdRes int graphId) {
        if (fragmentManager.isStateSaved()) {
            return;
        }

        String fragmentTag = fragmentTagMap.get(graphId);
        NavHostFragment selectedFragment = findNavHostFragment(fragmentTag);

        selectedNavController.postValue(selectedFragment.getNavController());

        // Pop the back stack to the root
        fragmentManager.popBackStack(rootFragmentTag, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        // If not the root, attach the fragment and clean up the back stack
        if (!rootFragmentTag.equals(fragmentTag)) {
            fragmentManager.beginTransaction()
                           .attach(selectedFragment)
                           .setPrimaryNavigationFragment(selectedFragment)
                           .detach(findNavHostFragment(rootFragmentTag))
                           .addToBackStack(rootFragmentTag)
                           .setCustomAnimations(
                                   R.anim.nav_default_enter_anim,
                                   R.anim.nav_default_exit_anim,
                                   R.anim.nav_default_pop_enter_anim,
                                   R.anim.nav_default_pop_exit_anim)
                           .setReorderingAllowed(true)
                           .commit();
        }
    }

    /**
     * Helper method to either get or create a nav host fragment.
     *
     * @param fragmentTag The fragment's tag.
     * @param navGraphId The graph resource.
     * @return The nav host fragment.
     */
    @NonNull
    private NavHostFragment obtainNavHostFragment(@NonNull String fragmentTag, @NavigationRes int navGraphId) {
        Fragment existingFragment = fragmentManager.findFragmentByTag(fragmentTag);
        if (existingFragment != null) {
            return (NavHostFragment) existingFragment;
        }

        NavHostFragment navHostFragment = NavHostFragment.create(navGraphId);
        fragmentManager.beginTransaction()
                       .add(containerId, navHostFragment, fragmentTag)
                       .commitNow();

        return navHostFragment;
    }

    /**
     * Helper method to find the navigation host fragment.
     *
     * @param fragmentTag The fragment's tag.
     * @return The nav host fragment.
     */
    private NavHostFragment findNavHostFragment(@NonNull String fragmentTag) {
        return (NavHostFragment) fragmentManager.findFragmentByTag(fragmentTag);
    }

    /**
     * Gets the fragment tag based on the navigation Id.
     *
     * @param navigationId The navigation ID.
     * @return The fragment's tag.
     */
    @NonNull
    private String getFragmentTag(@NavigationRes int navigationId) {
        return "MultiNavigationHelper#" + navigationId;
    }

}
