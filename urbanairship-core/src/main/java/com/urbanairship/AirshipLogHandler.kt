package com.urbanairship

/**
 * Log handler
 */
public fun interface AirshipLogHandler {

    /**
     * Called to log.
     * @param tag The logger's tag
     * @param logLevel The log level
     * @param throwable Optional throwable
     * @param message A message lambda
     */
    public fun log(tag: String, logLevel: Int, throwable: Throwable?, message: () -> String)
}
