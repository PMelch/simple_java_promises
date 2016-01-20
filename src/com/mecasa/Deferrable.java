package com.mecasa;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:28
 */
public abstract class Deferrable<T> {

    Promise getPromise() {
        return new Promise(this);
    }

    abstract T call(Object... params);
}
