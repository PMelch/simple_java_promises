package com.mecasa;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:28
 */
public interface Deferrable<T> {

    T call(Object... params) throws Exception;
}
