/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TestUtils {

    public static Set<String> tagSet(String... tags) {
        return new HashSet<>(Arrays.asList(tags));
    }

}
