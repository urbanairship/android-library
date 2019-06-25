/* Copyright Airship and Contributors */

package com.urbanairship.http;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import android.util.Base64;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.UAStringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Http request wrapper.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Request {

    @NonNull
    protected URL url;

    @Nullable
    protected String user;

    @Nullable
    protected String password;

    @NonNull
    protected String requestMethod;

    @Nullable
    protected String body;

    @Nullable
    protected String contentType;

    @NonNull
    protected final Map<String, String> responseProperties;
    private static final String USER_AGENT_FORMAT = "%s (%s; %s; UrbanAirshipLib-%s/%s; %s; %s)";
    private long ifModifiedSince = 0;
    private boolean compressRequestBody = false;

    /**
     * Request constructor.
     *
     * @param requestMethod The string request method.
     * @param url The request URL.
     */
    public Request(@NonNull String requestMethod, @NonNull URL url) {
        this.requestMethod = requestMethod;
        this.url = url;

        responseProperties = new HashMap<>();
        responseProperties.put("User-Agent", getUrbanAirshipUserAgent());
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
     * Executes the request.
     *
     * @return The request response.
     */
    @Nullable
    public Response execute() {
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(requestMethod);

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", contentType);
            }

            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);

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

            Response.Builder responseBuilder = Response.newBuilder(conn.getResponseCode())
                                                       .setResponseMessage(conn.getResponseMessage())
                                                       .setResponseHeaders(conn.getHeaderFields())
                                                       .setLastModified(conn.getLastModified());

            try {
                responseBuilder.setResponseBody(readEntireStream(conn.getInputStream()));
            } catch (IOException ex) {
                responseBuilder.setResponseBody(readEntireStream(conn.getErrorStream()));
            }

            return responseBuilder.build();

        } catch (Exception ex) {
            Logger.debug(ex, "Request - Request failed URL: %s method: %s", url, requestMethod);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Gets the Airship User Agent used for any Airship requests.
     *
     * @return The Airship User Agent.
     */
    @NonNull
    public static String getUrbanAirshipUserAgent() {
        String platform = UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? "amazon" : "android";

        return String.format(Locale.US, USER_AGENT_FORMAT, UAirship.getPackageName(),
                Build.MODEL, Build.VERSION.RELEASE, platform, UAirship.getVersion(),
                UAirship.shared().getAirshipConfigOptions().appKey,
                LocaleManager.shared(UAirship.getApplicationContext()).getDefaultLocale());
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
