#Simple Java Promises
a library for easily chaining async operations.
 
Lets you do things like

```Java

    Promise.when(asyncCall1, blockingCall)
        .then(asyncCall2.retries(3))
        .resolve(resolveCallback)
        .reject(rejectCallback);
        

```

Each Call can resolve its status by calling resolve(value) or reject it by calling reject(Throwable). Values get passed to any chained promises as parameters.

What SJPromise is:
* a simple way to use Javascript Promise-like syntax to chain async tasks
* working with Java 1.5 and above

What SJPromise is NOT:
* A full blown replacement for the CompletableFuture from Java 8 or the [JDeferred Lib](http://jdeferred.org/)
* complying to the [Promises/A+ standard](https://promisesaplus.com/) 


## Usage


### Simple Chain


```Java

    Promise.when(call1)
        .then(call1)
        .resolve(new Result<Object[]>() {
             public void accept(Object[] objects) {
             }})
        .reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                System.out.println("ERROR: "+throwable.getMessage());
            }
        });

```

A call can either be an AsyncCall which is assumed to perform it's call method in an async way already, or a BlockingCall which will be passed to the set Executor to be performed asynchronously. 


### Tasks

If you have a task that is already asynchronous - that is, running a background thread, or triggering an operation and get notified via callbacks - create a AsyncCall subclass like this:

```Java
    Call call = new AsyncCall<String>() {
            @Override
            public void call(Object... params) throws Exception {
                triggerAsyncOperationWithCallback(new Callback() {
                        void onSuccess() {
                            resolve("foo");
                        }
                        
                        void onError(String errorMessage) {
                            // wrap error message in an Exception
                            reject(new Exception(errorMessage));
                        }
                    }
                );
            }
        }

```


If you have a blocking task you can either create a subclass of BlockingCall like this:

```Java

        Call call = new BlockingCall<String>() {
            public void call(Object... params) throws Exception {
                try {
                    String resulg = performBlockingOperation();
                } catch (Exception e) {
                    reject(e);
                }
                resolve(result);
            }
        }

```

or if you just have a Runnable you want to perform asynchronously, you can use the BlockingCall.wrap(Runnable) method:
```Java

        Call call = new BlockingCall.wrap(runnable);

```

However, a wrapped Runnable can just resolve without a parameter and never get rejected. 


### Processing Return values / parameters

Each stage in the promise chain gets passed the returned values of the previous stage.

```Java
        Promise.when(new BlockingCall<String>(){
            public String call(Object... params) throws Exception {
                // read web page content
                return loadWebPage("http://www.google.com");
            }})
        .then(new BlockingCall<Integer>(){
            public Integer call(Object... params) throws Exception {
                // do operatino on passed String
                return countSomething((String)params[0]);
            }})
        .resolve(new Result<Object[]>() {
             public void accept(Object[] objects) {
                 // print resulting length
                 System.out.println(objects[0]);
             }})
        .reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                System.out.println("ERROR: "+throwable.getMessage());
            }
        });
```

When a stage has more then one Call to process, all resolved values are passed to each Call in the next stage.
```Java
        Promise.when(new WebPageLoadingCall("http://www.example.com"), 
                     new WebPageLoadingCall("http://www.google.com"))
        .then(new BlockingCall<Integer>(){
            public Integer call(Object... params) throws Exception {
                // do operatino on passed String
                return findCommonWords((String)params[0], (String)params[1]);
            }})
        .resolve(new Result<Object[]>() {
             public void accept(Object[] objects) {
                 // print resulting value
                 System.out.println(objects[0]);
             }})
        .reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                System.out.println("ERROR: "+throwable.getMessage());
            }
        });
```
        
