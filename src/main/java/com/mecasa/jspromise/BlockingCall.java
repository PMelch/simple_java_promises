package com.mecasa.jspromise;

/**
 * Created by pmelc on 19/02/16.
 */
public abstract class BlockingCall<T> extends Call<T, AsyncCall<T>> {
    @Override
    final protected void triggerCall(final Object... params) {
        super.triggerCall(params);

        getPromise().getExecutor().execute(new Runnable(){
            @Override
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
     * @param runnable
     * @return
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

    protected abstract void call(Object... params) throws Throwable;
}
