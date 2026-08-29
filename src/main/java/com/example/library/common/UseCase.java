package com.example.library.common;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an application use case. A use case is a Spring service that
 * runs in a transaction and is instrumented with the {@link Logged} logging
 * aspect. This composed annotation replaces the CDI {@code @Stereotype} from
 * the original Jakarta EE implementation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
@Transactional
@Logged
public @interface UseCase {
}
