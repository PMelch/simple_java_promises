package com.mecasa.jspromise;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    // used to schedule retried tasks with a delay
    private static ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    private TimeUnit _retryDelayUnit;

    protected void triggerCall(Object... params){
        // save for retries
        _params = params;
    }

    protected abstract void call(Object... params) throws Throwable;

    public Call retriesWithDelay(int numRetries, int delay, TimeUnit delayUnit) {
        _retries = numRetries;
        _retryDelay = delay;
        _retryDelayUnit = delayUnit;
        return this;
    }

    public Call retries(int numRetries) {
        _retries = numRetries;
        _retryDelay = -1;
        return this;
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
        // are retries wanted?
        if (_retries>=0) {
            // should there be a delay between retries?
            if (_retryDelay > 0) {
                // schedule the retry at the specific time.
                service.schedule(new Runnable() {
                    public void run() {
                        triggerCall(_params);
                    }
                }, _retryDelay, _retryDelayUnit);
            } else {
                // retry immediately
                triggerCall(_params);
            }
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
