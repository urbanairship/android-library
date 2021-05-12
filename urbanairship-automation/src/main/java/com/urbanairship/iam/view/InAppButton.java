package com.urbanairship.iam.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import androidx.annotation.RestrictTo;

/**
 * {@code Button} subclass with no customization. Used to bypass inflation-time view replacement by
 * libraries like Material Components that can interfere with our ability to apply custom styling
 * in a consistent way.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InAppButton extends Button {

    public InAppButton(Context context) {
        super(context);
    }

    public InAppButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InAppButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public InAppButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
