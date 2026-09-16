package com.fraudengine.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class MetricsRecorder {

    private final MeterRegistry meterRegistry;

    public void recordDuration(final String metricName, final Duration duration, final String... tags) {
        Timer.builder(metricName).tags(tags).register(meterRegistry).record(duration);
    }

    // creates the counter at zero, so Prometheus counts its first increment
    public void register(final String name, final String... tags) {
        meterRegistry.counter(name, tags);
    }

    public void increment(final String name, final String... tags) {
        meterRegistry.counter(name, tags).increment();
    }
}
