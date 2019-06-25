/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.FragmentActivity;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Activity that automatically uses the AppCompat support library if its available and the application
 * extends the app compat theme.
 */
public abstract class ThemedActivity extends FragmentActivity {

    private static Boolean isAppCompatDependencyAvailable;
    private AppCompatDelegateWrapper delegate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (isAppCompatAvailable(this)) {
            delegate = AppCompatDelegateWrapper.create(this);
        }

        if (delegate != null) {
            delegate.onCreate(savedInstanceState);
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (delegate != null) {
            delegate.onPostCreate(savedInstanceState);
        }
    }

    @NonNull
    @Override
    public MenuInflater getMenuInflater() {
        if (delegate != null) {
            return delegate.getMenuInflater();
        }

        return super.getMenuInflater();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResId) {
        if (delegate != null) {
            delegate.setContentView(layoutResId);
        } else {
            super.setContentView(layoutResId);
        }
    }

    @SuppressLint("UnknownNullness")
    @Override
    public void setContentView(View view) {
        if (delegate != null) {
            delegate.setContentView(view);
        } else {
            super.setContentView(view);
        }
    }

    @SuppressLint("UnknownNullness")
    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        if (delegate != null) {
            delegate.setContentView(view, params);
        } else {
            super.setContentView(view, params);
        }
    }

    @SuppressLint("UnknownNullness")
    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (delegate != null) {
            delegate.addContentView(view, params);
        } else {
            super.addContentView(view, params);
        }
    }

    @SuppressLint("UnknownNullness")
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (delegate != null) {
            delegate.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (delegate != null) {
            delegate.onStop();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (delegate != null) {
            delegate.onPostResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (delegate != null) {
            delegate.onDestroy();
        }
    }

    @SuppressLint("UnknownNullness")
    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        if (delegate != null) {
            delegate.setTitle(title);
        }
    }

    @Override
    public void supportInvalidateOptionsMenu() {
        if (delegate != null) {
            delegate.invalidateOptionsMenu();
        } else {
            super.supportInvalidateOptionsMenu();
        }
    }

    /**
     * Helper method to enable up navigation.
     *
     * @param enabled {@code true} to enable up navigation, otherwise {@code false}.
     */
    protected void setDisplayHomeAsUpEnabled(boolean enabled) {
        if (delegate != null) {
            if (delegate.getSupportActionBar() != null) {
                delegate.getSupportActionBar().setDisplayHomeAsUpEnabled(enabled);
                delegate.getSupportActionBar().setHomeButtonEnabled(enabled);
            }
        } else if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(enabled);
            getActionBar().setHomeButtonEnabled(enabled);
        }
    }

    protected void hideActionBar() {
        if (delegate != null) {
            if (delegate.getSupportActionBar() != null) {
                delegate.getSupportActionBar().hide();
            }
        } else if (getActionBar() != null) {
            getActionBar().hide();
        }
    }

    /**
     * Checks if AppCompat support library is both installed and available for the activity.
     *
     * @param activity The activity to check.
     * @return {@code true} if app compatibility is available for the activity, otherwise {@code false}.
     */
    static boolean isAppCompatAvailable(@NonNull Activity activity) {
        if (isAppCompatDependencyAvailable == null) {
            // Play Services
            try {
                Class.forName("androidx.appcompat.app.AppCompatDelegate");
                isAppCompatDependencyAvailable = true;
            } catch (ClassNotFoundException e) {
                isAppCompatDependencyAvailable = false;
            }
        }

        if (!isAppCompatDependencyAvailable) {
            return false;
        }

        int colorPrimary = activity.getResources().getIdentifier("colorPrimary", "attr", activity.getPackageName());
        if (colorPrimary == 0) {
            return false;
        }

        TypedArray a = activity.obtainStyledAttributes(new int[] { colorPrimary });
        final boolean isAvailable = a.hasValue(0);
        a.recycle();

        return isAvailable;
    }

}
