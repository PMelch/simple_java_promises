package com.mecasa;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:28
 */
public abstract class Deferrable<T> {

    private int _timeout = -1;
    private int _retryDelay = -1;
    private int _retries = -1;

    abstract T call(Object... params) throws Exception;

    /**
     * Set timeout for the Deferrables. If a asnc task takes longer than the specified time, the promise is rejected
     * with a TimedOutException.
     * @param timeout
     * @return
     */
    public Deferrable<T> timeout(int timeout) {
        _timeout = timeout;
        return this;
    }

    public int getTimeout() {
        return _timeout;
    }

    public Deferrable<T> retriesWithDelay(int numRetries, int delay) {
        _retries = numRetries;
        _retryDelay = delay;
        return this;
    }

    public int getRetries() {
        return _retries;
    }

    public int getRetryDelay() {
        return _retryDelay;
    }
}
