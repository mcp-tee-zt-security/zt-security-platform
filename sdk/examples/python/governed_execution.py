from zt_security import ActionContext, ZtSecurityClient

client = ZtSecurityClient(
    "http://localhost:8080",
    "example-key",
    "tenant-a",
)

result = client.governance.execute(
    ActionContext(
        subject="workload/payment-api",
        action="rotate-credential",
        resource="secret/payment-db",
        tenant_id="tenant-a",
    ),
    evidence={"type": "CHANGE_REQUEST", "subject": "payment-api"},
)

print(result)
