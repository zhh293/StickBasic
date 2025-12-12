package com.tmd.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RateLimit {
    int threshold() default 50;

    int windowSeconds() default 1;

    boolean byUser() default true;

    String key() default "";

    boolean tokenBucket() default false;

    int capacity() default 50;

    int refillPerSecond() default 50;
}
