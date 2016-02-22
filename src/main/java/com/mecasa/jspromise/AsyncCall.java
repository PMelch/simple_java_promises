package com.mecasa.jspromise;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:28
 *
 * An AsyncCall can be used for all tasks that are already asynchronous in some way. They are called directly without
 * any extra added async logic.
 */
public abstract class AsyncCall<T> extends Call<T> {

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
