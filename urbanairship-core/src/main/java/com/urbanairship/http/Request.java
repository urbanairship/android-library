/* Copyright Airship and Contributors */

package com.urbanairship.http;

import android.net.Uri;
import android.util.Base64;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.util.ConnectionUtils;
import com.urbanairship.util.PlatformUtils;
import com.urbanairship.util.UAStringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Http request wrapper.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Request {

    private static final int NETWORK_TIMEOUT_MS = 60000;

    private static final ResponseParser<Void> EMPTY_RESPONSE_PARSER = new ResponseParser<Void>() {
        @Override
        public Void parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) {
            return null;
        }
    };

    @Nullable
    protected Uri uri;

    @Nullable
    protected String user;

    @Nullable
    protected String password;

    @Nullable
    protected String requestMethod;

    @Nullable
    protected String body;

    @Nullable
    protected String contentType;

    protected long ifModifiedSince = 0;

    protected boolean compressRequestBody = false;

    protected boolean followRedirects = true;

    @NonNull
    protected final Map<String, String> responseProperties;

    private static final String USER_AGENT_FORMAT = "(UrbanAirshipLib-%s/%s; %s)";

    /**
     * Request constructor.
     *
     * @param requestMethod The string request method.
     * @param uri The request URL.
     */
    public Request(@Nullable String requestMethod, @Nullable Uri uri) {
        this();
        this.requestMethod = requestMethod;
        this.uri = uri;
    }

    public Request() {
        responseProperties = new HashMap<>();
    }

    public Request setOperation(@Nullable String requestMethod, @Nullable Uri uri) {
        this.requestMethod = requestMethod;
        this.uri = uri;
        return this;
    }

    /**
     * Sets the credentials.
     *
     * @param user The user ID.
     * @param password The user token.
     * @return The request.
     */
    @NonNull
    public Request setCredentials(@Nullable String user, @Nullable String password) {
        this.user = user;
        this.password = password;

        return this;
    }

    /**
     * Sets the airship user agent and X-UA-App-Key header.
     *
     * @param config The runtime config.
     */
    public Request setAirshipUserAgent(@NonNull AirshipRuntimeConfig config) {
        String platform = PlatformUtils.asString(config.getPlatform());

        String userAgent = String.format(Locale.ROOT,
                USER_AGENT_FORMAT,
                platform,
                UAirship.getVersion(),
                config.getConfigOptions().appKey);

        responseProperties.put("X-UA-App-Key", config.getConfigOptions().appKey);
        responseProperties.put("User-Agent", userAgent);
        return this;
    }

    /**
     * Sets the JSON request body.
     *
     * @param json The JSON.
     * @return The request.
     */
    @NonNull
    public Request setRequestBody(@NonNull JsonSerializable json) {
        return setRequestBody(json.toJsonValue().toString(), "application/json");
    }

    /**
     * Sets the request body.
     *
     * @param body The string body.
     * @param contentType The string content type.
     * @return The request.
     */
    @NonNull
    public Request setRequestBody(@Nullable String body, @Nullable String contentType) {
        this.body = body;
        this.contentType = contentType;
        return this;
    }

    /**
     * Sets the if modified since value.
     *
     * @param timeMS The time in milliseconds.
     * @return The request.
     */
    @NonNull
    public Request setIfModifiedSince(long timeMS) {
        this.ifModifiedSince = timeMS;
        return this;
    }

    /**
     * Adds additional request headers.
     *
     * @param headers The headers.
     * @return The request.
     */
    @NonNull
    public Request addHeaders(@NonNull Map<String, String> headers) {
        responseProperties.putAll(headers);
        return this;
    }

    /**
     * Set additional request properties.
     * <p>
     * The credentials and content type will also be added. The credentials can be set with
     * {@link #setCredentials(String, String)} and the content type can be set with
     * {@link #setRequestBody(String, String)}.
     * <p>
     *
     * @param key The property.
     * @param value The value of the property.
     * @return The request.
     */
    @NonNull
    public Request setHeader(@NonNull String key, @Nullable String value) {
        if (value == null) {
            responseProperties.remove(key);
        } else {
            responseProperties.put(key, value);
        }
        return this;
    }

    /**
     * Set the `application/vnd.urbanairship+json; version=3;` as the `Accept` header.
     *
     * @return The request.
     */
    @NonNull
    public Request setAirshipJsonAcceptsHeader() {
        return setHeader("Accept", "application/vnd.urbanairship+json; version=3;");
    }

    /**
     * Sets whether the request body is compressed with gzip.
     *
     * @param compressRequestBody A boolean to compress the request body.
     * @return The request.
     */
    @NonNull
    public Request setCompressRequestBody(boolean compressRequestBody) {
        this.compressRequestBody = compressRequestBody;
        return this;
    }

    /**
     * Sets whether the request must follow the redirection.
     *
     * @param followRedirects A boolean to follow redirection.
     * @return The request.
     */
    @NonNull
    public Request setInstanceFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    public Response<Void> execute() throws RequestException {
        return execute(EMPTY_RESPONSE_PARSER);
    }

    /**
     * Executes the request.
     *
     * @return The request response.
     */
    @NonNull
    public <T> Response<T> execute(@NonNull ResponseParser<T> parser) throws RequestException {
        if (uri == null) {
            throw new RequestException("Unable to perform request: missing URL");
        }

        URL url;
        try {
            url = new URL(uri.toString());
        } catch (MalformedURLException e) {
            throw new RequestException("Failed to build URL", e);
        }

        if (requestMethod == null) {
            throw new RequestException("Unable to perform request: missing request method");
        }

        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) ConnectionUtils.openSecureConnection(UAirship.getApplicationContext(), url);
            conn.setRequestMethod(requestMethod);
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", contentType);
            }

            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setInstanceFollowRedirects(followRedirects);

            if (ifModifiedSince > 0) {
                conn.setIfModifiedSince(ifModifiedSince);
            }

            for (String key : responseProperties.keySet()) {
                conn.setRequestProperty(key, responseProperties.get(key));
            }

            if (!UAStringUtil.isEmpty(user) && !UAStringUtil.isEmpty(password)) {
                String credentials = user + ":" + password;
                conn.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP));
            }

            // Create the form content
            if (body != null) {

                if (compressRequestBody) {
                    conn.setRequestProperty("Content-Encoding", "gzip");
                    OutputStream out = conn.getOutputStream();
                    GZIPOutputStream gos = new GZIPOutputStream(out);
                    Writer writer = new OutputStreamWriter(gos, "UTF-8");
                    writer.write(body);
                    writer.close();
                    gos.close();
                    out.close();
                } else {
                    OutputStream out = conn.getOutputStream();
                    Writer writer = new OutputStreamWriter(out, "UTF-8");
                    writer.write(body);
                    writer.close();
                    out.close();
                }
            }

            Response.Builder<T> responseBuilder = new Response.Builder<T>(conn.getResponseCode())
                    .setResponseHeaders(conn.getHeaderFields())
                    .setLastModified(conn.getLastModified());

            String messageBody;
            try {
                messageBody = readEntireStream(conn.getInputStream());
            } catch (IOException ex) {
                messageBody = readEntireStream(conn.getErrorStream());
            }

            return responseBuilder.setResult(parser.parseResponse(conn.getResponseCode(), conn.getHeaderFields(), messageBody))
                                  .setResponseBody(messageBody)
                                  .build();
        } catch (Exception e) {
            throw new RequestException(String.format(Locale.ROOT, "Request failed URL: %s method: %s", url, requestMethod), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Nullable
    private String readEntireStream(@Nullable InputStream input) throws IOException {
        if (input == null) {
            return null;
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(input));
        StringBuilder sb = new StringBuilder();

        try {
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine).append("\n");
            }
            br.close();
        } finally {
            try {
                input.close();
                br.close();
            } catch (Exception e) {
                Logger.error(e, "Failed to close streams");
            }
        }

        return sb.toString();
    }

}
