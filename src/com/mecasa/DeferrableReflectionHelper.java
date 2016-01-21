package com.mecasa;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 18:48
 */
public class DeferrableReflectionHelper {
    public static <T> T callMethod(Object deferrable, String name, Class<?> returnType, Object[] objects) throws Exception {
        Class<?> cls = deferrable.getClass();
        if (objects == null || objects.length == 0 ) {
            // find method with no parameters
            try {
                Method method = cls.getDeclaredMethod(name);
                if (returnType!=null && !method.getReturnType().equals(returnType)) {
                    throw new NoSuchMethodException("method must return a value of "+returnType.getCanonicalName());
                }
                return (T) method.invoke(deferrable);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                throw (Exception)e.getCause();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        Class<?>[] typeList = new Class[objects.length];
        for (int t=0; t<objects.length; t++) {
            typeList[t] = objects[t].getClass();
        }
        try {
            Method method = cls.getDeclaredMethod(name, typeList);
            if (returnType!=null && !method.getReturnType().equals(returnType)) {
                throw new NoSuchMethodException("method must return a value of "+returnType.getCanonicalName());
            }
            return (T) method.invoke(deferrable, objects);
        } catch (NoSuchMethodException e) {
        } catch (InvocationTargetException e) {
            throw (Exception)e.getCause();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }
}
