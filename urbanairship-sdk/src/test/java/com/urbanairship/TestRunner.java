/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.FileFsFile;

public class TestRunner extends RobolectricTestRunner {
    public TestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    protected AndroidManifest getAppManifest(Config config) {
        FileFsFile assets = FileFsFile.from("src/test/assets");
        FileFsFile manifest = FileFsFile.from("src/test/AndroidManifest.xml");
        FileFsFile resources = FileFsFile.from("src/main/res");

        return new AndroidManifest(manifest, resources, assets, "com.urbanairship");
    }
}
