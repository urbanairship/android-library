package com.urbanairship.actions;

import androidx.annotation.NonNull;

public interface ActionRunRequestExtender  {

    @NonNull
    ActionRunRequest extend(@NonNull ActionRunRequest request);
}
