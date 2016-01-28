package com.mecasa;

/**
 * User: peter
 * Date: 28.01.2016
 * Time: 17:27
 */
public abstract class ThreadDeferrable<T> implements Deferrable<T>, Thread.UncaughtExceptionHandler{
    private Throwable _rejectReason;
    private Object _syncObject = new Object();

    public void uncaughtException(Thread t, Throwable e) {
        _rejectReason = e;
        synchronized (_syncObject) {
            _syncObject.notify();
        }
    }

    protected Object getSyncObject() {
        return _syncObject;
    }

    protected void rejectIfError() throws Exception {
        if (_rejectReason instanceof Exception) {
            throw (Exception)_rejectReason;
        }

        throw new Exception(_rejectReason.getMessage());
    }
}
