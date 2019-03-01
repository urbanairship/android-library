/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.preference;

import android.content.Context;
import android.content.Intent;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;

/**
 * Tag preference.
 */
public class TagsPreference extends Preference {

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param attrs The attribute set.
     */
    public TagsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        Intent intent = new Intent(context, TagsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        setIntent(intent);
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }

}