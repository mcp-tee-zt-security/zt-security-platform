package com.zt.security.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public record McpToolConfig(String serverId, String upstreamTool, Set<String> allowedSubjects,
        JsonNode inputSchema, List<String> redactResultPaths, List<String> redactTextLiterals,
        boolean requireApproval) {
    public McpToolConfig {
        allowedSubjects = allowedSubjects == null ? Set.of() : Set.copyOf(allowedSubjects);
        redactResultPaths = redactResultPaths == null ? List.of() : List.copyOf(redactResultPaths);
        redactTextLiterals = redactTextLiterals == null ? List.of() : List.copyOf(redactTextLiterals);
    }
    void validate(McpGatewayProperties properties) {
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("MCP serverId is required");
        if (upstreamTool == null || !upstreamTool.matches("[A-Za-z0-9_.:-]{1,128}")
            || allowedSubjects.isEmpty() || allowedSubjects.size() > 1000
            || allowedSubjects.stream().anyMatch(s -> s.isBlank() || s.length() > 512)
            || redactResultPaths.size() > 100 || redactTextLiterals.size() > 100
            || redactTextLiterals.stream().anyMatch(s -> s.isEmpty() || s.length() > 256)) {
            throw new IllegalArgumentException("Invalid MCP tool binding");
        }
        for (String path : redactResultPaths) {
            if (path.isEmpty() || !path.startsWith("/") || path.length() > 512) {
                throw new IllegalArgumentException("Redaction paths must be nonempty JSON pointers");
            }
            for (int i = 0; i < path.length(); i++) {
                if (path.charAt(i) == '~' && (i + 1 == path.length() || "01".indexOf(path.charAt(++i)) < 0))
                    throw new IllegalArgumentException("Invalid JSON pointer escape");
            }
            com.fasterxml.jackson.core.JsonPointer.compile(path);
        }
        McpArgumentValidator.validateSchema(inputSchema);
    }
}
