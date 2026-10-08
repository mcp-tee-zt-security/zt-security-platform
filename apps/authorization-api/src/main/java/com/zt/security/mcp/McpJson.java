package com.zt.security.mcp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class McpJson {
    private McpJson() {}
    static JsonNode read(HttpServletRequest request, ObjectMapper mapper, int limit) {
        if (request.getContentType() == null || !request.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "application/json required");
        }
        if (request.getContentLengthLong() > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "MCP request too large");
        try {
            byte[] bytes = request.getInputStream().readNBytes(limit + 1);
            if (bytes.length > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "MCP request too large");
            return parse(bytes, mapper);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid MCP JSON body");
        }
    }
    static JsonNode parse(byte[] bytes, ObjectMapper mapper) throws IOException {
        return mapper.readerFor(JsonNode.class).with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
    }
    static String text(JsonNode value, String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isTextual() || node.textValue().isBlank() || node.textValue().length() > 512) {
            throw new IllegalArgumentException("MCP " + name + " must be a nonempty string");
        }
        return node.textValue();
    }
    static String hash(JsonNode value) { return digest(canonical(value)); }
    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private static String canonical(JsonNode value) {
        if (value.isNumber()) {
            var number = value.decimalValue().stripTrailingZeros();
            if (number.precision() > 1024 || Math.abs((long) number.scale()) > 1024)
                throw new IllegalArgumentException("MCP numeric value exceeds supported precision/range");
            return number.toPlainString();
        }
        if (value.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            value.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            List<String> fields = new ArrayList<>();
            sorted.forEach((key, node) -> fields.add(new com.fasterxml.jackson.databind.node.TextNode(key).toString() + ":" + canonical(node)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value.isArray()) {
            List<String> items = new ArrayList<>();
            value.forEach(node -> items.add(canonical(node)));
            return "[" + String.join(",", items) + "]";
        }
        return value.toString();
    }
}
