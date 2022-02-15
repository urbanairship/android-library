/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

@FunctionalInterface
public interface Function3<P1, P2, P3, R> {
    R invoke(P1 var1, P2 var2, P3 var3);
}
