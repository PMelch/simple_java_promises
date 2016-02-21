package com.mecasa.jspromise;

/**
 * Created by pmelc on 19/02/16.
 */
public abstract class Call<T,C extends Call> {
    private Promise _promise;
    private int _retryDelay = -1;
    private int _retries = -1;
    private boolean _rejected;
    private boolean _resolved;
    private Throwable _rejectedReason;
    private Object[] _params;
    private T _resolvedValue;

    protected void triggerCall(Object... params){
        // save for retries
        _params = params;
    }

    public C retriesWithDelay(int numRetries, int delay) {
        _retries = numRetries;
        _retryDelay = delay;
        return (C)this;
    }

    public C retries(int numRetries) {
        _retries = numRetries;
        _retryDelay = -1;
        return (C)this;
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

        _rejectedReason = e;
        _rejected = true;

        if (_promise != null) {
            _promise.setRejected(e);
        }
    }

    public void prepare() {
        _rejected = _resolved = false;
        _rejectedReason = null;
    }

    protected Promise getPromise() {
        return _promise;
    }

    public T getResolvedValue() {
        return _resolvedValue;
    }
}
