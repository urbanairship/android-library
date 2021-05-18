/* Copyright Airship and Contributors */

package com.urbanairship;

import android.net.Uri;

import com.urbanairship.http.Request;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.util.Checks;

import java.net.URL;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Request class used for testing.
 */
public class TestRequest extends Request {

    public Map<String, List<String>> responseHeaders;
    public long responseLastModifiedTime;
    public String responseBody;
    public int responseStatus;

    public TestRequest() {
        super();
    }

    @NonNull
    @Override
    public <T> Response<T> execute(@NonNull ResponseParser<T> parser) throws RequestException {

        try {
            Checks.checkNotNull(uri, "missing url");
            Checks.checkNotNull(requestMethod, "missing request method");
            return new Response.Builder<T>(responseStatus)
                    .setLastModified(responseLastModifiedTime)
                    .setResponseBody(responseBody)
                    .setResponseHeaders(responseHeaders)
                    .setResult(parser.parseResponse(responseStatus, responseHeaders, responseBody))
                    .build();
        } catch (Exception e) {
            throw new RequestException("parse error", e);
        }
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

    public long getIfModifiedSince() {
        return ifModifiedSince;
    }

    @Nullable
    public String getRequestMethod() {
        return requestMethod;
    }

    @Nullable
    public Uri getUrl() {
        return uri;
    }
}
