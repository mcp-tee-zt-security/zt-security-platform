from __future__ import annotations

from collections.abc import Mapping
from copy import deepcopy
import json
from typing import Any
from urllib.parse import quote
from uuid import uuid4

import requests

from .agent import AgentRuntime
from .errors import ZtSecurityConfigurationError, ZtSecurityProtocolError
from .events import EventReplay
from .governance import GovernanceFacade
from .stitch import StitchRetrieval
from .models import (
    ActionContext, ApiModel, Approval, Evidence, EvaluationRequest, Execution,
    ExecutionContract, JsonObject, Mission, PolicyDecision, SecurityEvent, Verification,
)
from .transport import Timeout, TokenProvider, Transport, nonempty, valid_uuid

RequestBody = Mapping[str, Any] | ActionContext | EvaluationRequest | ApiModel


class ZtSecurityClient:
    """Synchronous 4.75 client. Legacy methods return dictionaries; *_typed returns models."""

    def __init__(
        self,
        base_url: str,
        api_key: str | None = None,
        tenant_id: str | None = None,
        workspace_id: str | None = None,
        timeout: Timeout = 10,
        retries: int = 2,
        *,
        client_id: str | None = None,
        bearer_token: str | TokenProvider | None = None,
        session: requests.Session | None = None,
        max_retry_delay: float = 30,
    ):
        self.http = Transport(
            base_url, api_key, tenant_id, workspace_id, timeout, retries,
            client_id=client_id, bearer_token=bearer_token, session=session,
            max_retry_delay=max_retry_delay,
        )
        self.tenant_id = self.http.tenant_id
        self.workspace_id = self.http.workspace_id
        self.governance = GovernanceFacade(self)
        self.agents = AgentRuntime(self)
        self.events = EventReplay(self)
        self.stitch = StitchRetrieval(self)

    def close(self) -> None:
        self.http.close()

    def __enter__(self) -> ZtSecurityClient:
        self.http.__enter__()
        return self

    def __exit__(self, *args: Any) -> None:
        self.close()

    def _body(self, body: RequestBody) -> JsonObject:
        if isinstance(body, (ActionContext, EvaluationRequest, ApiModel)):
            result = body.to_dict()
        elif isinstance(body, Mapping):
            result = deepcopy(dict(body))
        else:
            raise ZtSecurityConfigurationError("Request body must be a mapping or SDK model")
        if "tenantId" in result:
            normalized = valid_uuid(result["tenantId"], "tenantId")
            if normalized != self.tenant_id:
                raise ZtSecurityConfigurationError("Request tenantId must match the client tenant")
            result["tenantId"] = normalized
        try:
            json.dumps(result, allow_nan=False)
        except (TypeError, ValueError) as error:
            raise ZtSecurityConfigurationError("Request must contain finite JSON values") from error
        return result

    @staticmethod
    def _object(value: Any, model: type[ApiModel] | None = None) -> JsonObject:
        if not isinstance(value, dict):
            raise ZtSecurityProtocolError("Expected a JSON object response")
        if model is not None:
            model.from_dict(value)
        return value

    def _post(
        self, path: str, body: RequestBody, model: type[ApiModel] | None = None,
        *, idempotency_key: str | None = None,
    ) -> JsonObject:
        return self._object(
            self.http.post(path, self._body(body), idempotency_key=idempotency_key), model
        )

    @staticmethod
    def _id(value: str, name: str = "id") -> str:
        return valid_uuid(value, name)

    @staticmethod
    def _same_response_id(body: JsonObject, key: str, expected: str) -> None:
        try:
            actual = valid_uuid(body.get(key), key)
        except ZtSecurityConfigurationError as error:
            raise ZtSecurityProtocolError(f"Response field {key} must be a UUID") from error
        if actual != expected:
            raise ZtSecurityProtocolError(f"Response field {key} does not match the requested resource")

    @staticmethod
    def _segment(value: str) -> str:
        return quote(nonempty(value, "path identifier"), safe="")

    def evaluate(
        self, request: Mapping[str, Any] | ActionContext | EvaluationRequest,
        idempotency_key: str | None = None,
    ) -> JsonObject:
        body = self._body(request)
        if "principal" not in body:
            # Input checks do not infer an identity type; the authoritative server does.
            ActionContext(
                body.get("subject"), body.get("action"), body.get("resource"),
                body.get("tenantId", self.tenant_id),
                body["attributes"] if body.get("attributes") is not None else {},
            )
        else:
            self._validate_structured(body)
        return self._post(
            "/v1/actions/evaluate", body, PolicyDecision,
            idempotency_key=idempotency_key if idempotency_key is not None else str(uuid4()),
        )

    def evaluate_typed(
        self, request: Mapping[str, Any] | ActionContext | EvaluationRequest,
        idempotency_key: str | None = None,
    ) -> PolicyDecision:
        return PolicyDecision.from_dict(self.evaluate(request, idempotency_key))

    @staticmethod
    def _validate_structured(body: JsonObject) -> None:
        from .models import Action, Principal, Resource

        principal, action, resource = (body.get(key) for key in ("principal", "action", "resource"))
        if not all(isinstance(value, dict) for value in (principal, action, resource)):
            raise ZtSecurityConfigurationError("principal, action and resource must be objects")
        Principal(principal.get("id"), principal.get("type"),
                  principal["attributes"] if principal.get("attributes") is not None else {}).to_dict()
        Action(action.get("name")).to_dict()
        Resource(resource.get("type"), resource.get("id"),
                 resource["attributes"] if resource.get("attributes") is not None else {}).to_dict()
        if body.get("context") is not None and not isinstance(body["context"], dict):
            raise ZtSecurityConfigurationError("context must be an object")

    def start_runtime_session(
        self, agent: str, task_id: str | None = None, source: str = "SDK",
        metadata: Mapping[str, Any] | None = None,
    ) -> JsonObject:
        nonempty(agent, "agent")
        if task_id is not None:
            nonempty(task_id, "task_id")
        return self._post("/v1/runtime/sessions", {
            "agent": agent, "taskId": task_id, "source": nonempty(source, "source"),
            "metadata": dict(metadata) if metadata is not None else {},
        })

    def runtime_check(
        self, session_id: str, request: Mapping[str, Any] | EvaluationRequest,
        *, idempotency_key: str | None = None,
    ) -> JsonObject:
        body = self._body(request)
        if "principal" not in body:
            raise ZtSecurityConfigurationError("Runtime checks require a structured evaluation request")
        self._validate_structured(body)
        return self._post(
            f"/v1/runtime/sessions/{self._id(session_id)}/check", body, PolicyDecision,
            idempotency_key=idempotency_key,
        )

    def runtime_session(self, session_id: str) -> JsonObject:
        return self._object(self.http.get(f"/v1/runtime/sessions/{self._id(session_id)}"))

    def end_runtime_session(self, session_id: str) -> JsonObject:
        return self._post(f"/v1/runtime/sessions/{self._id(session_id)}/end", {})

    def policy_lint(self, text: str) -> JsonObject:
        return self._post("/v1/governance/policies/lint", {"policyText": nonempty(text, "text")})

    def policy_blast_radius(self, policy_id: str) -> JsonObject:
        return self._object(self.http.get(f"/v1/governance/policies/{self._id(policy_id)}/blast-radius"))

    def export_policy(self, policy_id: str) -> JsonObject:
        return self._object(self.http.get(f"/v1/governance/policies/{self._id(policy_id)}/as-code"))

    def resolve_identity(self, body: RequestBody) -> JsonObject:
        result = self._post("/v1/identity/resolve", body)
        if not isinstance(result.get("resolved"), bool):
            raise ZtSecurityProtocolError("Identity result must include boolean resolved")
        return result

    def verify_attestation(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/attestation/verify", body)

    def check_capability(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/capabilities/check", body)

    def create_evidence(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/evidence", body, Evidence)

    def create_evidence_typed(self, body: RequestBody) -> Evidence:
        return Evidence.from_dict(self.create_evidence(body))

    def verify_evidence(self, evidence_id: str) -> JsonObject:
        result = self._post(f"/v1/evidence/{self._id(evidence_id)}/verify", {})
        if not isinstance(result.get("verified"), bool):
            raise ZtSecurityProtocolError("Evidence verification must include boolean verified")
        self._same_response_id(result, "id", self._id(evidence_id))
        return result

    def request_approval(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/approvals", body, Approval)

    def request_approval_typed(self, body: RequestBody) -> Approval:
        return Approval.from_dict(self.request_approval(body))

    def approve(self, approval_id: str, body: RequestBody | None = None) -> JsonObject:
        expected = self._id(approval_id)
        result = self._post(f"/v1/approvals/{expected}/approve", {} if body is None else body, Approval)
        self._same_response_id(result, "id", expected)
        return result

    def create_execution_contract(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/execution/contracts", body, ExecutionContract)

    def create_execution_contract_typed(self, body: RequestBody) -> ExecutionContract:
        return ExecutionContract.from_dict(self.create_execution_contract(body))

    def get_execution_contract(self, contract_id: str) -> JsonObject:
        expected = self._id(contract_id)
        result = self._object(
            self.http.get(f"/v1/execution/contracts/{expected}"), ExecutionContract
        )
        self._same_response_id(result, "id", expected)
        return result

    def get_execution_contract_typed(self, contract_id: str) -> ExecutionContract:
        return ExecutionContract.from_dict(self.get_execution_contract(contract_id))

    def execute_contract(self, contract_id: str, body: RequestBody | None = None) -> JsonObject:
        expected = self._id(contract_id)
        result = self._post(
            f"/v1/execution/contracts/{expected}/execute", {} if body is None else body, Execution
        )
        self._same_response_id(result, "contractId", expected)
        return result

    def execute_contract_typed(self, contract_id: str) -> Execution:
        return Execution.from_dict(self.execute_contract(contract_id))

    def verify_execution(self, execution_id: str, body: RequestBody | None = None) -> JsonObject:
        expected = self._id(execution_id)
        result = self._post(
            f"/v1/execution/{expected}/verify", {} if body is None else body, Verification
        )
        self._same_response_id(result, "executionId", expected)
        return result

    def verify_execution_typed(self, execution_id: str) -> Verification:
        return Verification.from_dict(self.verify_execution(execution_id))

    def register_organization(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/federation/organizations", body)

    def establish_trust(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/federation/trust", body)

    def register_agent(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/agents", body)

    def create_mission(self, agent_id: str, body: RequestBody) -> JsonObject:
        return self._post(f"/v1/agents/{self._segment(agent_id)}/missions", body, Mission)

    def create_mission_typed(self, agent_id: str, body: RequestBody) -> Mission:
        return Mission.from_dict(self.create_mission(agent_id, body))

    def create_simulation(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/simulations", body)

    def run_simulation(
        self, simulation_id: str, body: RequestBody | None = None,
    ) -> JsonObject:
        return self._post(
            f"/v1/simulations/{self._id(simulation_id)}/run", {} if body is None else body
        )

    def apply_kubernetes_policy(self, body: RequestBody) -> JsonObject:
        return self._post("/v1/kubernetes/policies", body)

    def observability_metrics(self) -> JsonObject:
        return self._object(self.http.get("/v1/observability/metrics"))

    def replay_events(self, trace_id: str | None = None, limit: int = 100) -> list[JsonObject]:
        if isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= 500:
            raise ZtSecurityConfigurationError("limit must be an integer from 1 to 500")
        params: dict[str, Any] = {"limit": limit}
        if trace_id is not None:
            params["traceId"] = nonempty(trace_id, "trace_id")
        result = self.http.get("/v1/observability/events", params=params)
        if not isinstance(result, list):
            raise ZtSecurityProtocolError("Event replay must be a JSON array")
        for body in result:
            event = SecurityEvent.from_dict(body)
            if event.tenant_id != self.tenant_id:
                raise ZtSecurityProtocolError("Event belongs to another tenant")
            workspace = body.get("workspaceId")
            if workspace is not None and workspace != self.workspace_id:
                raise ZtSecurityProtocolError("Event belongs to another workspace")
        return result

    def replay_events_typed(
        self, trace_id: str | None = None, limit: int = 100,
    ) -> list[SecurityEvent]:
        return [SecurityEvent.from_dict(event) for event in self.replay_events(trace_id, limit)]

