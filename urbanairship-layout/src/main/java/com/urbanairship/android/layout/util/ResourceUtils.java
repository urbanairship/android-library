/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.TypedValue;

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
                return WindowSize.MEDIUM;
            case SCREENLAYOUT_SIZE_LARGE:
            case SCREENLAYOUT_SIZE_XLARGE:
                return WindowSize.LARGE;
        }
        return null;
    }

    @NonNull
    private static String readStream(@NonNull InputStream inputStream) {
        try(Scanner s = new Scanner(inputStream, "UTF-8").useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }
}
