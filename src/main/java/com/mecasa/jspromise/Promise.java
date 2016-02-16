package com.mecasa.jspromise;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
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
    private boolean _fulfilled = false;
    private boolean _passValues = false;

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
        if (_fulfilled) {
            return;
        }

        if (_executor == null) {
            _executor = sExecutorServiceProvider.getExecutorService();
        }
        final Object[] params = _params;

        int numDeferrables = _deferrables.size();
        _futures.setSize(numDeferrables);
        int valuesStartIndex = 0;
        if (_passValues && params != null) {
            final int numParams = params.length;
            _values.addAll(Arrays.asList(params));
            _values.setSize(numDeferrables + numParams);
            valuesStartIndex = numParams;
        } else {
            _values.setSize(numDeferrables);
        }

        int[] retries = new int[numDeferrables];
        int[] retryDelays = new int[numDeferrables];
        boolean[] resultsRetrieved = new boolean[numDeferrables];

        for (int t = 0; t< numDeferrables; t++) {
            final Deferrable deferrable = _deferrables.get(t);

            // fetch num retries ( either from the deferrable or from the promise )
            int numTries = deferrable.getRetries();
            if (numTries < 0) numTries = _numRetries;
            retries[t] = numTries;

            // fetch retry delay ( either from the deferrable or from the promise )
            int retryDelay = deferrable.getRetryDelay();
            if (retryDelay < 0) retryDelay = _retryDelay;
            retryDelays[t] = retryDelay;
        }

        for (;;) {
            boolean deferrableSubmitted = false;

            for (int t = 0; t< numDeferrables; t++) {

                // if there is a valid result for this deferrable continue with the next.
                if (resultsRetrieved[t]) {
                    continue;
                }

                final Deferrable deferrable = _deferrables.get(t);

                deferrableSubmitted = true;

                Future<Object> future = _executor.submit(new Callable<Object>() {
                    public Object call() throws Exception {
                        return deferrable.call(params);
                    }
                });

                _futures.set(t, future);
            }

            if (!deferrableSubmitted) {
                _fulfilled = true;
                // we're done here...
                return;
            }

            for (int t = 0; t< numDeferrables; t++) {

                final Future future = _futures.get(t);
                // if there is no future at this slot
                // ( ie there was nothing submitted cause there was a valid result already)
                // skip this one.
                if (future == null) {
                    continue;
                }

                final Deferrable deferrable = _deferrables.get(t);

                int timeout = deferrable.getTimeout();
                if (timeout < 0) timeout = _timeout;

                Throwable rejected = null;
                try {
                    Object value = timeout > 0 ?
                            future.get(timeout, TimeUnit.MILLISECONDS) :
                            future.get();
                    _values.set(t + valuesStartIndex, value);
                    resultsRetrieved[t] = true;
                } catch (InterruptedException e) {
                    rejected = e;
                } catch (ExecutionException e) {
                    rejected = e.getCause();
                } catch (TimeoutException e) {
                    rejected = e;
                }

                // so delete the current future.
                _futures.set(t, null);

                if (rejected != null) {
                    if (retries[t] == 0) {
                        if (_rejected == null) {
                            _rejected = rejected;
                            _fulfilled = true;
                        }
                        // max num retries reached. break out of the whole promise stage.
                        return;
                    } else {
                        --retries[t];
                        if (retryDelays[t]>0) {
                            try {
                                Thread.sleep(retryDelays[t]);
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                } else {
                    // valid result
                }
            }
        }
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
            return configureNewPromise(new Promise(_rejected));
        }
        return configureNewPromise(new Promise(deferrableList, _values));
    }

    /**
     * pass on all relevant properties from this promise to the next.
     * @param promise
     * @return
     */
    private Promise configureNewPromise(Promise promise) {
        promise.setExecutor(_executor);
        promise.passResultsThrough(_passValues);
        return promise;
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

    /**
     * By default each stage of execution gets passed the return value(s) of the previous stage. By calling
     * passResultsThrough(true), each following stage gets called with a list of all results of all previous stages.
     * <p>
     *     So in this scenario
     * <pre>
     *     Promise.when(calcValue1).passResultsThrough(true)
     *            .then(calcValue2)
     *            .then(calcValue3)
     *            .resolve(resultCallback);
     * </pre>
     *  calcValue2 will be called with the result generated from calcValue1, calcValue3 will be called with both the results
     *  of calcValue1 and calcValue2 and the resultCallback will be called with an Object[] list containing the results
     *  of calcValue1, calcValue2 and calcValue3.
     *
     *  if the call to passResultsThrough was omitted the resultCallback would have been called with the result of calcValue3 alone.
     * </p>
     *
     *
     * @param passThrough
     * @return
     */
    public Promise passResultsThrough(boolean passThrough) {
        _passValues = passThrough;
        return this;
    }


}
