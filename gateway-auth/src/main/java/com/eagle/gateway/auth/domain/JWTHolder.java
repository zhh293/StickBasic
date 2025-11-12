package com.eagle.gateway.auth.domain;

import org.springframework.stereotype.Component;

@Component
public class JWTHolder{
    private static final ThreadLocal<String> jwt = new ThreadLocal<>();
    public static void set(String jwt){
        JWTHolder.jwt.set(jwt);
    }
    public static String get(){
        return jwt.get();
    }
    public static void remove(){
        jwt.remove();
    }
}
