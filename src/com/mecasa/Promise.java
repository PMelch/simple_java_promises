package com.mecasa;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.*;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:39
 */
public class Promise  {
    // default executor
    private static ExecutorService sExecutor = new ThreadPoolExecutor(1, 10, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
    private final Vector<Future<?>> _futures;
    private Vector _values = new Vector();


    public static void setExecutor(ExecutorService executor) {
        sExecutor = executor;
    }



    public static Promise all(Deferrable<?>... deferrables) {
        return new Promise(deferrables, null);
    }

    public Promise() {
        _futures = new Vector<Future<?>>();
    }

    public <T> Promise(final Deferrable<T> deferrable) {
        this();

        synchronized (sExecutor) {
            synchronized (_futures) {
                _futures.add(sExecutor.submit(() -> deferrable.call(null)));
            }
        }
    }

    public Promise(@NotNull Deferrable<?>[] deferrables, @Nullable final Vector _values) {
        this();

        Object[] values = _values != null ? _values.toArray(new Object[_values.size()]) : null;
        synchronized (sExecutor) {
            synchronized (_futures) {
                for (Deferrable<?> deferrable : deferrables) {
                    _futures.add(sExecutor.submit(() -> deferrable.call(values)));
                }
            }
        }
    }



    public void waitForAll() throws InterruptedException {
        synchronized (sExecutor) {
            synchronized (_futures) {
                _futures.stream().forEach((future) -> {
                    synchronized (_values) {
                        try {
                            _values.add(future.get());
                        } catch (InterruptedException e) {
                        } catch (ExecutionException e) {
                        }
                    }
                });
            }
        }
    }

    public Promise then(Deferrable<?>... deferrables) {
        try {
            waitForAll();
        } catch (InterruptedException e) {
        }
        return new Promise(deferrables, _values);
    }

}
