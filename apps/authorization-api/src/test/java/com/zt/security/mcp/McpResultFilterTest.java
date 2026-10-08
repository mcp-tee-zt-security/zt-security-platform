package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class McpResultFilterTest {
    final ObjectMapper mapper = new ObjectMapper();
    McpToolConfig config(List<String> paths, List<String> literals) {
        return new McpToolConfig("server", "tool", Set.of("caller"), mapper.createObjectNode(), paths, literals, false);
    }
    @Test void redactionDoesNotRescanInsertedMarkers() throws Exception {
        var result = McpResultFilter.filter(mapper.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"A secret A\"}]}"),
            config(List.of(), List.of("A", "A")), mapper, 1024);
        assertEquals("[REDACTED] secret [REDACTED]", result.at("/content/0/text").textValue());
    }
    @Test void removingTextOrContentLeavesAValidEmptyResult() throws Exception {
        var result = McpResultFilter.filter(mapper.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"private\"}]}"),
            config(List.of("/content/0/text"), List.of()), mapper, 1024);
        assertTrue(result.get("content").isArray());
        assertEquals(0, result.get("content").size());
    }
    @Test void unsupportedEmbeddedResourcesAreNotReleased() throws Exception {
        var value = mapper.readTree("{\"content\":[{\"type\":\"resource\",\"resource\":{\"uri\":\"file:///secret\",\"text\":\"private\"}}]}");
        assertThrows(IllegalArgumentException.class, () -> McpResultFilter.filter(value, config(List.of(), List.of()), mapper, 1024));
    }
}
