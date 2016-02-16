package com.mecasa.jspromise.helper;

import com.mecasa.jspromise.Deferrable;

/**
 * User: peter
 * Date: 16.02.2016
 * Time: 19:44
 */
public abstract class AsyncDeferrable<T> extends Deferrable<T>{
    private Object _syncObject = new Object();
    private Exception _rejectReason;
    private T _result;

    public T call(Object... params) throws Exception {
        // trigger the async operation
        triggerAsyncOperation(params);

        // wait for the async operation to be complete.
        synchronized (_syncObject) {
            _syncObject.wait();
        }

        // if the operation has been rejected, throw the passed exception.
        if (_rejectReason != null) {
            throw _rejectReason;
        }

        return _result;
    }

    /**
     * overwrite this method and trigger the async operation there.
     * once the operation is complete, you must {@link #resolve(Object)} or {@link #reject(Exception)}.
     * the {@link #call(Object...)} method is blocking until one of the two methods is called.
     * @param params
     */
    abstract void triggerAsyncOperation(Object... params);

    protected void resolve(T result) {
        _result = result;
        synchronized (_syncObject) {
            _syncObject.notifyAll();
        }
    }

    protected void reject(Exception throwable) {
        _rejectReason = throwable;
        synchronized (_syncObject) {
            _syncObject.notifyAll();
        }
    }
}
