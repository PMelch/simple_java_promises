package com.mecasa;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:28
 */
public interface Deferrable<T> {

    default T call(Object... params) throws Exception {
        return (T)DeferrableReflectionHelper.callMethod(this, "call", null, params);
    }
}
