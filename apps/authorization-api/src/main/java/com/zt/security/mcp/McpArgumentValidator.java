package com.zt.security.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Deliberately small JSON Schema subset. Unsupported keywords are rejected at registration. */
final class McpArgumentValidator {
    private static final Set<String> COMMON = Set.of("type", "description", "enum");
    private McpArgumentValidator() {}
    static void validateSchema(JsonNode schema) {
        schema(schema, 0);
        if (!"object".equals(schema.path("type").asText())) throw new IllegalArgumentException("Tool inputSchema must be an object schema");
    }
    private static void schema(JsonNode schema, int depth) {
        if (depth > 16 || schema == null || !schema.isObject()) throw new IllegalArgumentException("Invalid/deep tool schema");
        String type = schema.path("type").asText("");
        Set<String> allowed = new HashSet<>(COMMON);
        switch (type) {
            case "object" -> {
                allowed.addAll(Set.of("properties", "required", "additionalProperties"));
                if (!schema.path("properties").isObject() || !schema.has("additionalProperties")
                    || !schema.get("additionalProperties").isBoolean() || schema.get("additionalProperties").booleanValue()
                    || schema.get("properties").size() > 100) throw new IllegalArgumentException("Object schema requires properties and additionalProperties=false");
                schema.get("properties").elements().forEachRemaining(child -> schema(child, depth + 1));
                if (schema.has("required")) {
                    if (!schema.get("required").isArray()) throw new IllegalArgumentException("required must be an array");
                    Set<String> unique = new HashSet<>();
                    for (JsonNode name : schema.get("required")) {
                        if (!name.isTextual() || !schema.get("properties").has(name.textValue()) || !unique.add(name.textValue())) {
                            throw new IllegalArgumentException("Invalid required property");
                        }
                    }
                }
            }
            case "array" -> {
                allowed.addAll(Set.of("items", "minItems", "maxItems"));
                schema(schema.get("items"), depth + 1);
                integers(schema, "minItems", "maxItems", 1000);
            }
            case "string" -> {
                allowed.addAll(Set.of("minLength", "maxLength"));
                integers(schema, "minLength", "maxLength", 65536);
            }
            case "number", "integer" -> {
                allowed.addAll(Set.of("minimum", "maximum"));
                for (String field : List.of("minimum", "maximum")) {
                    if (schema.has(field) && !schema.get(field).isNumber()) throw new IllegalArgumentException("Numeric schema bounds required");
                }
                if (schema.has("minimum") && schema.has("maximum")
                    && schema.get("minimum").decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0) {
                    throw new IllegalArgumentException("Invalid numeric schema bounds");
                }
            }
            case "boolean" -> {}
            default -> throw new IllegalArgumentException("Unsupported tool schema type");
        }
        schema.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw new IllegalArgumentException("Unsupported tool schema keyword: " + field);
        });
        if (schema.has("enum") && (!schema.get("enum").isArray() || schema.get("enum").isEmpty() || schema.get("enum").size() > 100)) {
            throw new IllegalArgumentException("enum must contain 1..100 values");
        }
        if (schema.has("enum")) {
            if ("object".equals(type) || "array".equals(type)) throw new IllegalArgumentException("Only scalar enums are supported");
            for (JsonNode value : schema.get("enum")) {
                boolean valid = switch (type) {
                    case "string" -> value.isTextual();
                    case "number" -> value.isNumber();
                    case "integer" -> value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0;
                    case "boolean" -> value.isBoolean();
                    default -> false;
                };
                if (!valid) throw new IllegalArgumentException("Schema enum type mismatch");
            }
        }
        if (schema.has("description") && (!schema.get("description").isTextual()
            || schema.get("description").textValue().length() > 2048)) throw new IllegalArgumentException("Invalid schema description");
    }
    private static void integers(JsonNode schema, String min, String max, int cap) {
        for (String name : List.of(min, max)) {
            JsonNode value = schema.get(name);
            if (value != null && (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0 || value.intValue() > cap)) {
                throw new IllegalArgumentException("Invalid schema length/item bound");
            }
        }
        if (schema.has(min) && schema.has(max) && schema.get(min).intValue() > schema.get(max).intValue()) {
            throw new IllegalArgumentException("Invalid schema bounds");
        }
    }
    static void validate(JsonNode schema, JsonNode arguments) {
        check(schema, arguments, "$", 0);
    }
    private static void check(JsonNode schema, JsonNode value, String path, int depth) {
        if (depth > 16 || value == null || value.isNull()) fail(path);
        String type = schema.path("type").asText();
        switch (type) {
            case "object" -> {
                if (!value.isObject() || value.size() > 100) fail(path);
                for (JsonNode required : schema.path("required")) if (!value.has(required.textValue())) fail(path);
                value.fields().forEachRemaining(entry -> {
                    JsonNode child = schema.path("properties").get(entry.getKey());
                    if (child == null) fail(path);
                    check(child, entry.getValue(), path + "." + entry.getKey(), depth + 1);
                });
            }
            case "array" -> {
                if (!value.isArray() || value.size() > 1000) fail(path);
                length(schema, value.size(), "minItems", "maxItems", path);
                for (JsonNode child : value) check(schema.get("items"), child, path + "[]", depth + 1);
            }
            case "string" -> {
                if (!value.isTextual()) fail(path);
                length(schema, value.textValue().codePointCount(0, value.textValue().length()), "minLength", "maxLength", path);
            }
            case "number", "integer" -> {
                if (!value.isNumber() || type.equals("integer") && value.decimalValue().stripTrailingZeros().scale() > 0) fail(path);
                if (schema.has("minimum") && value.decimalValue().compareTo(schema.get("minimum").decimalValue()) < 0) fail(path);
                if (schema.has("maximum") && value.decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0) fail(path);
            }
            case "boolean" -> { if (!value.isBoolean()) fail(path); }
            default -> fail(path);
        }
        if (schema.has("enum")) {
            boolean match = false;
            for (JsonNode candidate : schema.get("enum")) {
                if (candidate.equals(value) || candidate.isNumber() && value.isNumber()
                    && candidate.decimalValue().compareTo(value.decimalValue()) == 0) match = true;
            }
            if (!match) fail(path);
        }
    }
    private static void length(JsonNode schema, int value, String min, String max, String path) {
        if (schema.has(min) && value < schema.get(min).intValue() || schema.has(max) && value > schema.get(max).intValue()) fail(path);
    }
    private static void fail(String path) { throw new IllegalArgumentException("Tool arguments do not satisfy the registered schema at " + path); }
}
