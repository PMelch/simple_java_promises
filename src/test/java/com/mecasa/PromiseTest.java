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


    @Test
    public void testCallParameters() throws Exception {
        Deferrable<String> deferrable = new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                return "Hello";
            }
        };

        Promise.when(deferrable)
                .resolve(new Result<Object[]>() {
                    public void accept(Object[] objects) {
                        assertEquals(1, objects.length);
                        assertEquals("Hello", objects[0]);
                    }
                }).reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                fail();
            }
        });


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
        }).resolve(new Result<Object[]>() {
            public void accept(Object[] objects) {
                assertEquals(2, objects.length);
                assertEquals("Hello", objects[0]);
                assertEquals("World", objects[1]);

            }
        }).reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                fail();
            }
        });


        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;
        System.out.println("200+100 => " + diff);
        assertTrue(diff >= 200);
        assertTrue(diff <= 250);

    }

    @Test
    public void testParallelExecution() throws Exception {
        long ct1 = System.currentTimeMillis();

        Promise.when(new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                Thread.sleep(100);
                return "Hello";
            }
        }, new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                Thread.sleep(100);
                return "World";
            }
        }).waitForCompletion();

        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;
        assertTrue(diff < 200);

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
                return new Scanner(new URL("http://www.google.com").openStream(), "UTF-8").useDelimiter("\\A").next();
            }
        }, new Deferrable<String>() {
            public String call(Object... params) throws Exception {
                return new Scanner(new URL("http://www.example.com").openStream(), "UTF-8").useDelimiter("\\A").next();
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
    public void testRetry() throws Exception {
        Deferrable<String> failingDeferrable = new Deferrable<String>() {
            private int tryNum = 0;

            public String call(Object... params) throws Exception {
                if (tryNum++ < 3) {
                    throw new IllegalAccessError();
                }
                return "Foo";
            }
        };


        Promise.when(failingDeferrable)
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


        long ct1 = System.currentTimeMillis();
        Promise.when(failingDeferrable)
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
        long ct2 = System.currentTimeMillis();
        // make sure the working Deferrable has not been re-run
        assertTrue(ct2-ct1 < 100);
    }
    @Test
    public void testRetryPerDeferrable() throws Exception {
        Deferrable<String> retryDeferrable = new Deferrable<String>() {
            private int tryNum = 0;

            public String call(Object... params) throws Exception {
                if (tryNum++ < 3) {
                    throw new IllegalAccessError();
                }
                return "Foo";
            }
        };

        Deferrable<String> delayedDeferrable = new Deferrable<String>() {
            @Override
            String call(Object... params) throws Exception {
                Thread.sleep(1000);
                return "Bar";
            }
        };

        long ct1 = System.currentTimeMillis();
        Promise.when(delayedDeferrable, retryDeferrable.retriesWithDelay(3, 100))
                .reject(new Result<Throwable>() {
                    public void accept(Throwable throwable) {
                        fail();
                    }
                })
                .resolve(new Result<Object[]>() {
                    public void accept(Object[] objects) {
                        assertEquals(2, objects.length);
                        assertEquals("Foo", objects[1]);
                        assertEquals("Bar", objects[0]);
                    }
                });
        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;

        // make sure that only the first deferrable was re-run after failing.
        // the second one ( the one with the long delay ) was only run once
        assertTrue(diff < 1400);
    }


    @Test
    public void testParameters() throws Exception {
        // empty Deferrable list is illegal, so expect an Exception
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

    private static abstract class AsyncDeferrable<T> extends Deferrable<T> implements Thread.UncaughtExceptionHandler{
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

        // test with timeout set for a promise stage

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
        Promise.when(deferrable).timeout(100)
                .retries(2)
                .waitForCompletion();
        long cp2 = System.currentTimeMillis();
        long diff = cp2 - cp1;
        System.out.println("diff: "+diff);

        // make sure the executor shut down the timed out Futures.
        assertTrue(diff < 400);


        // test timeout set for a single Deferrable

        Promise.when(deferrable.timeout(100))
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

        cp1 = System.currentTimeMillis();
        Promise.when(deferrable.timeout(100))
                .retries(2)
                .waitForCompletion();
        cp2 = System.currentTimeMillis();
        diff = cp2 - cp1;
        System.out.println("diff: "+diff);

        // make sure the executor shut down the timed out Futures.
        assertTrue(diff < 400);



        // retry on Deferrable
        cp1 = System.currentTimeMillis();
        Promise.when(deferrable.timeout(100).retries(2))
                .waitForCompletion();
        cp2 = System.currentTimeMillis();
        diff = cp2 - cp1;
        System.out.println("diff: "+diff);

        // make sure the executor shut down the timed out Futures.
        assertTrue(diff < 400);

    }

    @Test
    public void testValidNullResult() throws Exception {
        Promise.when(new Deferrable<String>() {
            @Override
            String call(Object... params) throws Exception {
                return null;
            }
        }).resolve(new Result<Object[]>() {
            public void accept(Object[] objects) {
                assertEquals(1, objects.length);
                assertEquals(null, objects[0]);
            }
        }).reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                fail();
            }
        });
    }


    @Test
    public void testPassingValues() throws Exception {
        // we create a promise that should pass through the values to each stage. only the
        // last stage should return only one value to the resolve call.

        Promise.when(new Deferrable<Integer>() {
            @Override
            Integer call(Object... params) throws Exception {
                return 10;
            }
        }).setExecutor( new BlockingTestExecutor())
          .passResultsThrough(true)
          .then(new Deferrable<Integer>() {
            @Override
            Integer call(Object... params) throws Exception {
                assertEquals(1, params.length);
                return ((Integer)params[0]) + 20;
            }
        }).then(new Deferrable<Integer>() {
            @Override
            Integer call(Object... params) throws Exception {
                assertEquals(2, params.length);
                return ((Integer)params[0]) + ((Integer)params[1]) + 30;
            }
        }).passResultsThrough(false)    // collapse to one result
          .resolve(new Result<Object[]>() {
            public void accept(Object[] objects) {
                assertEquals(1, objects.length);
                assertEquals(70, objects[0]);
            }
        });

    }

}
