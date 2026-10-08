package com.zt.security.mcp;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpArgumentValidatorTest {
    final ObjectMapper mapper = new ObjectMapper();
    @Test void amountLimitAndUnknownArgumentsFailClosed() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"object","properties":{
                "amount":{"type":"integer","minimum":1,"maximum":500000},
                "destination":{"type":"string","enum":["internal"],"maxLength":16}},
             "required":["amount","destination"],"additionalProperties":false}
            """);
        McpArgumentValidator.validateSchema(schema);
        assertDoesNotThrow(() -> McpArgumentValidator.validate(schema, mapper.readTree("{\"amount\":500000,\"destination\":\"internal\"}")));
        assertThrows(IllegalArgumentException.class, () -> McpArgumentValidator.validate(schema, mapper.readTree("{\"amount\":500001,\"destination\":\"internal\"}")));
        assertThrows(IllegalArgumentException.class, () -> McpArgumentValidator.validate(schema, mapper.readTree("{\"amount\":1,\"destination\":\"external\"}")));
        assertThrows(IllegalArgumentException.class, () -> McpArgumentValidator.validate(schema, mapper.readTree("{\"amount\":1,\"destination\":\"internal\",\"agent\":\"admin\"}")));
        assertThrows(IllegalArgumentException.class, () -> McpArgumentValidator.validate(schema, mapper.readTree("{\"amount\":\"1\",\"destination\":\"internal\"}")));
    }
    @Test void unsupportedSchemaCannotSilentlyDisableConstraints() throws Exception {
        JsonNode schema = mapper.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false,\"oneOf\":[]}");
        assertThrows(IllegalArgumentException.class, () -> McpArgumentValidator.validateSchema(schema));
    }
    @Test void trailingJsonAndDuplicateKeysAreRejected() {
        assertThrows(java.io.IOException.class, () -> McpJson.parse("{\"amount\":1,\"amount\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8), mapper));
        assertThrows(java.io.IOException.class, () -> McpJson.parse("{} {}".getBytes(java.nio.charset.StandardCharsets.UTF_8), mapper));
    }
}
