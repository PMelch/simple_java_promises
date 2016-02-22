package com.mecasa.jspromise;

/**
 * Created by peter on 19/02/16.
 *
 * a BlockingCall should be used for all blocking tasks to be executed.
 * The blocking code will be passed to an {@link java.util.concurrent.Executor} to performed asynchronously.
 */
public abstract class BlockingCall<T> extends Call<T> {
    @Override
    final protected void triggerCall(final Object... params) {
        super.triggerCall(params);

        getPromise().getExecutor().execute(new Runnable(){
            public void run() {
                try {
                    call(params);
                } catch (Throwable e) {
                    reject(e);
                }
            }
        });
    }


    /**
     * wraps a blocking runnable in a AsyncCall which will always be resolved without a parameter once
     * the {@link Runnable#run()} has completed.
     * The set ExecutorService is used to create the async version of the runnable.
     *
     * @param runnable the Runnable to wrap in a BlockingCall.
     * @return the created BlockingCall
     */

    public static <Void> BlockingCall<Void> wrap(final Runnable runnable) {
        return new BlockingCall<Void>() {
            @Override
            protected void call(Object... params) throws Throwable {
                runnable.run();
                resolve(null);
            }
        };
    }
}
