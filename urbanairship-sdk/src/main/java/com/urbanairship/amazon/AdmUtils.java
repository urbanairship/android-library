/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.amazon;

import android.content.Context;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;

/**
 * Util methods for ADM.
 */
public class AdmUtils {

    private static Boolean isAdmAvailable;

    /**
     * Checks if ADM is available on the device.
     *
     * @return <code>true</code> if ADM is available.
     */
    public static boolean isAdmAvailable() {
        if (isAdmAvailable != null) {
            return isAdmAvailable;
        }

        try {
            Class.forName("com.amazon.device.messaging.ADM");
            isAdmAvailable = true;
        } catch (ClassNotFoundException e) {
            isAdmAvailable = false;
        }

        return isAdmAvailable;
    }

    /**
     * Checks if ADM is available and supported on the device.
     *
     * @return <code>true</code> if ADM is available and supported.
     */
    public static boolean isAdmSupported() {
        return isAdmAvailable() && AdmWrapper.isSupported();
    }

    /**
     * Starts the registration process for ADM.
     */
    public static void startRegistration(@NonNull Context context) {
        if (isAdmSupported()) {
            AdmWrapper.startRegistration(context);
        }
    }

    /**
     * Validates the manifest for ADM.
     */
    public static void validateManifest() {
        if (isAdmAvailable()) {
            AdmWrapper.validateManifest();
        } else {
            Logger.warn("ADM is not available on this device.");
        }
    }
}
