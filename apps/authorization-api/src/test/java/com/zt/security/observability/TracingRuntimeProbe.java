package com.zt.security.observability;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.web.servlet.WebMvcObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** Run against only BOOT-INF/lib jars to verify the packaged runtime classpath. */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({OpenTelemetryAutoConfiguration.class,
        OpenTelemetryTracingAutoConfiguration.class, MicrometerTracingAutoConfiguration.class,
        ObservationAutoConfiguration.class, WebMvcObservationAutoConfiguration.class,
        ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class})
public class TracingRuntimeProbe {
    public static void main(String[] args) {
        try (var context = SpringApplication.run(TracingRuntimeProbe.class,
                "--server.port=0", "--management.tracing.enabled=true")) {
            context.getBean(OpenTelemetry.class);
            context.getBean(Tracer.class);
            context.getBean(ObservationRegistry.class);
            context.getBean("webMvcObservationFilter");
            System.out.println("PACKAGED_TRACING_STARTUP_OK");
        }
    }
}
