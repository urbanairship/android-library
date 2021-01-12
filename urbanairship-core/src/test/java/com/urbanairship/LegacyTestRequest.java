/* Copyright Airship and Contributors */

package com.urbanairship;

import com.urbanairship.http.Request;
import com.urbanairship.http.Response;

import java.net.URL;
import java.util.Map;

import androidx.annotation.NonNull;

/**
 * Request class used for testing.
 */
public class LegacyTestRequest extends Request {

    public Response response;
    private long ifModifiedSince;

    public LegacyTestRequest() {
        super(null, null);
    }

    @Override
    public Response execute() {
        return response;
    }

    /**
     * Get the request body.
     *
     * @return The request body.
     */
    public String getRequestBody() {
        return body;
    }

    /**
     * Get the request headers.
     *
     * @return The request headers.
     */
    public Map<String, String> getRequestHeaders() {
        return responseProperties;
    }

    /**
     * Set the URL.
     *
     * @param url The URL.
     */
    public void setURL(URL url) {
        this.url = url;
    }

    /**
     * Get the URL.
     *
     * @return The URL.
     */
    public URL getURL() {
        return url;
    }

    /**
     * Set the request method.
     *
     * @param requestMethod The requestMethod as a string.
     */
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    /**
     * Get the request method.
     *
     * @return The requestMethod as a string.
     */
    public String getRequestMethod() {
        return requestMethod;
    }

    @NonNull
    @Override
    public Request setIfModifiedSince(long milliseconds) {
        super.setIfModifiedSince(milliseconds);
        ifModifiedSince = milliseconds;
        return this;
    }

    public long getIfModifiedSince() {
        return ifModifiedSince;
    }

}
