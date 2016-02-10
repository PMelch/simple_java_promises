package com.mecasa;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * User: peter
 * Date: 07.02.2016
 * Time: 14:23
 */
class BlockingTestExecutor implements ExecutorService {
    public void shutdown() {

    }

    @NotNull
    public List<Runnable> shutdownNow() {
        return null;
    }

    public boolean isShutdown() {
        return false;
    }

    public boolean isTerminated() {
        return false;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @NotNull
    public <T> Future<T> submit(final Callable<T> task) {
        return new Future<T>() {
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            public boolean isCancelled() {
                return false;
            }

            public boolean isDone() {
                return false;
            }

            public T get() throws InterruptedException, ExecutionException {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new ExecutionException(e.getMessage(), e.getCause());
                }
            }

            public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return null;
            }
        };
    }

    @NotNull
    public <T> Future<T> submit(Runnable task, T result) {
        return null;
    }

    @NotNull
    public Future<?> submit(Runnable task) {
        return null;
    }

    @NotNull
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return null;
    }

    @NotNull
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return null;
    }

    @NotNull
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return null;
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
    }

    public void execute(Runnable command) {

    }
}
