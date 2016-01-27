package com.mecasa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * User: peter
 * Date: 18.01.2016
 * Time: 20:52
 */
public class PromiseTest {

    @Before
    public void setUp() throws Exception {
        Promise.setExecutorServiceProvider(new Promise.ExecutorServiceProvider() {
            public ExecutorService getExecutorService() {
                return Executors.newFixedThreadPool(4);
            }
        });
    }

    @After
    public void tearDown() throws Exception {

    }


    public static class TestDefererrable implements Deferrable<String> {

        public Object[] params;

        public String call(Object... params) {
            this.params = params;
            return null;
        }
    }

    @Test
    public void testCallParameters() throws Exception {
        Deferrable<String> deferrable = new Deferrable<String>() {
            public String call(Object... params) throws Exception {
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
                .waitForCompletion();

        assertEquals(1, deferrable1.params.length);
        assertEquals("Hello", deferrable1.params[0]);

    }


    @Test
    public void testPromiseCompletion() throws Exception {
        Deferrable<String> deferrable = new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                Thread.sleep(100);
                return "Hello";
            }
        };
        long ct1 = System.currentTimeMillis();
        Promise.when(deferrable).waitForCompletion();
        long ct2 = System.currentTimeMillis();
        assertTrue(ct2 >= ct1 + 100);
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
        Promise.when(new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                Thread.sleep(100);
                return "Hello";
            }
        }, new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                Thread.sleep(200);
                return "World";
            }
        }).then(resultDeferrable)
                .waitForCompletion();

        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;
        System.out.println("200+100=" + diff);
        assertTrue(diff >= 200);
        assertTrue(diff <= 250);

        assertEquals(2, resultDeferrable.params.length);
        assertEquals("Hello", resultDeferrable.params[0]);
        assertEquals("World", resultDeferrable.params[1]);
    }


    @Test
    public void testPromiseCompletionChain() throws Exception {
        long ct1 = System.currentTimeMillis();

        Promise.when(new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                Thread.sleep(100);
                return "Hello";
            }
        }).then(new Deferrable<String>() {

            public String call(Object... params) throws Exception {
                Thread.sleep(100);
                return "World";
            }
        }).waitForCompletion();

        long ct2 = System.currentTimeMillis();
        assertTrue(ct2 >= ct1 + 200);
    }


    @Test
    public void testError() throws Exception {
        Promise.when(new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                throw new IllegalArgumentException("Error");
            }
        }).then(new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                fail();
                return null;
            }
        }).resolve(new Result<Object[]>() {
            public void accept(Object[] objects) {
                fail();
            }
        })
                .reject(new Result<Throwable>() {
                    public void accept(Throwable throwable) {
                        System.out.println(throwable);
                    }
                });
    }


    @Test
    public void testResolve() throws Exception {
        Promise.when(new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                return "Foo";
            }
        }).resolve(new Result<Object[]>() {
            public void accept(Object[] objects) {
                assertEquals(1, objects.length);
                assertEquals("Foo", objects[0]);
            }
        });
    }

    @Test
    public void testWebAccess() throws Exception {
        Promise.when(new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                return new Scanner(new URL("http://www.melchart.com").openStream(), "UTF-8").useDelimiter("\\A").next();
            }
        }, new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                return new Scanner(new URL("http://www.orf.at").openStream(), "UTF-8").useDelimiter("\\A").next();
            }
        }).resolve(new Result<Object[]>() {
            public void accept(Object[] objects) {
                for (Object o : objects) {
                    System.out.println(((String) o).length());
                }
                System.out.println("done");
            }
        }).reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                System.out.println("ERROR: " + throwable.getMessage());
            }
        });
    }

    @Test
    public void testSettingExecutor() throws Exception {
        ExecutorService executorService = mock(ExecutorService.class);
        when(executorService.submit(any(Callable.class))).thenReturn(mock(Future.class));

        final Deferrable<String> deferrable = new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                return "Foo";
            }
        };
        Promise.when(deferrable).setExecutor(executorService)
                .then(deferrable)
                .waitForCompletion();

        // make sure the executor has been passed through all promises
        verify(executorService, times(2)).submit(any(Callable.class));
    }

    @Test
    public void testRetry() throws Exception {
        Promise.when(new Deferrable<String>() {
            private int tryNum = 0;
            public String call(Object... params) throws Exception {
                if (tryNum++<3) {
                    throw new IllegalAccessError();
                }
                return "Foo";
            }
        })
        .retriesWithDelay(3, 100)
        .reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                fail();
            }
        })
        .resolve(new Result<Object[]>() {
            public void accept(Object[] objects) {
                assertEquals(1, objects.length);
                assertEquals("Foo", objects[0]);
            }
        });
    }

    @Test
    public void testParameters() throws Exception {
        try {
            Promise.when().waitForCompletion();
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            Promise.when(null).waitForCompletion();
            fail();
        } catch (IllegalArgumentException e) {
        }

    }
}