package com.mecasa.jspromise;

/**
 * Created by peter on 19/02/16.
 *
 * The base class for all other call types.
 */
public abstract class Call<T> {
    private Promise _promise;
    private int _retryDelay = -1;
    private int _retries = -1;
    private boolean _rejected;
    private boolean _resolved;
    private Object[] _params;
    private T _resolvedValue;

    protected void triggerCall(Object... params){
        // save for retries
        _params = params;
    }

    protected abstract void call(Object... params) throws Throwable;

    public Call retriesWithDelay(int numRetries, int delay) {
        _retries = numRetries;
        _retryDelay = delay;
        return this;
    }

    public Call retries(int numRetries) {
        _retries = numRetries;
        _retryDelay = -1;
        return this;
    }

    public int getRetries() {
        return _retries;
    }

    public int getRetryDelay() {
        return _retryDelay;
    }

    protected void setPromise(Promise promise) {
        _promise = promise;
    }

    protected void resolve(T value) {
        if (_rejected || _resolved)  {
            return;
        }

        _resolved = true;
        _resolvedValue = value;

        if (_promise != null) {
            _promise.setResolved(this);
        }
    }

    protected void reject(Throwable e) {
        if (_rejected || _resolved)  {
            return;
        }

        --_retries;
        if (_retries>=0) {
            triggerCall(_params);
            return;
        }

        _rejected = true;

        if (_promise != null) {
            _promise.setRejected(e);
        }
    }

    public void prepare() {
        _rejected = _resolved = false;
    }

    protected Promise getPromise() {
        return _promise;
    }

    public T getResolvedValue() {
        return _resolvedValue;
    }
}
