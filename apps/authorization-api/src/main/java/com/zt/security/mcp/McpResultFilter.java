package com.zt.security.mcp;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

final class McpResultFilter {
    private McpResultFilter() {}
    static ObjectNode filter(JsonNode upstream, McpToolConfig config, ObjectMapper mapper, int limit) {
        if (!upstream.isObject() || !upstream.path("content").isArray()
            || upstream.has("isError") && !upstream.get("isError").isBoolean()) {
            throw new IllegalArgumentException("Invalid MCP tool result");
        }
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        // Initial execution adapter only releases text content. No embedded resources/blobs are proxied.
        for (JsonNode block : upstream.get("content")) {
            if (!block.isObject() || !"text".equals(block.path("type").asText())) {
                throw new IllegalArgumentException("Upstream returned an unsupported MCP content type");
            }
            if (!block.path("text").isTextual()) throw new IllegalArgumentException("Invalid MCP text content");
            content.addObject().put("type", "text").put("text", block.get("text").textValue());
        }
        result.put("isError", upstream.path("isError").asBoolean(false));
        if (upstream.has("structuredContent")) {
            if (!upstream.get("structuredContent").isObject()) throw new IllegalArgumentException("Invalid structuredContent");
            result.set("structuredContent", upstream.get("structuredContent").deepCopy());
        }
        for (String path : config.redactResultPaths()) remove(result, JsonPointer.compile(path));
        redact(result, config, 0, new int[] { limit });
        // Restore protocol shape without restoring any redacted data.
        JsonNode remaining = result.get("content");
        ArrayNode released = mapper.createArrayNode();
        if (remaining != null && remaining.isArray()) {
            for (JsonNode block : remaining) {
                if (block != null && block.path("text").isTextual()) {
                    released.addObject().put("type", "text").put("text", block.get("text").textValue());
                }
            }
        }
        result.set("content", released);
        result.put("isError", upstream.path("isError").asBoolean(false));
        return result;
    }
    private static void remove(ObjectNode root, JsonPointer pointer) {
        JsonNode parent = root.at(pointer.head());
        String property = pointer.last().getMatchingProperty();
        if (parent instanceof ObjectNode object) object.remove(property);
        else if (parent instanceof ArrayNode array) {
            int index = pointer.last().getMatchingIndex();
            if (index >= 0 && index < array.size()) array.set(index, NullNode.instance);
        }
    }
    private static void redact(JsonNode node, McpToolConfig config, int depth, int[] budget) {
        if (depth > 32) throw new IllegalArgumentException("MCP result is too deeply nested");
        if (node instanceof ObjectNode object) {
            object.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) object.put(entry.getKey(), text(entry.getValue().textValue(), config, budget));
                else redact(entry.getValue(), config, depth + 1, budget);
            });
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i).isTextual()) array.set(i, TextNode.valueOf(text(array.get(i).textValue(), config, budget)));
                else redact(array.get(i), config, depth + 1, budget);
            }
        }
    }
    private static String text(String value, McpToolConfig config, int[] budget) {
        StringBuilder released = new StringBuilder();
        int offset = 0;
        while (offset < value.length()) {
            int next = value.length(), matchedLength = 0;
            for (String literal : config.redactTextLiterals()) {
                int position = value.indexOf(literal, offset);
                if (position >= 0 && (position < next || position == next && literal.length() > matchedLength)) {
                    next = position; matchedLength = literal.length();
                }
            }
            int added = next - offset + (matchedLength == 0 ? 0 : 10);
            if (added > budget[0]) throw new IllegalArgumentException("Filtered MCP response exceeds size budget");
            budget[0] -= added;
            released.append(value, offset, next);
            if (matchedLength == 0) break;
            released.append("[REDACTED]");
            offset = next + matchedLength;
        }
        return released.toString();
    }
}
