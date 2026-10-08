package com.zt.security.llm;

import java.util.Map;

public interface PrivateLlmProvider {
    String provider();
    Map<String,Object> health(PrivateLlmRequest request);
    Map<String,Object> chat(PrivateLlmRequest request);
}
