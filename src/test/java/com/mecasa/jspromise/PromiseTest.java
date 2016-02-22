package com.mecasa.jspromise;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

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
        Promise.setExecutorProvider(new Promise.ExecutorProvider() {
            public ExecutorService getExecutor() {
                return Executors.newFixedThreadPool(4);
            }
        });
    }

    @After
    public void tearDown() throws Exception {

    }


    @Test
    public void testCallParameters() throws Exception {
        BlockingCall<String> asyncCall = new BlockingCall<String>() {
            public void call(Object... params) throws Exception {
                resolve("Hello");
            }
        };

        Promise.when(asyncCall)
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
        BlockingCall<String> asyncCall = new BlockingCall<String>() {
            public void call(Object... params) throws Exception {
                Thread.sleep(100);

                resolve("Hello");
            }
        };
        long ct1 = System.currentTimeMillis();
        Promise.when(asyncCall)
                .waitForCompletion();
        long ct2 = System.currentTimeMillis();
        final long diff = ct2 - ct1;
        assertTrue(diff >= 100);
    }


    @Test
    public void testPromiseMultiCompletion() throws Exception {

        Result<Object[]> resultCallback = mockResultCallback();

        long ct1 = System.currentTimeMillis();
        Promise.when(new BlockingCall<String>() {
            public void call(Object... params) throws Exception {
                Thread.sleep(100);
                resolve("Hello");
            }
        }, new BlockingCall<String>() {
            public void call(Object... params) throws Exception {
                Thread.sleep(200);
                resolve("World");
            }
        }).resolve(resultCallback)
          .reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                fail();
            }
        }).waitForCompletion();


        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;
        assertTrue(diff >= 200);
        assertTrue(diff <= 220);

        ArgumentCaptor<Object[]> argumentCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(resultCallback).accept(argumentCaptor.capture());

        Object[] value = argumentCaptor.getValue();
        assertEquals(2, value.length);
        assertEquals("Hello", value[0]);
        assertEquals("World", value[1]);
    }

    @Test
    public void testParallelExecution() throws Exception {
        long ct1 = System.currentTimeMillis();

        Promise.when(new BlockingCall<String>() {
            @Override
            protected void call(Object... params) throws Throwable {
                Thread.sleep(100);
                resolve("Hello");
            }
        }, new BlockingCall<String>() {
            @Override
            protected void call(Object... params) throws Throwable {
                Thread.sleep(100);
                resolve("World");
            }
        }).waitForCompletion();

        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;
        assertTrue(diff < 200);

    }

    @Test
    public void testPromiseCompletionChain() throws Exception {
        long ct1 = System.currentTimeMillis();

        Promise.when(new AsyncCall<String>() {
            public void call(Object... params) throws Exception {
                Thread.sleep(100);
                resolve("Hello");
            }
        }).then(new AsyncCall<String>() {

            public void call(Object... params) throws Exception {
                Thread.sleep(100);
                resolve("World");
            }
        }).waitForCompletion();

        long ct2 = System.currentTimeMillis();
        assertTrue(ct2 >= ct1 + 200);
    }


    @Test
    public void testError() throws Exception {
        Result<Throwable> errorHandler = mockRejectCallback();
        Promise.when(new AsyncCall<String>() {
            public void call(Object... params) throws Exception {
                reject(new IllegalArgumentException("Error"));
            }
        })
                .then(new AsyncCall<String>() {
                    public void call(Object... params) throws Exception {
                        fail();
                        resolve(null);
                    }
                })
                .resolve(new Result<Object[]>() {
                    public void accept(Object[] objects) {
                        fail();
                    }
                })
                .reject(errorHandler);

        verify(errorHandler).accept(any(Exception.class));
    }



    @Test
    public void testResolve() throws Exception {
        Promise.when(new AsyncCall<String>() {
            public void call(Object... params) throws Exception {
                resolve("Foo");
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
        Result<Throwable> errorHandler = mockRejectCallback();
        Result<Object[]> resultHandler = mockResultCallback();

        Promise.when(new BlockingCall<String>() {
            public void call(Object... params) throws Exception {
                resolve(new Scanner(new URL("http://www.google.com").openStream(), "UTF-8").useDelimiter("\\A").next());
            }
        }, new BlockingCall<String>() {
            public void call(Object... params) throws Exception {
                resolve(new Scanner(new URL("http://www.example.com").openStream(), "UTF-8").useDelimiter("\\A").next());
            }
        }).resolve(resultHandler).reject(errorHandler).waitForCompletion();

        ArgumentCaptor<Object[]> argumentCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(resultHandler).accept(argumentCaptor.capture());
        verify(errorHandler, never()).accept(any(Throwable.class));

        Object[] values = argumentCaptor.getValue();
        assertEquals(2, values.length);
        assertTrue(((String)values[0]).length()>0);
        assertTrue(((String)values[1]).length()>0);
    }


    @Test
    public void testRetryPerDeferrable() throws Exception {
        BlockingCall<String> retryAsyncCall = new BlockingCall<String>() {
            private int tryNum = 0;

            public void call(Object... params) throws Exception {
                Thread.sleep(100);
                if (tryNum++ < 3) {
                    reject(new IllegalAccessError());
                }
                resolve("Foo");
            }
        };

        BlockingCall<String> delayedAsyncCall = new BlockingCall<String>() {
            @Override
            public void call(Object... params) throws Exception {
                Thread.sleep(1000);
                resolve("Bar");
            }
        };

        long ct1 = System.currentTimeMillis();
        Promise.when(delayedAsyncCall, retryAsyncCall.retriesWithDelay(3, 100))
                .reject(new Result<Throwable>() {
                    public void accept(Throwable e) {
                        fail();
                    }
                })
                .resolve(new Result<Object[]>() {
                    public void accept(Object[] objects) {
                        assertEquals(2, objects.length);
                        assertEquals("Foo", objects[1]);
                        assertEquals("Bar", objects[0]);
                    }
                }).waitForCompletion();
        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;

        // make sure that only the first deferrable was re-run after failing.
        // the second one ( the one with the long delay ) was only run once
        assertTrue(diff < 1050);
    }


    @Test
    public void testParameters() throws Exception {
        // empty AsyncCall list is illegal, so expect an Exception
        try {
            Promise.when().waitForCompletion();
            fail();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testThreadChaining() throws Exception {
        BlockingCall<String> blockingCall = new BlockingCall<String>() {
            @Override
            protected void call(Object... params) throws Throwable {
                Thread.sleep(100);
                resolve("Foo");
            }
        };


        long ct1 = System.currentTimeMillis();
        Promise
                .when(blockingCall)
                .then(blockingCall)
                .waitForCompletion();
        long ct2 = System.currentTimeMillis();
        assertTrue(ct2-ct1>200);
        assertTrue(ct2-ct1<250);
    }



    @Test
    public void testThreadError() throws Exception {

        BlockingCall<String> blockingCall = new BlockingCall<String>() {
            @Override
            protected void call(Object... params) throws Throwable {
                Thread.sleep(100);
                reject(new IllegalArgumentException("error"));
            }
        };

        final Result<Throwable> errorHandler = spy(new Result<Throwable>() {
            public void accept(Throwable throwable) {
            }
        });
        final Result<Object[]> resultHandler = spy(new Result<Object[]>() {
            public void accept(Object[] objects) {
            }
        });

        Promise.when(blockingCall)
               .reject(errorHandler)
               .resolve(resultHandler)
               .waitForCompletion();

        verify(errorHandler).accept(any(IllegalArgumentException.class));
        verify(resultHandler, never()).accept(any(Object[].class));

    }


    @Test
    public void testValidNullResult() throws Exception {
        Result<Object[]> resolveCallback = mockResultCallback();
        Result<Throwable> rejectCallback = mockRejectCallback();

        Promise.when(new AsyncCall<String>() {
            @Override
            public void call(Object... params) throws Exception {
                resolve(null);
            }
        }).resolve(resolveCallback)
          .reject(rejectCallback);

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(resolveCallback).accept(captor.capture());
        Object[] objects = captor.getValue();
        assertEquals(1, objects.length);
        assertEquals(null, objects[0]);

    }



    @Test
    public void testPassingValues() throws Exception {
        // we create a promise that should pass through the values to each stage. only the
        // last stage should return only one value to the resolve call.


        final AsyncCall<Integer> asyncCall1 = spy(new AsyncCall<Integer>() {
            @Override
            public void call(Object... params) throws Exception {
                resolve(((Integer) params[0]) + 20);
            }
        });


        final AsyncCall<Integer> asyncCall2 = spy(new AsyncCall<Integer>() {
            @Override
            public void call(Object... params) throws Exception {
                resolve(((Integer) params[0]) + 30);
            }
        });

        final Result<Object[]> resultHandler = spy(new Result<Object[]>() {
            public void accept(Object[] objects) {
            }
        });

        Promise.when(new AsyncCall<Integer>() {
            @Override
            public void call(Object... params) throws Exception {
                resolve(10);
            }
        })
          .then(asyncCall1)
          .then(asyncCall2)
          .resolve(resultHandler)
          .reject(new Result<Throwable>() {
              public void accept(Throwable throwable) {
                  throwable.printStackTrace();
              }
          });

        ArgumentCaptor<Object> argumentCaptor1 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> argumentCaptor2 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object[]> argumentCaptor3 = ArgumentCaptor.forClass(Object[].class);
        try {
            verify(asyncCall1).call(argumentCaptor1.capture());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        Object values1 = argumentCaptor1.getValue();
        assertEquals(10, values1);

        try {
            verify(asyncCall2).call(argumentCaptor2.capture());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        Object values2 = argumentCaptor2.getValue();
        assertEquals(30, values2);

        verify(resultHandler).accept(argumentCaptor3.capture());

        Object[] values3 = argumentCaptor3.getValue();
        assertEquals(1, values3.length);
        assertEquals(60, values3[0]);
    }


    @Test
    public void testAsyncBehaviour() throws Exception {

        Result<Object[]> resultCallback = mockResultCallback();

        long ct1 = System.currentTimeMillis();

        Promise promise = Promise.when(BlockingCall.wrap(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        })).resolve(resultCallback);

        long ct2 = System.currentTimeMillis();
        // make sure the promise code was executed in less than 100ms => so the sleep was executed in another thread.
        assertTrue(ct2-ct1<100);

        promise.waitForCompletion();
        verify(resultCallback).accept(any(Object[].class));
    }

    @Test
    public void testFulfilledOnResolve() throws Exception {
        Runnable fulfilledRunnable = mock(Runnable.class);
        Promise.when(new BlockingCall<Integer>() {
            @Override
            protected void call(Object... params) throws Throwable {
                resolve(10);
            }
        }).fulfilled(fulfilledRunnable).waitForCompletion();

        verify(fulfilledRunnable).run();
    }

    @Test
    public void testFulfilledOnRejected() throws Exception {
        Runnable fulfilledRunnable = mock(Runnable.class);

        Promise.when(new BlockingCall<Integer>() {
            @Override
            protected void call(Object... params) throws Throwable {
                reject(new IllegalArgumentException());
            }
        }).fulfilled(fulfilledRunnable).waitForCompletion();

        verify(fulfilledRunnable).run();
    }

    @Test
    public void testInitWithExecutor() throws Exception {
        Executor executor = mock(Executor.class);
        Promise promise = Promise.when(false, BlockingCall.wrap(mock(Runnable.class)));
        promise.setExecutor(executor);
        promise.start();
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    public void testTimerCallback() throws Exception {
        Result<Object[]> result = mockResultCallback();
        Promise.when(new AsyncCall<String>() {
            @Override
            protected void call(Object... params) throws Throwable {
                ScheduledExecutorService service = Executors
                        .newSingleThreadScheduledExecutor();
                service.schedule(new Runnable() {
                    public void run() {
                        resolve(null);
                    }
                }, 1, TimeUnit.SECONDS);
            }
        }).resolve(result).waitForCompletion();

        verify(result).accept(any(Object[].class));
    }

    @SuppressWarnings("unchecked")
    private Result<Object[]> mockResultCallback() {
        return (Result<Object[]>)mock(Result.class);
    }

    @SuppressWarnings("unchecked")
    private Result<Throwable> mockRejectCallback() {
        return (Result<Throwable>)mock(Result.class);
    }

    @Test
    public void testDelayedStart() throws Exception {
        Runnable runnable = mock(Runnable.class);
        Promise promise = Promise.when(false, BlockingCall.wrap(runnable));
        verify(runnable, never()).run();

        try {
            promise.waitForCompletion();
            fail();
        } catch (IllegalStateException e) {
            // expected exception
        }

        promise.start();
        verify(runnable).run();

        // no exceptions should be thrown now.
        promise.waitForCompletion();
    }
}
