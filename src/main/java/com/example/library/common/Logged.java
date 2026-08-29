package com.example.library.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation that triggers method-call logging via {@link LoggedInterceptor}.
 * It is carried by the {@link UseCase} stereotype; the aspect matches use cases
 * through their {@link UseCase} meta-annotation.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Logged {
}
