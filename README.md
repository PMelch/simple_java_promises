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
        
