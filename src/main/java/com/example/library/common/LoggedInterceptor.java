package com.example.library.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring AOP equivalent of the CDI {@code @AroundInvoke} interceptor. It logs
 * entry, exit and exceptions for every method of a {@link UseCase}-annotated
 * bean.
 */
@Aspect
@Component
public class LoggedInterceptor {
    private static final Logger logger = Logger.getLogger(LoggedInterceptor.class.getName());

    @Around("@within(com.example.library.common.UseCase)")
    public Object logMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] params = joinPoint.getArgs();
        String methodName = joinPoint.getSignature().getName();
        logger.log(Level.INFO, "Entering method: {0} with parameters: {1}", new Object[]{methodName, params});
        try {
            Object result = joinPoint.proceed();
            logger.log(Level.INFO, "Exiting method: {0} with result: {1}", new Object[]{methodName, result});
            return result;
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Exception in method: {0}", new Object[]{e});
            throw e;
        }
    }
}
