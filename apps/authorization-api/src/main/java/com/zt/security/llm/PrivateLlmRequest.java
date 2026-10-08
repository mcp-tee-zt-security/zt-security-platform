package com.zt.security.llm;

import java.util.Map;

public record PrivateLlmRequest(String provider,String endpoint,String model,
String secretRef,String prompt,Map<String,Object> metadata) {
}
