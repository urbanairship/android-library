package com.urbanairship.iam;

import androidx.annotation.NonNull;

/**
 * Display coordinator that is always ready.
 */
class ImmediateDisplayCoordinator extends DisplayCoordinator {

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void onDisplayStarted(@NonNull InAppMessage message) {}

    @Override
    public void onDisplayFinished(@NonNull InAppMessage message) {}

}
