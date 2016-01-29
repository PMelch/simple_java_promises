package com.mecasa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.*;

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


    public static class TestDeferrable implements Deferrable<String> {

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

        final TestDeferrable deferrable1 = new TestDeferrable() {
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
        final long diff = ct2 - ct1;
        System.out.println("DIff: "+diff);
        assertTrue(diff >= 100);
    }


    @Test
    public void testPromiseMultiCompletion() throws Exception {
        long ct1 = System.currentTimeMillis();

        final TestDeferrable resultDeferrable = new TestDeferrable() {
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
        System.out.println("200+100 => " + diff);
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
                        System.out.println(throwable.getMessage());
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
                if (tryNum++ < 3) {
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
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Promise.when(null).waitForCompletion();
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testThreadChaining() throws Exception {
        Deferrable<String> deferrable = new AsyncDeferrable<String>() {
            public String call(Object... params) throws Exception {
                final Thread deferrable = createThread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                        synchronized (getSyncObject()) {
                            getSyncObject().notify();
                        }
                    }
                });
                deferrable.start();

                System.out.println("waiting for thread to finish");
                synchronized (getSyncObject()) {
                    getSyncObject().wait();
                }
                System.out.println("done");

                rejectIfError();
                return "Foo";
            }
        };


        Promise
                .when(deferrable)
                .then(deferrable)
                .waitForCompletion();

    }

    private static abstract class AsyncDeferrable<T> implements Deferrable<T>, Thread.UncaughtExceptionHandler{
        private Throwable _rejectReason;
        private final Object _syncObject = new Object();

        public void uncaughtException(Thread t, Throwable e) {
            _rejectReason = e;
            synchronized (_syncObject) {
                _syncObject.notify();
            }
        }

        protected Thread createThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler(this);
            return thread;
        }

        final protected Object getSyncObject() {
            return _syncObject;
        }

        final protected void rejectIfError() throws Exception {
            if (_rejectReason instanceof Exception) {
                throw (Exception)_rejectReason;
            }

            throw new Exception(_rejectReason.getMessage());
        }
    }


    @Test
    public void testThreadError() throws Exception {
        Deferrable<String> deferrable = new AsyncDeferrable<String>() {
            public String call(Object... params) throws Exception {
                final Thread deferredThread = createThread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                        throw new IllegalArgumentException("error");
                    }
                });

                deferredThread.start();

                System.out.println("waiting for thread to finish");
                synchronized (getSyncObject()) {
                    getSyncObject().wait();
                }
                // in case there was an error, reject the deferrable
                System.out.println("done");

                rejectIfError();
                return "Foo";
            }
        };


        Promise
                .when(deferrable)
                .reject(new Result<Throwable>() {
                    public void accept(Throwable throwable) {
                        // success
                        System.out.println("Error: "+throwable.getMessage());
                    }
                }).resolve(new Result<Object[]>() {
                    public void accept(Object[] objects) {
                        fail();
                    }
                });

    }

    @Test
    public void testTimeout() throws Exception {
        Deferrable deferrable = new Deferrable() {
            public Object call(Object... params) throws Exception {
                Thread.sleep(1000);
                return "Foo";
            }
        };

        Promise.when(deferrable).timeout(100)
                .reject(new Result<Throwable>() {
                    public void accept(Throwable throwable) {
                        // we should receive the TimeoutException
                        if (!(throwable instanceof TimeoutException)) {
                            fail();
                        }
                    }
                }).resolve(new Result<Object[]>() {
                     public void accept(Object[] objects) {
                        fail();
                    }
                });


        long cp1 = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        Promise.when(deferrable).timeout(100)
                .retries(2)
                .waitForCompletion();
        long cp2 = System.currentTimeMillis();
        final long diff = cp2 - cp1;
        System.out.println("diff: "+diff);

        // make sure the executor shut down the timed out Futures.
        assertTrue(diff < 400);

    }
}
