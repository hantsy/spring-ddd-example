package com.example.library.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes a {@link Clock} bean for time-related domain logic, replacing the CDI
 * {@code @Produces} producer of the original implementation.
 */
@Configuration
public class ClockProducer {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
