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
    private int _timeout;


    public interface ExecutorServiceProvider {
        ExecutorService getExecutorService();
    }

    /**
     * Set the {@link ExecutorServiceProvider} used to retrieve the {@link ExecutorService} to use in case
     * no specific ExecutorService is set with {@link #setExecutor(ExecutorService)}.
     * @param provider
     */
    public static void setExecutorServiceProvider(@NotNull ExecutorServiceProvider provider) {
        sExecutorServiceProvider = provider;
    }


    /**
     * Create a Promise object wich executes the passed list of {@link Deferrable}. Each call to {@link Deferrable#call(Object...)} will receive
     * no parameters.
     * @param deferrableList the list of Deferrables.
     * @return
     */
    public static Promise when(@NotNull Deferrable<?>... deferrableList) {
        if (deferrableList.length == 0) {
            throw new IllegalArgumentException("deferrableList must not be of zero length");
        }
        return new Promise(deferrableList, null);
    }

    private Promise() {
    }

    /**
     * Constructor used to pass on errors.
     * @param rejected
     */
    private Promise(Throwable rejected) {
        this();
        _rejected = rejected;
    }

    /**
     * @param deferrableList
     * @param _values
     */
    protected Promise(@NotNull Deferrable<?>[] deferrableList, @Nullable final Vector _values) {
        this();

        _params = _values != null ? _values.toArray(new Object[_values.size()]) : null;
        for (Deferrable<?> deferrable : deferrableList) {
            _deferrables.add(deferrable);
        }
    }

    /**
     * Triggers any remaining tasks and wait for their compleation.
     * This terminates the Promise chain.
     */
    public void waitForCompletion() {
        submitAndWaitForResults();
    }

    private void submitAndWaitForResults()  {
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

            assert(_deferrables.size() == _futures.size());

            for (int t = 0; t<_futures.size(); t++) {
                Future future = _futures.get(t);

                int timeout = _deferrables.get(t).getTimeout();
                if (timeout == 0) timeout = _timeout;

                try {
                    _values.add(timeout > 0 ?
                        future.get(timeout, TimeUnit.MILLISECONDS) :
                        future.get());
                } catch (InterruptedException e) {
                    _rejected = _rejected == null ? e : _rejected;
                    break;
                } catch (ExecutionException e) {
                    _rejected = _rejected == null ? e.getCause() : _rejected;
                    break;
                } catch (TimeoutException e) {
                    _rejected = _rejected == null ? e : _rejected;
                }
            }

            if (_numRetries == 0) {
                break;
            }

            if (_retryDelay > 0) {
                try {
                    Thread.sleep(_retryDelay);
                } catch (InterruptedException ignored) {
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


    /**
     * Schedules a new set of {@link Deferrable}s after the current set have completed their tasks.
     * Each {@link Deferrable#call(Object...)} will get the results of the previous task as parameters.
     * The order of the parameters correlates with the order of the Deferrables when being passed
     * to {@link #when(Deferrable[])} or preceding calls to {@link#then(Deferrable[])}.
     * @param deferrableList
     * @return the chained Promise
     */
    public Promise then(Deferrable<?>... deferrableList) {
        submitAndWaitForResults();
        // forward any rejected error to the next promise
        if (_rejected != null) {
            return new Promise(_rejected).setExecutor(_executor);
        }
        return new Promise(deferrableList, _values).setExecutor(_executor);
    }


    /**
     * A call to resolve will trigger a call to the passed {@link Result#accept(Object)} with the list of results ( from the
     * completed Deferrables) as parameter once the deferred tasks are complete. This happens only if no uncaught Exception
     * has occurred. Otherwise the error callback passed to {@link #reject(Result)} will be triggered.
     * @param result
     * @return the chained Promise
     */
    public Promise resolve(Result<Object[]> result) {
        submitAndWaitForResults();
        if (_rejected == null ) {
            result.accept(_values.toArray(new Object[_values.size()]));
        }
        return this;
    }


    /**
     * A call to reject will trigger a call to the passed {@link Result#accept(Object)} with the first error that
     * has occurred in the processed list of deferrables. If an error has occured the result callback passed with {@link #resolve(Result)} will
     * not be triggered.
     * @param e
     * @return the chained Promise
     */
    public Promise reject(Result<Throwable> e) {
        submitAndWaitForResults();
        if (_rejected != null) {
            e.accept(_rejected);
        }

        return this;
    }

    /**
     * Specifies that in case one of the passed tasks of the current Promise fails, all Tasks will be retries for the given amount of times.
     * @param numRetries
     * @return the chained Promise
     */
    public Promise retries(int numRetries) {
        _numRetries = numRetries;
        return this;
    }

    /**
     * Specifies that in case one of the passed tasks of the current Promise fails, all Tasks will be retries for the given amount of times.
     * Each retry is delayed for the given amount of millisecs.
     * @param numRetries
     * @param millisecs
     * @return the chained Promise
     */
    public Promise retriesWithDelay(int numRetries, int millisecs) {
        _numRetries = numRetries;
        _retryDelay = millisecs;
        return this;
    }

    /**
     * Specifies the {@link ExecutorService} for this and all chained Promise.
     * @param executorService
     * @return the chained Promise
     */
    public Promise setExecutor(ExecutorService executorService) {
        _executor = executorService;
        return this;
    }

    /**
     * Set timeout for the Deferrables. If a asnc task takes longer than the specified time, the promise is rejected
     * with a TimedOutException.
     * @param timeout
     * @return
     */
    public Promise timeout(int timeout) {
        _timeout = timeout;
        return this;
    }

}
