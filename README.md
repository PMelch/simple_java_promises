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
