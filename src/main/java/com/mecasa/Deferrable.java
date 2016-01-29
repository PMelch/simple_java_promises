package com.mecasa;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:28
 */
public abstract class Deferrable<T> {

    private int _timeout;

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
}
