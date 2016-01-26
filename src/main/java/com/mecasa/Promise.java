package com.mecasa;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Vector;
import java.util.concurrent.*;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:39
 */
public class Promise  {
    // default executor
//    private static ExecutorService sExecutor = new ThreadPoolExecutor(1, 10, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
    private final Vector<Future<?>> _futures = new Vector<Future<?>>();
    private final Vector<Object> _values = new Vector<Object>();
    private final Vector<Deferrable<?>> _deferrables = new Vector<Deferrable<?>>();
    private Object[] _params = null;
    private Throwable _rejected;
    private ExecutorService _executor;
    private static ExecutorServiceProvider sExecutorServiceProvider = new ExecutorServiceProvider() {
        public ExecutorService getExecutorService() {
            return Executors.newFixedThreadPool(2);
        }
    };
    private int _numRetries;
    private int _retryDelay;

    public Promise retries(int numRetries) {
        _numRetries = numRetries;
        return this;
    }

    public Promise retriesWithDelay(int numRetries, int millisecs) {
        _numRetries = numRetries;
        _retryDelay = millisecs;
        return this;
    }

    public interface ExecutorServiceProvider {
        ExecutorService getExecutorService();
    }

    public static void setExecutorServiceProvider(@NotNull ExecutorServiceProvider provider) {
        sExecutorServiceProvider = provider;
    }

    public static Promise when(Deferrable<String> deferrable) {
        return new Promise(deferrable);
    }

    public static Promise all(Deferrable<?>... deferrableList) {
        return new Promise(deferrableList, null);
    }

    public Promise() {
    }

    private Promise(Throwable rejected) {
        this();
        _rejected = rejected;
    }

    public <T> Promise(final Deferrable<T> deferrable) {
        this();

        _params = null;
        _deferrables.add(deferrable);
    }


    public Promise(@NotNull Deferrable<?>[] deferrableList, @Nullable final Vector _values) {
        this();

        _params = _values != null ? _values.toArray(new Object[_values.size()]) : null;
        for (Deferrable<?> deferrable : deferrableList) {
            _deferrables.add(deferrable);
        }
    }



    public void submitAndWaitForResults()  {
        if (_executor == null) {
            _executor = sExecutorServiceProvider.getExecutorService();
        }
        final Object[] params = _params;

        for(;;) {
            for (final Deferrable deferrable : _deferrables) {
                _futures.add(_executor.submit(new Callable<Object>() {
                    public Object call() throws Exception {
                        return deferrable.call(params);
                    }
                }));
            }

            for (Future future : _futures) {
                try {
                    _values.add(future.get());
                } catch (InterruptedException e) {
                    _rejected = _rejected == null ? e : _rejected;
                    break;
                } catch (ExecutionException e) {
                    _rejected = _rejected == null ? e.getCause() : _rejected;
                    break;
                }
            }

            if (_numRetries == 0) {
                break;
            }

            if (_retryDelay > 0) {
                try {
                    Thread.sleep(_retryDelay);
                } catch (InterruptedException e) {
                }
            }

            // retry everything.
            _rejected = null;
            --_numRetries;
            _values.clear();
            _futures.clear();
        }

        // remove all pending futures and deferrables
        _deferrables.clear();
        _params = null;
        _futures.clear();
    }


    public Promise then(Deferrable<?>... deferrableList) {
        submitAndWaitForResults();
        // forward any rejected error to the next promise
        if (_rejected != null) {
            return new Promise(_rejected).setExecutor(_executor);
        }
        return new Promise(deferrableList, _values).setExecutor(_executor);
    }


    public Promise resolve(Result<Object[]> result) {
        submitAndWaitForResults();
        if (_rejected == null ) {
            result.accept(_values.toArray(new Object[_values.size()]));
        }
        return this;
    }

    public Promise reject(Result<Throwable> e) {
        submitAndWaitForResults();
        if (_rejected != null) {
            e.accept(_rejected);
        }

        return this;
    }

    public Promise setExecutor(ExecutorService executorService) {
        _executor = executorService;
        return this;
    }
}
