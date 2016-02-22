package com.mecasa.jspromise;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:28
 */
public abstract class AsyncCall<T> extends Call<T, AsyncCall<T>> {

    @Override
    final protected void triggerCall(Object... params) {
        super.triggerCall(params);

        try {
            call(params);
        } catch (Throwable e) {
            reject(e);
        }
    }
}
