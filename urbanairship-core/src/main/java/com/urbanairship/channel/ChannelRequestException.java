package com.urbanairship.channel;

/**
 * ChannelApi request exception.
 */
class ChannelRequestException extends Exception {

    ChannelRequestException(String message) {
        super(message);
    }

    ChannelRequestException(String message, Throwable e) {
        super(message, e);
    }

}
