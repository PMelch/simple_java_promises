package com.mecasa.jspromise;


import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: peter
 * Date: 17.01.2016
 * Time: 15:39
 */
public class Promise {
    private Call[] _tasks;
    private Queue<Call[]> _queue = new LinkedList<Call[]>();
    private int _stageComplete;
    private final Object _completionSyncObject = new Object();

    private Result<Throwable> _rejectedHandler;
    private Result<Object[]> _resolvedHandler;
    private Runnable _fulfilledRunnable;
    private Throwable _rejectedReason;


    private final Vector<Object> _values = new Vector<Object>();
    private Executor _executor;
    private static ExecutorProvider sExecutorProvider = new ExecutorProvider() {
        public ExecutorService getExecutor() {
            return Executors.newFixedThreadPool(2);
        }
    };

    private final AtomicBoolean _fulfilled = new AtomicBoolean(false);
    private boolean _rejected = false;
    private boolean _resolved = false;
    private boolean _started = false;


    private Promise(Call... tasks) {
        _tasks = tasks;
    }


    /**
     * Schedules a new set of {@link Call}s after the current set have completed their tasks.
     * Each {@link Call#call(Object...)} will get the results of the previous task as parameters.
     * The order of the parameters correlates with the order of the Calls when being passed
     * to {@link #when(Call[])} or preceding calls to <i>then</i>.
     *
     * @param calls a list of {@link Call} instances to trigger.
     * @return the Promise
     */
    public Promise then(Call... calls) {
        _queue.add(calls);
        synchronized (_fulfilled) {
            if (_fulfilled.get()) {
                // we have already a fulfilled stage.

                // if we are already rejected, don't start the next stage
                if (!_rejected) {
                    // reset the state
                    _fulfilled.set(_resolved = false);
                    nextStage();
                }
            }
        }
        return this;
    }

    public static Promise when(Call... tasks) {
        return when(true, tasks);
    }

    public static Promise when(boolean startImmediately, Call... tasks) {
        if (tasks == null || tasks.length == 0) {
            throw new IllegalArgumentException("empty task list not allowed");
        }
        final Promise promise = new Promise(tasks);
        if (startImmediately) {
            promise.start();
        }
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
     * @param rejectedReason the reason why a task failed.
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
        Object[] params = _values.toArray(new Object[_values.size()]);
        _values.setSize(_tasks.length);
        for (final Call task : _tasks) {
            task.setPromise(this);
            task.prepare();
            task.triggerCall(params);
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
            _fulfilled.set(true);
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

    public Promise reject(Result<Throwable> rejectedHandler) {
        _rejectedHandler = rejectedHandler;
        if (_rejected) {
            _rejectedHandler.accept(_rejectedReason);
        }
        return this;
    }

    public Promise start() {
        _started = true;
        runStage();
        return this;
    }

    public Promise fulfilled(Runnable fulfilledRunnable) {
        synchronized (_fulfilled) {
            _fulfilledRunnable = fulfilledRunnable;

            if (_fulfilled.get()) {
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
     * @param provider the {@link ExecutorProvider} used to fetch the default {@link Executor}
     */
    public static void setExecutorProvider(@NotNull ExecutorProvider provider) {
        sExecutorProvider = provider;
    }



    /**
     * Triggers any remaining tasks and wait for their completion.
     * This terminates the Promise chain.
     */
    public void waitForCompletion() {
        if (!_started) {
            throw new IllegalStateException("Promise not started yet. Call start() before waitForCompletion().");
        }

        synchronized (_fulfilled) {
            if (_fulfilled.get()) {
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
                if (_fulfilled.get()) {
                    return;
                }
            }
        }
    }


    /**
     * Specifies the {@link Executor} for this  Promise. This will override any result delivered by the set ExecutorProvider.
     *
     * @param executor the {@link Executor} used for this Promise.
     * @return the chained Promise
     */
    public Promise setExecutor(Executor executor) {
        _executor = executor;
        return this;
    }
}
