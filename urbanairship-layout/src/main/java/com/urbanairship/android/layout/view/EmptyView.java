/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.view.View;

import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.EmptyModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;

/**
 * An empty view that can have a background and border.
 *
 * Useful for the nub on a banner or as dividers between items.
 *
 * @see EmptyModel
 */
public class EmptyView extends View implements BaseView {
    private final EmptyModel model;

    public EmptyView(@NonNull Context context, @NonNull EmptyModel model, @NonNull ViewEnvironment viewEnvironment) {
        super(context);
        this.model = model;

        setId(model.getViewId());

        configure();
    }

    private void configure() {
        LayoutUtils.applyBorderAndBackground(this, model);
    }
}
