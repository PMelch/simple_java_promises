package com.mecasa;

public class Main {

    interface Test {
        default void func(Object... params) {
            DeferrableReflectionHelper.callMethod(this, "func", null, params);
        }
    }

    static class A implements Test {
        void func(String a, String b) {
            System.out.println(a+" "+b);
        }
    }


    public static void main(String[] args) {
	// write your code here
        A a = new A();
        a.func(new Object[]{"Hello", "World"});
    }
}
