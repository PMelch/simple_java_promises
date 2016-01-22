package com.mecasa;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Vector;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:39
 */
public class Promise  {
    // default executor
    private static ExecutorService sExecutor = new ThreadPoolExecutor(1, 10, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
    private final Vector<Future<?>> _futures;
    private final Vector<Object> _values = new Vector<>();
    private Throwable _rejected;


    public static void setExecutor(ExecutorService executor) {
        sExecutor = executor;
    }

    public static Promise when(Deferrable<String> deferrable) {
        return new Promise(deferrable);
    }

    public static Promise all(Deferrable<?>... deferrableList) {
        return new Promise(deferrableList, null);
    }

    public Promise() {
        _futures = new Vector<>();
    }

    private Promise(Throwable rejected) {
        this();
        _rejected = rejected;
    }

    public <T> Promise(final Deferrable<T> deferrable) {
        this();

        synchronized (sExecutor) {
            synchronized (_futures) {
                _futures.add(sExecutor.submit(()->deferrable.call()));
            }
        }
    }

    public Promise(@NotNull Deferrable<?>[] deferrableList, @Nullable final Vector _values) {
        this();

        Object[] values = _values != null ? _values.toArray(new Object[_values.size()]) : null;
        synchronized (sExecutor) {
            synchronized (_futures) {
                for (Deferrable<?> deferrable : deferrableList) {
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
                            _rejected = _rejected == null ? e : _rejected;
                        } catch (ExecutionException e) {
                            _rejected = _rejected == null ? e.getCause() : _rejected;
                        }
                    }
                });
                // remove all pending futures
                _futures.clear();
            }
        }
    }

    public Promise then(Deferrable<?>... deferrableList) {
        try {
            waitForAll();
        } catch (InterruptedException ignored) {
        }
        // forward any rejected error to the next promise
        if (_rejected != null) {
            return new Promise(_rejected);
        }
        return new Promise(deferrableList, _values);
    }


    public Promise resolve(Consumer<Object[]> result) {
        try {
            waitForAll();
        } catch (InterruptedException ignored) {
        }
        if (_rejected == null ) {
            result.accept(_values.toArray(new Object[_values.size()]));
        }
        return this;
    }

    public void reject(Consumer<Throwable> e) {
        try {
            waitForAll();
        } catch (InterruptedException ignored) {
        }
        if (_rejected != null) {
            e.accept(_rejected);
        }
    }

}
