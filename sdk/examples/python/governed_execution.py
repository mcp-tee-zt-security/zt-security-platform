from zt_security import ActionContext, ZtSecurityClient

client = ZtSecurityClient(
    "http://localhost:8080",
    "dev-master-key",
    "11111111-1111-1111-1111-111111111111",
    workspace_id="88888888-8888-8888-8888-888888888801",
)

result = client.governance.execute(
    ActionContext(
        subject="payment-agent",
        action="payment.transfer",
        resource="bank_account/ACC-1001",
        tenant_id="11111111-1111-1111-1111-111111111111",
        attributes={"amount": 100000, "task_id": "payment-demo-task",
                    "tool_id": "33333333-3333-3333-3333-333333333301"},
    ),
    evidence={"type": "CHANGE_REQUEST", "subject": "payment-api"},
)

print(result)
