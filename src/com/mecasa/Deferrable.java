package com.mecasa;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:28
 */
public class Deferrable<T> {

    Promise getPromise() {
        return new Promise(this);
    }

    T call(Object... params) {
        return (T)DeferrableReflectionHelper.callMethod(this, "call", null, params);
    }
}
