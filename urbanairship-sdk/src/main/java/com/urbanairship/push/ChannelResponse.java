/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.push;

import android.support.annotation.NonNull;

import com.urbanairship.http.Response;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Model object containing response information from a request.
 */
class ChannelResponse {

    private final Response response;

    public ChannelResponse(@NonNull Response response) {
        this.response = response;
    }

    /**
     * Returns the response status code.
     *
     * @return The response status code as an int.
     */
    public int getStatus() {
        return response.getStatus();
    }

    /**
     * Returns the Channel ID.
     *
     * @return The Channel ID as a string.
     */
    String getChannelId() {
        if (UAStringUtil.isEmpty(response.getResponseBody())) {
            return null;
        }

        try {
            return new JSONObject(response.getResponseBody()).getString("channel_id");
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Returns the channel location.
     *
     * @return The channel location as a string.
     */
    String getChannelLocation() {
        if (response.getResponseHeaders() != null) {
            List<String> headersList = response.getResponseHeaders().get("Location");
            if (headersList != null && headersList.size() > 0) {
                return headersList.get(0);
            }
        }

        return null;
    }
}
