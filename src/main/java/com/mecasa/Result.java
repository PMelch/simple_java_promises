package com.mecasa;

/**
 * Created by pmelc on 25/01/16.
 *
 *
 * copied the Consumer interface from Java 8 JDK in order to being compatible with java 1.6
 */
public interface Result<T>  {
    void accept(T t);
}
