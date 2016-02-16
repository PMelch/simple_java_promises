package com.mecasa.jspromise.helper;

import com.mecasa.jspromise.Promise;
import com.mecasa.jspromise.Result;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * User: peter
 * Date: 16.02.2016
 * Time: 19:56
 */
public class AsyncDeferrableTest {

    public static final int DELAY = 100;

    @Test
    public void testResolve() throws Exception {
        AsyncDeferrable<String> asyncDeferrable = new AsyncDeferrable<String>() {
            @Override
            void triggerAsyncOperation(Object... params) {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(DELAY);
                        } catch (InterruptedException e) {
                            reject(e);
                        }
                        resolve("Foo");
                    }
                }).start();
            }
        };

        long ct1 = System.currentTimeMillis();
        Promise.when(asyncDeferrable)
                .resolve(new Result<Object[]>() {
                    public void accept(Object[] objects) {
                        assertEquals(1, objects.length);
                        assertEquals("Foo", objects[0]);
                    }
                })
                .waitForCompletion();

        long ct2 = System.currentTimeMillis();

        assertTrue(ct2-ct1> DELAY);
    }

    @Test
    public void testReject() throws Exception {

        AsyncDeferrable<String> asyncDeferrable = new AsyncDeferrable<String>() {
            @Override
            void triggerAsyncOperation(Object... params) {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            System.out.println("sleeping for "+DELAY+"ms");
                            Thread.sleep(DELAY);
                        } catch (InterruptedException e) {
                        }
                        reject(new IllegalArgumentException());
                        System.out.println(".end");
                    }
                }).start();
            }
        };

        long ct1 = System.currentTimeMillis();
        Promise.when(asyncDeferrable)
                .resolve(new Result<Object[]>() {
                    public void accept(Object[] objects) {
                        fail();
                    }
                })
                .reject(new Result<Throwable>() {
                    public void accept(Throwable throwable) {
                        assertTrue(throwable instanceof IllegalArgumentException);
                    }
                })
                .waitForCompletion();

        long ct2 = System.currentTimeMillis();

        assertTrue(ct2-ct1> DELAY);

    }
}