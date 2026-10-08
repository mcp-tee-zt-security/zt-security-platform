package com.zt.sdk.example;

import com.zt.sdk.GovernanceFacade;
import com.zt.sdk.ZtSecurityClient;

import java.util.Map;

public final class GovernedExecution {
    private GovernedExecution() {
    }

    public static void main(String[] args) {
        ZtSecurityClient client = new ZtSecurityClient(
                "http://localhost:8080",
                "example-key",
                "tenant-a");
        GovernanceFacade governance = new GovernanceFacade(client);

        var result = governance.execute(
                "workload/payment-api",
                "rotate-credential",
                "secret/payment-db",
                "tenant-a",
                Map.of("type", "CHANGE_REQUEST"),
                null);
        System.out.println(result);
    }
}
