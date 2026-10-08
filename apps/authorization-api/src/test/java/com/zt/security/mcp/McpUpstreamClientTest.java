package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class McpUpstreamClientTest {
    @Test void realHttpAdapterInitializesSessionAndCorrelatesSseToolResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger calls = new AtomicInteger();
        List<String> sequence = Collections.synchronizedList(new ArrayList<>());
        List<String> violations = Collections.synchronizedList(new ArrayList<>());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            try {
                if ("DELETE".equals(exchange.getRequestMethod())) {
                    sequence.add("DELETE"); exchange.sendResponseHeaders(204, -1); return;
                }
                JsonNode rpc = mapper.readTree(exchange.getRequestBody());
                String method = rpc.path("method").asText();
                sequence.add(method);
                if (exchange.getRequestHeaders().getFirst("X-API-Key") != null ||
                    exchange.getRequestHeaders().getFirst("Authorization") != null) violations.add("caller credential leaked");
                if (!"initialize".equals(method)) {
                    if (!"session-test".equals(exchange.getRequestHeaders().getFirst("MCP-Session-Id"))) violations.add("missing session");
                    if (!"2025-11-25".equals(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"))) violations.add("missing protocol");
                }
                if ("notifications/initialized".equals(method)) { exchange.sendResponseHeaders(202, -1); return; }
                String body, contentType;
                if ("initialize".equals(method)) {
                    exchange.getResponseHeaders().set("MCP-Session-Id", "session-test");
                    body = mapper.valueToTree(Map.of("jsonrpc", "2.0", "id", rpc.get("id"),
                        "result", Map.of("protocolVersion", "2025-11-25", "capabilities", Map.of("tools", Map.of())))).toString();
                    contentType = "application/json";
                } else {
                    calls.incrementAndGet();
                    if (!"payment.transfer".equals(rpc.at("/params/name").asText()) ||
                        rpc.at("/params/arguments/amount").asInt() != 100) violations.add("wrong arguments");
                    body = "data: " + mapper.valueToTree(Map.of("jsonrpc", "2.0", "id", rpc.get("id"),
                        "result", Map.of("content", List.of(Map.of("type", "text", "text", "receipt-42"))))) + "\n\n";
                    contentType = "text/event-stream";
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            McpGatewayProperties properties = new McpGatewayProperties();
            properties.setAllowHttp(true);
            var target = new McpGatewayProperties.Server();
            target.setEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"));
            properties.setServers(Map.of("payments", target)); properties.validate();
            var config = new McpToolConfig("payments", "payment.transfer", Set.of("caller"),
                mapper.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
                List.of(), List.of(), false);
            var result = new McpUpstreamClient(properties, mapper).call(config, mapper.readTree("{\"amount\":100}"));
            assertEquals("receipt-42", result.at("/content/0/text").textValue());
            assertEquals(1, calls.get());
            assertEquals(List.of("initialize", "notifications/initialized", "tools/call", "DELETE"), sequence);
            assertTrue(violations.isEmpty(), violations.toString());
        } finally { server.stop(0); }
    }
}
