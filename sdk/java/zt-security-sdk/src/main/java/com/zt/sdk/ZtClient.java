package com.zt.sdk;

/** Backward-compatible facade for the pre-1.0 SDK name. */
public final class ZtClient extends ZtSecurityClient {
    public ZtClient(String baseUrl, String apiKey, String tenantId) {
        super(baseUrl, apiKey, tenantId);
    }

    public ZtClient(String baseUrl, String apiKey, String tenantId, String workspaceId) {
        super(baseUrl, apiKey, tenantId, workspaceId);
    }
}
