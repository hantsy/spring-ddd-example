package com.example.library.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring AOP equivalent of the CDI {@code @AroundInvoke} interceptor. It logs
 * entry, exit and exceptions for every method of a {@link UseCase}-annotated
 * bean.
 */
@Aspect
@Component
public class LoggedInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(LoggedInterceptor.class);

    @Around("@within(com.example.library.common.UseCase)")
    public Object logMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] params = joinPoint.getArgs();
        String methodName = joinPoint.getSignature().getName();
        logger.info("Entering method: {} with parameters: {}", methodName, params);
        try {
            Object result = joinPoint.proceed();
            logger.info("Exiting method: {} with result: {}", methodName, result);
            return result;
        } catch (Throwable e) {
            logger.error("Exception in method: {}", methodName, e);
            throw e;
        }
    }
}
