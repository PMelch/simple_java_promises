package com.mecasa.jspromise;


import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.*;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:39
 */
public class Promise {
    private Call[] _tasks;
    private Queue<Call[]> _queue = new LinkedList<Call[]>();
    private int _stageComplete;
    private Object _completionSyncObject = new Object();

    private Result _rejectedHandler;
    private Result _resolvedHandler;
    private Runnable _fulfilledRunnable;
    private Throwable _rejectedReason;


    private final Vector<Object> _values = new Vector<Object>();
    private Object[] _params = null;
    private Executor _executor;
    private static ExecutorProvider sExecutorProvider = new ExecutorProvider() {
        public ExecutorService getExecutor() {
            return Executors.newFixedThreadPool(2);
        }
    };

    private Boolean _fulfilled = false;
    private boolean _passValues = false;
    private boolean _rejected;
    private boolean _resolved;


    private Promise(Call... tasks) {
        _tasks = tasks;
    }


    /**
     * Schedules a new set of {@link Call}s after the current set have completed their tasks.
     * Each {@link Call#call(Object...)} will get the results of the previous task as parameters.
     * The order of the parameters correlates with the order of the Deferrables when being passed
     * to {@link #when(Call[])} or preceding calls to {@link #then(Call[])}.
     *
     * @param tasks
     * @return the chained Promise
     */
    public Promise then(Call... tasks) {
        _queue.add(tasks);
        synchronized (_fulfilled) {
            if (_fulfilled) {
                // we have already a fulfilled stage.

                // if we are already rejected, don't start the next stage
                if (!_rejected) {
                    // reset the state
                    _fulfilled = _resolved = false;
                    nextStage();
                }
            }
        }
        return this;
    }

    public static Promise when(Call... tasks) {
        return when(null, tasks);
    }

    public static Promise when(Executor executor, Call... tasks) {
        if (tasks == null || tasks.length == 0) {
            throw new IllegalArgumentException("empty task list not allowed");
        }
        final Promise promise = new Promise(tasks);
        promise.setExecutor(executor);
        promise.start();
        return promise;
    }


    synchronized
    protected void setResolved(Call call) {
        ++_stageComplete;

        int callIndex = Arrays.asList(_tasks).indexOf(call);
        if (callIndex >= 0) {
            _values.set(callIndex, call.getResolvedValue());
        }

        if (!_rejected && _stageComplete == _tasks.length) {
            nextStage();
        }
    }

    /**
     * @param rejectedReason
     */
    synchronized
    protected void setRejected(Throwable rejectedReason) {
        if (_rejected) {
            // already rejected, ignore the subsequent rejects
            return;
        }

        _rejected = true;
        _rejectedReason = rejectedReason;

        if (_rejectedHandler != null) {
            _rejectedHandler.accept(_rejectedReason);
        }

        setFulfilled();
    }

    private void runStage() {
        _params = _values.toArray(new Object[_values.size()]);
        _values.setSize(_tasks.length);
        for (final Call task : _tasks) {
            task.setPromise(this);
            task.prepare();
            task.triggerCall(_params);
        }
    }

    private void nextStage() {
        _tasks = _queue.poll();
        _stageComplete = 0;
        if (_tasks == null) {
            _resolved = true;
            if (_resolvedHandler != null) {
                _resolvedHandler.accept(_values.toArray(new Object[_values.size()]));
            }
            setFulfilled();
        } else {
            runStage();
        }
    }

    private void setFulfilled() {
        synchronized (_fulfilled) {
            _fulfilled = true;
        }

        if (_fulfilledRunnable != null) {
            _fulfilledRunnable.run();
        }

        synchronized (_completionSyncObject) {
            _completionSyncObject.notifyAll();
        }
    }

    public Promise resolve(Result<Object[]> resultHandler) {
        _resolvedHandler = resultHandler;
        if (_resolved) {
            _resolvedHandler.accept(_values.toArray(new Object[_values.size()]));
        }
        return this;
    }

    public Promise reject(Result<Throwable> resulHandler) {
        _rejectedHandler = resulHandler;
        if (_rejected) {
            _rejectedHandler.accept(_rejectedReason);
        }
        return this;
    }

    public Promise start() {
        runStage();
        return this;
    }

    public Promise fulfilled(Runnable fulfilledRunnable) {
        synchronized (_fulfilled) {
            _fulfilledRunnable = fulfilledRunnable;

            if (_fulfilled) {
                _fulfilledRunnable.run();
            }
        }
        return this;
    }

    public Executor getExecutor() {
        if (_executor != null) {
            return _executor;
        }
        return sExecutorProvider.getExecutor();
    }


    public interface ExecutorProvider {
        Executor getExecutor();
    }

    /**
     * Set the {@link ExecutorProvider} used to retrieve the {@link Executor} to use in case
     * no specific ExecutorService is set with {@link #setExecutor(Executor)}.
     *
     * @param provider
     */
    public static void setExecutorProvider(@NotNull ExecutorProvider provider) {
        sExecutorProvider = provider;
    }


    private Promise() {
    }


    /**
     * Triggers any remaining tasks and wait for their compleation.
     * This terminates the Promise chain.
     */
    public void waitForCompletion() {
        synchronized (_fulfilled) {
            if (_fulfilled) {
                return;
            }
        }
        while (true) {
            synchronized (_completionSyncObject) {
                try {
                    _completionSyncObject.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            synchronized (_fulfilled) {
                if (_fulfilled) {
                    return;
                }
            }
        }
    }


    /**
     * Specifies the {@link Executor} for this and all chained Promise.
     *
     * @param executor
     * @return the chained Promise
     */
    public Promise setExecutor(Executor executor) {
        _executor = executor;
        return this;
    }


    /**
     * By default each stage of execution gets passed the return value(s) of the previous stage. By calling
     * passResultsThrough(true), each following stage gets called with a list of all results of all previous stages.
     * <p>
     * So in this scenario
     * <pre>
     *     Promise.when(calcValue1).passResultsThrough(true)
     *            .then(calcValue2)
     *            .then(calcValue3)
     *            .resolve(resultCallback);
     * </pre>
     * calcValue2 will be called with the result generated from calcValue1, calcValue3 will be called with both the results
     * of calcValue1 and calcValue2 and the resultCallback will be called with an Object[] list containing the results
     * of calcValue1, calcValue2 and calcValue3.
     * <p/>
     * if the call to passResultsThrough was omitted the resultCallback would have been called with the result of calcValue3 alone.
     * </p>
     *
     * @param passThrough
     * @return
     */
    public Promise passResultsThrough(boolean passThrough) {
        _passValues = passThrough;
        return this;
    }


}
