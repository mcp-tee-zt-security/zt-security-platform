"""Resume an existing approved contract; no new preparation or auto-approval."""
import os

from zt_security import ZtSecurityClient

with ZtSecurityClient(
    os.environ.get("ZT_API_URL", "http://localhost:8080"),
    os.environ["ZT_API_KEY"],
    os.environ["ZT_TENANT_ID"],
    workspace_id=os.environ.get("ZT_WORKSPACE_ID"),
    timeout=(3, 10),
) as client:
    result = client.governance.resume(os.environ["ZT_CONTRACT_ID"])
    print("Execution status:", result.execution.status.value)
    print("Verified success:", result.succeeded)

