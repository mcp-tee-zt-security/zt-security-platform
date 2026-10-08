from zt_security import ActionContext, ZtSecurityClient

client = ZtSecurityClient(
    "http://localhost:8080",
    "example-key",
    "tenant-a",
)
agent = client.agents.register(
    {
        "name": "soc-remediation-agent",
        "capabilities": ["isolate-workload"],
    }
)

result = client.agents.execute_governed(
    agent["id"],
    ActionContext(
        subject="agent/soc-remediation-agent",
        action="isolate-workload",
        resource="workload/suspicious-api",
        tenant_id="tenant-a",
    ),
)

print(result)
