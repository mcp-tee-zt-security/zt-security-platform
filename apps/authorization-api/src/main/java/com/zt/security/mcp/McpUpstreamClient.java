package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** One isolated upstream session per invocation. No automatic retries of side-effecting calls. */
@Component
public class McpUpstreamClient {
    public static class Failure extends RuntimeException {
        private final String code;
        private final boolean executionPossible;
        Failure(String code, boolean executionPossible) { super(code); this.code = code; this.executionPossible = executionPossible; }
        public String code() { return code; }
        public boolean executionPossible() { return executionPossible; }
    }
    private final McpGatewayProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Semaphore slots;
    private static final Set<String> PROTOCOLS = Set.of("2025-11-25", "2025-06-18");

    McpUpstreamClient(McpGatewayProperties properties, ObjectMapper mapper) {
        this.properties = properties; this.mapper = mapper;
        slots = new Semaphore(properties.getMaxConcurrentCalls());
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds())).build();
    }
    public JsonNode call(McpToolConfig config, JsonNode arguments) {
        return call(config, arguments, properties.server(config.serverId()));
    }
    public JsonNode call(McpToolConfig config, JsonNode arguments, McpGatewayProperties.Server server) {
        return request(server, "tools/call", mapper.valueToTree(Map.of("name", config.upstreamTool(), "arguments", arguments)));
    }
    public JsonNode discover(McpGatewayProperties.Server server) {
        return request(server, "tools/list", mapper.createObjectNode());
    }
    private JsonNode request(McpGatewayProperties.Server server, String method, JsonNode params) {
        if (!slots.tryAcquire()) throw new Failure("UPSTREAM_CAPACITY_EXHAUSTED", false);
        String session = null, version = "2025-11-25";
        boolean executionPossible = false;
        try {
            String id = UUID.randomUUID().toString();
            var initialization = rpc(id, "initialize", mapper.valueToTree(Map.of(
                "protocolVersion", version, "capabilities", Map.of(),
                "clientInfo", Map.of("name", "zt-mcp-security-gateway", "version", "1.0.0"))));
            HttpResponse<byte[]> initialized = exchange(server, initialization, null, null, "POST");
            session = initialized.headers().firstValue("MCP-Session-Id").orElse(null);
            if (session != null && (session.isEmpty() || session.length() > 512 || session.chars().anyMatch(c -> c < 0x21 || c > 0x7e))) {
                session = null;
                throw new Failure("UPSTREAM_SESSION_INVALID", false);
            }
            JsonNode result = response(initialized, id);
            if (result == null || !result.isObject()) throw new Failure("UPSTREAM_INITIALIZATION_INVALID", false);
            version = result.path("protocolVersion").asText("");
            if (!PROTOCOLS.contains(version) || !result.path("capabilities").has("tools")) {
                throw new Failure("UPSTREAM_PROTOCOL_UNSUPPORTED", false);
            }
            JsonNode notification = mapper.valueToTree(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
            HttpResponse<byte[]> acknowledged = exchange(server, notification, session, version, "POST");
            if (acknowledged.statusCode() != 202 || acknowledged.body().length != 0) {
                throw new Failure("UPSTREAM_INITIALIZATION_NOT_ACKNOWLEDGED", false);
            }
            String callId = UUID.randomUUID().toString();
            executionPossible = "tools/call".equals(method);
            // Arguments are exactly those evaluated/stored by the gateway. No caller headers/_meta escape this boundary.
            return response(exchange(server, rpc(callId, method, params), session, version, "POST"), callId);
        } catch (Failure error) {
            throw new Failure(error.code(), executionPossible || error.executionPossible());
        } catch (RuntimeException error) {
            throw new Failure("UPSTREAM_TRANSPORT_ERROR", executionPossible);
        } finally {
            if (session != null) {
                try { exchange(server, null, session, version, "DELETE"); }
                catch (RuntimeException ignored) { /* Cleanup failure never causes a second tool call. */ }
            }
            slots.release();
        }
    }
    private JsonNode rpc(String id, String method, JsonNode params) {
        return mapper.valueToTree(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
    }
    private HttpResponse<byte[]> exchange(McpGatewayProperties.Server server, JsonNode body,
            String session, String version, String method) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(server.getEndpoint())
            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .header("Accept", "application/json, text/event-stream");
        if (body != null) builder.header("Content-Type", "application/json");
        if (session != null) builder.header("MCP-Session-Id", session);
        if (version != null) builder.header("MCP-Protocol-Version", version);
        if (server.getBearerTokenEnv() != null) {
            String credential = System.getenv(server.getBearerTokenEnv());
            if (credential == null || credential.isBlank() || credential.contains("\r") || credential.contains("\n")) {
                throw new Failure("UPSTREAM_CREDENTIAL_UNAVAILABLE", false);
            }
            builder.header("Authorization", "Bearer " + credential);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
        BoundedBody subscriber = new BoundedBody(properties.getMaxResponseBytes());
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(builder.build(), info -> subscriber);
        try {
            return future.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure("UPSTREAM_INTERRUPTED", false);
        } catch (TimeoutException ex) {
            throw new Failure("UPSTREAM_TIMEOUT", false);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof Failure failure) throw failure;
            throw new Failure("UPSTREAM_TRANSPORT_ERROR", false);
        } finally {
            if (!future.isDone()) { subscriber.cancel(); future.cancel(true); }
        }
    }
    private JsonNode response(HttpResponse<byte[]> response, String id) {
        if (response.statusCode() != 200) throw new Failure("UPSTREAM_HTTP_ERROR", false);
        String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
        try {
            JsonNode message;
            if ("application/json".equalsIgnoreCase(type)) {
                message = McpJson.parse(response.body(), mapper);
            } else if ("text/event-stream".equalsIgnoreCase(type)) {
                message = sse(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(response.body())).toString(), id);
            } else {
                throw new Failure("UPSTREAM_CONTENT_TYPE_UNSUPPORTED", false);
            }
            if (message == null || !message.isObject() || !"2.0".equals(message.path("jsonrpc").asText())
                || !message.path("id").isTextual() || !id.equals(message.get("id").textValue())
                || message.has("result") == message.has("error") || message.has("method")) {
                throw new Failure("UPSTREAM_RESPONSE_INVALID", false);
            }
            if (message.has("error")) throw new Failure("UPSTREAM_RPC_ERROR", false);
            return message.get("result");
        } catch (java.io.IOException ex) {
            throw new Failure("UPSTREAM_RESPONSE_INVALID", false);
        }
    }
    private JsonNode sse(String stream, String id) throws java.io.IOException {
        StringBuilder data = new StringBuilder();
        JsonNode matching = null;
        for (String line : (stream.replace("\r\n", "\n").replace('\r', '\n') + "\n\n").split("\n", -1)) {
            if (line.isEmpty()) {
                if (!data.isEmpty()) {
                    JsonNode message = McpJson.parse(data.toString().getBytes(StandardCharsets.UTF_8), mapper);
                    if (message != null && message.has("method") && message.has("id")) {
                        throw new Failure("UPSTREAM_INTERACTION_UNSUPPORTED", false);
                    }
                    if (message != null && id.equals(message.path("id").asText())) {
                        if (matching != null) throw new Failure("UPSTREAM_RESPONSE_AMBIGUOUS", false);
                        matching = message;
                    }
                    data.setLength(0);
                }
            } else if (line.startsWith("data:")) {
                String value = line.substring(5);
                if (value.startsWith(" ")) value = value.substring(1);
                data.append(value).append('\n');
            }
        }
        return matching;
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private volatile Flow.Subscription subscription;
        BoundedBody(int limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) {
            if (subscription != null) { value.cancel(); return; }
            subscription = value;
            if (body.isDone()) value.cancel(); else value.request(1);
        }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new Failure("UPSTREAM_RESPONSE_TOO_LARGE", false));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
        void cancel() {
            body.completeExceptionally(new Failure("UPSTREAM_TIMEOUT", false));
            if (subscription != null) subscription.cancel();
        }
    }
}
