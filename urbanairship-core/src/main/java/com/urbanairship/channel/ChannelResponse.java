package com.urbanairship.channel;

import com.urbanairship.http.Response;

/**
 * Channel response.
 *
 * @param <T> The result.
 */
class ChannelResponse<T> extends Response {

    private final T result;

    ChannelResponse(T result, Response response) {
        super(response);
        this.result = result;
    }

    /**
     * Gets the result.
     *
     * @return The channel result.
     */
    T getResult() {
        return result;
    }

}
