package com.mecasa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executors;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * User: peter
 * Date: 18.01.2016
 * Time: 20:52
 */
public class PromiseTest {

    @Before
    public void setUp() throws Exception {
        Promise.setExecutor(Executors.newFixedThreadPool(10));
    }

    @After
    public void tearDown() throws Exception {

    }




    public static class TestDefererrable implements Deferrable<String> {

        public Object[] params;

        @Override
        public String call(Object... params) {
            this.params = params;
            return null;
        }
    }

    @Test
    public void testCallParameters() throws Exception {
        Deferrable<String> deferrable = new Deferrable<String>() {
            @Override
            public String call(Object... params) {
                return "Hello";
            }
        };


        final TestDefererrable deferrable1 = new TestDefererrable() {
            @Override
            public String call(Object... params) {
                super.call(params);
                return "World";
            }
        };

        Promise.when(deferrable)
                .then(deferrable1)
                .waitForAll();

        assertEquals(1, deferrable1.params.length);
        assertEquals("Hello", deferrable1.params[0]);

    }


    @Test
    public void testPromiseCompletion() throws Exception {
        Deferrable<String> deferrable = new Deferrable<String>() {
            @Override
            public String call(Object... params) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                return "Hello";
            }
        };
        long ct1 = System.currentTimeMillis();
        Promise.when(deferrable).waitForAll();
        long ct2 = System.currentTimeMillis();
        assertTrue(ct2>=ct1+100);
    }


    @Test
    public void testPromiseMultiCompletion() throws Exception {
        long ct1 = System.currentTimeMillis();

        final TestDefererrable resultDeferrable = new TestDefererrable() {
            @Override
            public String call(Object... params) {
                super.call(params);
                return "World";
            }
        };
        Promise.all(new Deferrable<String>() {
            @Override
            public String call(Object... params) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                return "Hello";
            }
        }, new Deferrable<String>() {
            @Override
            public String call(Object... params) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }
                return "World";
            }
        })
            .then(resultDeferrable)
            .waitForAll();

        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;
        assertTrue(diff>=200);
        assertTrue(diff<=250);

        assertEquals(2, resultDeferrable.params.length);
        assertEquals("Hello", resultDeferrable.params[0]);
        assertEquals("World", resultDeferrable.params[1]);
    }

    @Test
    public void testTypesParams() throws Exception {
        Promise.when(new Deferrable<String>() {
            @Override
            public String call(Object... params) {
                return "Hello";
            }
        }).then(new Deferrable<String>(){
            String call(String value) {
                assertEquals("Hello", value);
                return value;
            }
        });

    }

    @Test
    public void testPromiseCompletionChain() throws Exception {
        long ct1 = System.currentTimeMillis();

        Deferrable<String> deferrable = new Deferrable<String>() {
            @Override
            public String call(Object... params) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                return "Hello";
            }
        };
        Promise.when(deferrable)
                .then(new Deferrable<String>() {
                    @Override
                    public String call(Object... params) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                        return "World";
                    }
                })
                .waitForAll();

        long ct2 = System.currentTimeMillis();
        assertTrue(ct2>=ct1+200);
    }


    @Test
    public void testError() throws Exception {
        Promise.when(new Deferrable<String>() {
            public String call() {
                throw new IllegalArgumentException("Error");
            }
        }).then(new Deferrable<String>() {
            public String call() {
                return "Hansi";
            }
        }).reject(throwable -> System.out.println(throwable.getMessage()));

    }


    @Test
    public void testResolve() throws Exception {
        Promise.when(new Deferrable<String>() {
            public String call() {
                return "Hansi";
            }
        }).resolve((params)->{for (Object o : params) System.out.println(o);});

    }

}