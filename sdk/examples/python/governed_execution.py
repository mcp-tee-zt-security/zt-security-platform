"""Local integrated-server example; unsupported execution is never success."""
import os

from zt_security import ActionContext, ZtSecurityClient

TENANT = "11111111-1111-1111-1111-111111111111"
WORKSPACE = "88888888-8888-8888-8888-888888888801"

with ZtSecurityClient(
    os.environ.get("ZT_API_URL", "http://localhost:8080"),
    os.environ["ZT_API_KEY"],
    TENANT,
    workspace_id=WORKSPACE,
    timeout=(3, 10),
) as client:
    result = client.governance.execute(
        ActionContext(
            subject="payment-agent",
            action="payment.transfer",
            resource="bank_account/ACC-1001",
            tenant_id=TENANT,
            attributes={
                "amount": 100000,
                "task_id": "payment-demo-task",
                "tool_id": "33333333-3333-3333-3333-333333333301",
            },
        ),
        evidence={"type": "CHANGE_REQUEST", "subject": "payment-agent"},
    )
    print("Decision:", result.decision_model.decision.value)
    print("Verified success:", result.succeeded)
    if result.pending_approval:
        print("Approval ID:", result.approval["id"])
        print("Save contract ID and resume after approval:", result.contract["id"])
    elif result.execution is not None:
        print("Execution status:", result.execution["status"])

