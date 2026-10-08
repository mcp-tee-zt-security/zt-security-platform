from zt_security import ActionContext, ZtSecurityClient

client = ZtSecurityClient(
    "http://localhost:8080",
    "dev-master-key",
    "11111111-1111-1111-1111-111111111111",
    workspace_id="88888888-8888-8888-8888-888888888801",
)
agent = client.agents.register(
    {
        "name": "mission-payment-agent",
    }
)

result = client.agents.execute_governed(
    agent["id"],
    ActionContext(
        subject="mission-payment-agent",
        action="payment.transfer",
        resource="bank_account/ACC-1001",
        tenant_id="11111111-1111-1111-1111-111111111111",
        attributes={"amount": 100000},
    ),
)

print(result)
