package com.lava.swexpedited.batch;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Shared retry boilerplate for the GFM/Samsara/Vektor HTTP clients, all of which retry on a transient 5xx up to 4
 * attempts total with a fixed, per-integration-configurable backoff between them. {@link RetryTemplate} isn't
 * thread-safety-sensitive to reuse, so one is cached per distinct {@code retryBackoff} rather than rebuilt on every
 * call - in practice each subclass only ever calls {@link #retrying} with its own single injected backoff, so this
 * settles into exactly one cached template per bean.
 */
public abstract class RetryingHttpClient {

    private final Map<Duration, RetryTemplate> retryTemplatesByBackoff = new ConcurrentHashMap<>();

    protected <T> T retrying(Supplier<T> call, Duration retryBackoff) {
        return this.retryTemplatesByBackoff
                .computeIfAbsent(retryBackoff, this::buildRetryTemplate)
                .execute(_ -> call.get());
    }

    private RetryTemplate buildRetryTemplate(Duration retryBackoff) {
        return RetryTemplate.builder()
                .maxAttempts(4)
                .fixedBackoff(retryBackoff)
                .retryOn(HttpServerErrorException.class)
                .build();
    }
}
