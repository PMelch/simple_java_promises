#SJPromise
the Simple Java Promise library for easily chaining async operations.
 
Lets you do things like

```Java

    Promise.when(new Deferrable<String>(){...}, new Deferrable<String>(){...})
        .then(new Deferrable<String>(){...})
        .resolve(new Result<Object[]>() {
             public void accept(Object[] objects) {
                // receive the results of the previously called Deferrables.  
                System.out.println(objects.length);
             }})
        .reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                // catch any error that occured within the execution of  
                System.out.println("ERROR: "+throwable.getMessage());
            }
        });
        

```

Each Deferrable can resolve its status by returning a value or reject it by throwing an exception. Values get passed to any chained promises as parameters.

What SJPromise is:
* a simple way to use Javascript Promise-like syntax to chain async tasks
* working with Java 1.5 and above

What SJPromise is NOT:
* A full blown replacement for the CompletableFuture from Java 8 or the [JDeferred Lib](http://jdeferred.org/)
* complying to the [Promises/A+ standard](https://promisesaplus.com/) 


## Usage


### Simple Chain


```Java

    Promise.when(deferrable)
        .then(nextDeferrable)
        .resolve(new Result<Object[]>() {
             public void accept(Object[] objects) {
             }})
        .reject(new Result<Throwable>() {
            public void accept(Throwable throwable) {
                System.out.println("ERROR: "+throwable.getMessage());
            }
        });

```

### Processing Return values / parameters

Each stage in the promise chain gets passed the returned values of the previous stage.

```Java
        Promise.when(new Deferrable<String>(){
            public String call(Object... params) throws Exception {
                // read web page content
                return loadWebPage("http://www.google.com");
            }})
        .then(new Deferrable<Integer>(){
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

When a stage has more then one Deferrable to process, all parameters are passed to each Deferrable in the next stage.
```Java
        Promise.when(new WebPageLoadingDeferrable("http://www.example.com"), 
                     new WebPageLoadingDeferrable("http://www.google.com"))
        .then(new Deferrable<Integer>(){
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
        
