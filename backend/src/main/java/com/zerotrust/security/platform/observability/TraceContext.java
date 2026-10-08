package com.zerotrust.security.platform.observability;

public final class TraceContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String getOrCreate() {
        String traceId = TRACE_ID.get();
        return traceId == null ? java.util.UUID.randomUUID().toString() : traceId;
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
