package com.mecasa;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.Executors;

import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * User: peter
 * Date: 18.01.2016
 * Time: 20:52
 */
public class PromiseTest {

    @Before
    public void setUp() throws Exception {
        Promise.setExecutor(Executors.newFixedThreadPool(4));
    }

    @After
    public void tearDown() throws Exception {

    }




    public static class TestDefererrable implements Deferrable<String> {

        public Object[] params;

        @Override
        public String call(Object... params) {
            this.params = params;
            return null;
        }
    }

    @Test
    public void testCallParameters() throws Exception {
        Deferrable<String> deferrable = params -> "Hello";


        final TestDefererrable deferrable1 = new TestDefererrable() {
            @Override
            public String call(Object... params) {
                super.call(params);
                return "World";
            }
        };

        Promise.when(deferrable)
                .then(deferrable1)
                .waitForAll();

        assertEquals(1, deferrable1.params.length);
        assertEquals("Hello", deferrable1.params[0]);

    }


    @Test
    public void testPromiseCompletion() throws Exception {
        Deferrable<String> deferrable = params -> {
            Thread.sleep(100);
            return "Hello";
        };
        long ct1 = System.currentTimeMillis();
        Promise.when(deferrable).waitForAll();
        long ct2 = System.currentTimeMillis();
        assertTrue(ct2>=ct1+100);
    }


    @Test
    public void testPromiseMultiCompletion() throws Exception {
        long ct1 = System.currentTimeMillis();

        final TestDefererrable resultDeferrable = new TestDefererrable() {
            @Override
            public String call(Object... params) {
                super.call(params);
                return "World";
            }
        };
        Promise.all(params -> {
            Thread.sleep(100);
            return "Hello";
        }, params -> {
            Thread.sleep(200);
            return "World";
        }).then(resultDeferrable)
          .waitForAll();

        long ct2 = System.currentTimeMillis();
        long diff = ct2 - ct1;
        System.out.println("200+100="+diff);
        assertTrue(diff>=200);
        assertTrue(diff<=250);

        assertEquals(2, resultDeferrable.params.length);
        assertEquals("Hello", resultDeferrable.params[0]);
        assertEquals("World", resultDeferrable.params[1]);
    }


    @Test
    public void testPromiseCompletionChain() throws Exception {
        long ct1 = System.currentTimeMillis();

        Promise.when(params -> {
            Thread.sleep(100);
            return "Hello";
        }).then((Deferrable<String>) params -> {
            Thread.sleep(100);
            return "World";
        }).waitForAll();

        long ct2 = System.currentTimeMillis();
        assertTrue(ct2>=ct1+200);
    }


    @Test
    public void testError() throws Exception {
        Promise.when(params -> {
            throw new IllegalArgumentException("Error");
        }).then((Deferrable<String>) params -> {fail();return null;})
          .resolve(objects -> {fail();})
          .reject(throwable -> System.out.println(throwable));

    }


    @Test
    public void testResolve() throws Exception {
        Promise.when(params -> "Hansi")
                .resolve((params)->{for (Object o : params) System.out.println(o);});
    }

    @Test
    public void testWebAccess() throws Exception {
        Promise.all(params -> {
            String out = new Scanner(new URL("http://www.melchart.com").openStream(), "UTF-8").useDelimiter("\\A").next();
            return out;
        }, params -> {
            String out = new Scanner(new URL("http://www.orf.at").openStream(), "UTF-8").useDelimiter("\\A").next();
            return out;
        }).resolve(objects -> {
           for (Object o : objects) {
               System.out.println(((String)o).length());
           }
            System.out.println("done");
        }).reject(throwable -> {
            System.out.println("ERROR: "+throwable.getMessage());
        });
    }
}