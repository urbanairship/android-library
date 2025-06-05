/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.urbanairship.android.layout.property.Orientation;
import com.urbanairship.android.layout.property.WindowSize;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_NORMAL;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_SMALL;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_XLARGE;
import static androidx.annotation.Dimension.DP;
import static androidx.annotation.Dimension.SP;

public final class ResourceUtils {
    private ResourceUtils() {}

    @Nullable
    public static JsonMap readJsonAsset(@NonNull Context context, @NonNull String fileName) throws JsonException, IOException {
        JsonValue jsonValue = JsonValue.parseString(readAsset(context, fileName));
        return jsonValue.getMap();
    }

    @NonNull
    public static List<String> listJsonAssets(@NonNull Context context, @Nullable String path) {
        AssetManager assetManager = context.getResources().getAssets();

        String[] assets;
        try {
            assets = assetManager.list(path == null ? "" : path);
        } catch (IOException e) {
            return Collections.emptyList();
        }

        List<String> jsonAssets = new ArrayList<>();
        for (String a : assets) {
            if (a.endsWith(".json")) {
                jsonAssets.add(a);
            }
        }

        return jsonAssets;
    }

    @NonNull
    public static String readAsset(@NonNull Context context, @NonNull String fileName) throws IOException {
        return readStream(context.getResources().getAssets().open(fileName));
    }

    @Dimension
    public static float dpToPx(@NonNull Context context, @Dimension(unit = DP) int dp) {
        Resources r = context.getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
    }

    @Dimension
    public static float spToPx(@NonNull Context context, @Dimension(unit = SP) int sp) {
        Resources r = context.getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, r.getDisplayMetrics());
    }

    public static boolean isUiModeNight(@NonNull Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        // If we're not in night mode, we're in light or unspecified mode, which we'll assume is not night.
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Nullable
    public static Orientation getOrientation(@NonNull Context context) {
        switch (context.getResources().getConfiguration().orientation) {
            case ORIENTATION_PORTRAIT:
                return Orientation.PORTRAIT;
            case ORIENTATION_LANDSCAPE:
                return Orientation.LANDSCAPE;
        }
        return null;
    }

    @Nullable
    public static WindowSize getWindowSize(@NonNull Context context) {
        int screenLayout = context.getResources().getConfiguration().screenLayout;
        screenLayout &= Configuration.SCREENLAYOUT_SIZE_MASK;
        switch (screenLayout) {
            case SCREENLAYOUT_SIZE_SMALL:
                return WindowSize.SMALL;
            case SCREENLAYOUT_SIZE_NORMAL:
            case SCREENLAYOUT_SIZE_LARGE:
                return WindowSize.MEDIUM;
            case SCREENLAYOUT_SIZE_XLARGE:
                return WindowSize.LARGE;
        }
        return null;
    }

    public static int getDisplayWidthPixels(@NonNull Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int getDisplayHeightPixels(@NonNull Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static int getWindowWidthPixels(@NonNull Context context, boolean ignoreSafeArea) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();

            if (ignoreSafeArea) {
                return windowMetrics.getBounds().width();
            }

            Insets insets = windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            return windowMetrics.getBounds().width() - insets.left - insets.right;
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            // This won't be the exact window width and we can't handle ignoreSafeArea
            // but this is the closest value we can get for API<30.
            // getMetrics() doesn't work well with the insets.
            // getRealMetrics() would be better but there can be cases it won't work.
            return displayMetrics.widthPixels;
        }
    }

    public static int getWindowHeightPixels(@NonNull Context context, boolean ignoreSafeArea) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();

            if (ignoreSafeArea) {
                return windowMetrics.getBounds().height();
            }

            Insets insets = windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            return windowMetrics.getBounds().height() - insets.top - insets.bottom;
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            // This won't be the exact window height and we can't handle ignoreSafeArea
            // but this is the closest value we can get for API<30.
            // getMetrics() doesn't work well with the insets.
            // getRealMetrics() would be better but it won't work for split screens for example.
            return displayMetrics.heightPixels;
        }
    }

    @NonNull
    private static String readStream(@NonNull InputStream inputStream) {
        try(Scanner s = new Scanner(inputStream, "UTF-8").useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }
}
