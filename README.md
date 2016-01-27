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