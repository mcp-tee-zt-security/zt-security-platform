package com.zt.security.observability;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.web.servlet.WebMvcObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TracingAutoConfigurationTest {
    private final WebApplicationContextRunner context = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class,
                    MicrometerTracingAutoConfiguration.class,
                    ObservationAutoConfiguration.class,
                    WebMvcObservationAutoConfiguration.class))
            .withPropertyValues("management.tracing.enabled=true");

    @Test
    void tracingAndServletObservationInitializeWithoutCustomTelemetryBean() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).hasSingleBean(OpenTelemetry.class);
            assertThat(application).hasSingleBean(Tracer.class);
            assertThat(application).hasSingleBean(ObservationRegistry.class);
            assertThat(application).hasBean("webMvcObservationFilter");
        });
    }

    @Test
    void missingSdkReproducesTheReportedStartupFailure() {
        context.withClassLoader(new FilteredClassLoader("io.opentelemetry.sdk.OpenTelemetrySdk"))
                .withBean(io.opentelemetry.sdk.resources.Resource.class,
                        io.opentelemetry.sdk.resources.Resource::getDefault)
                .run(application -> {
                    assertThat(application).hasFailed();
                    assertThat(application.getStartupFailure()).hasStackTraceContaining(
                            "No qualifying bean of type 'io.opentelemetry.api.OpenTelemetry'");
                });
    }
}
