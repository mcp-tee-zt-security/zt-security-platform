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
                "dev-master-key",
                "11111111-1111-1111-1111-111111111111",
                "88888888-8888-8888-8888-888888888801");
        GovernanceFacade governance = new GovernanceFacade(client);

        var result = governance.execute(
                "payment-agent",
                "payment.transfer",
                "bank_account/ACC-1001",
                "11111111-1111-1111-1111-111111111111",
                Map.of("amount", 100000, "task_id", "payment-demo-task",
                    "tool_id", "33333333-3333-3333-3333-333333333301"),
                Map.of("type", "CHANGE_REQUEST"),
                null);
        System.out.println(result);
    }
}
