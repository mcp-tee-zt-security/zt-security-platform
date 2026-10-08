from __future__ import annotations

import uuid
from typing import Any

from .agent import AgentRuntime
from .governance import GovernanceFacade
from .transport import Transport


class ZtSecurityClient:
    """Canonical Python client for the 4.75 developer platform."""

    def __init__(
        self,
        base_url: str,
        api_key: str,
        tenant_id: str,
        workspace_id: str | None = None,
        timeout: int = 10,
        retries: int = 2,
    ):
        self.http = Transport(
            base_url,
            api_key,
            tenant_id,
            workspace_id,
            timeout,
            retries,
        )
        self.governance = GovernanceFacade(self)
        self.agents = AgentRuntime(self)

    def evaluate(self, request: dict[str, Any], idempotency_key: str | None = None):
        return self.http.post(
            "/v1/actions/evaluate",
            request,
            idempotency_key=idempotency_key or str(uuid.uuid4()),
        )

    def start_runtime_session(self, agent, task_id=None, source="SDK", metadata=None):
        return self.http.post(
            "/v1/runtime/sessions",
            {
                "agent": agent,
                "taskId": task_id,
                "source": source,
                "metadata": metadata or {},
            },
        )

    def runtime_check(self, session_id, request):
        return self.http.post(f"/v1/runtime/sessions/{session_id}/check", request)

    def runtime_session(self, session_id):
        return self.http.get(f"/v1/runtime/sessions/{session_id}")

    def end_runtime_session(self, session_id):
        return self.http.post(f"/v1/runtime/sessions/{session_id}/end", {})

    def policy_lint(self, text):
        return self.http.post("/v1/governance/policies/lint", {"policyText": text})

    def policy_blast_radius(self, policy_id):
        return self.http.get(f"/v1/governance/policies/{policy_id}/blast-radius")

    def export_policy(self, policy_id):
        return self.http.get(f"/v1/governance/policies/{policy_id}/as-code")

    def resolve_identity(self, body):
        return self.http.post("/v1/identity/resolve", body)

    def verify_attestation(self, body):
        return self.http.post("/v1/attestation/verify", body)

    def check_capability(self, body):
        return self.http.post("/v1/capabilities/check", body)

    def create_evidence(self, body):
        return self.http.post("/v1/evidence", body)

    def verify_evidence(self, evidence_id):
        return self.http.post(f"/v1/evidence/{evidence_id}/verify", {})

    def request_approval(self, body):
        return self.http.post("/v1/approvals", body)

    def approve(self, approval_id, body=None):
        return self.http.post(f"/v1/approvals/{approval_id}/approve", body or {})

    def create_execution_contract(self, body):
        return self.http.post("/v1/execution/contracts", body)

    def execute_contract(self, contract_id, body=None):
        return self.http.post(
            f"/v1/execution/contracts/{contract_id}/execute",
            body or {},
        )

    def verify_execution(self, execution_id, body=None):
        return self.http.post(
            f"/v1/execution/{execution_id}/verify",
            body or {},
        )

    def register_organization(self, body):
        return self.http.post("/v1/federation/organizations", body)

    def establish_trust(self, body):
        return self.http.post("/v1/federation/trust", body)

    def register_agent(self, body):
        return self.http.post("/v1/agents", body)

    def create_mission(self, agent_id, body):
        return self.http.post(f"/v1/agents/{agent_id}/missions", body)

    def create_simulation(self, body):
        return self.http.post("/v1/simulations", body)

    def run_simulation(self, simulation_id, body=None):
        return self.http.post(
            f"/v1/simulations/{simulation_id}/run",
            body or {},
        )

    def apply_kubernetes_policy(self, body):
        return self.http.post("/v1/kubernetes/policies", body)

    def observability_metrics(self):
        return self.http.get("/v1/observability/metrics")

    def replay_events(self, trace_id=None, limit=100):
        path = f"/v1/observability/events?limit={limit}"
        if trace_id:
            path += f"&traceId={trace_id}"
        return self.http.get(path)
