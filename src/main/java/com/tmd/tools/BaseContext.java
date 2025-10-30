package com.tmd.tools;

public class BaseContext {
    private static final ThreadLocal<Long>threadLocal = new ThreadLocal<>();
    public static Long get(){
        return threadLocal.get();
    }
    public static void set(Long value){
        threadLocal.set(value);
    }
    public static void remove(){
        threadLocal.remove();
    }
}
