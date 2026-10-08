from __future__ import annotations

from collections.abc import Mapping
from copy import deepcopy
from dataclasses import dataclass, field, fields
from datetime import datetime
from enum import Enum
import math
import re
from typing import Any, Literal, TypeVar, overload

from .errors import ZtSecurityConfigurationError, ZtSecurityProtocolError
from .transport import nonempty, valid_uuid

JsonObject = dict[str, Any]


class Decision(str, Enum):
    ALLOW = "ALLOW"
    DENY = "DENY"
    STEP_UP = "STEP_UP"
    REQUIRE_APPROVAL = "REQUIRE_APPROVAL"  # Accepted legacy spelling; server emits STEP_UP.


class ApprovalStatus(str, Enum):
    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"
    EXPIRED = "EXPIRED"


class ExecutionStatus(str, Enum):
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    UNSUPPORTED = "UNSUPPORTED"


class ContractStatus(str, Enum):
    READY = "READY"
    PENDING_APPROVAL = "PENDING_APPROVAL"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    UNSUPPORTED = "UNSUPPORTED"


def _object(value: Any, name: str = "response") -> JsonObject:
    if not isinstance(value, Mapping) or any(not isinstance(key, str) for key in value):
        raise ZtSecurityProtocolError(f"{name} must be a JSON object")
    return deepcopy(dict(value))


def _string(body: Mapping[str, Any], key: str) -> str:
    value = body.get(key)
    if not isinstance(value, str):
        raise ZtSecurityProtocolError(f"Response field {key} must be a string")
    return value


@overload
def _uuid(body: Mapping[str, Any], key: str, optional: Literal[False] = False) -> str: ...


@overload
def _uuid(body: Mapping[str, Any], key: str, optional: Literal[True]) -> str | None: ...


def _uuid(body: Mapping[str, Any], key: str, optional: bool = False) -> str | None:
    value = body.get(key)
    if value is None and optional:
        return None
    try:
        return valid_uuid(value, key)
    except ZtSecurityConfigurationError as error:
        raise ZtSecurityProtocolError(f"Response field {key} must be a UUID") from error


EnumValue = TypeVar("EnumValue", bound=Enum)


def _enum(enum_type: type[EnumValue], body: Mapping[str, Any], key: str) -> EnumValue:
    try:
        return enum_type(_string(body, key))
    except ValueError as error:
        raise ZtSecurityProtocolError(f"Unknown response state in {key}") from error


def _boolean(body: Mapping[str, Any], key: str) -> bool:
    value = body.get(key)
    if not isinstance(value, bool):
        raise ZtSecurityProtocolError(f"Response field {key} must be a boolean")
    return value


@overload
def _date(body: Mapping[str, Any], key: str, optional: Literal[False] = False) -> datetime: ...


@overload
def _date(body: Mapping[str, Any], key: str, optional: Literal[True]) -> datetime | None: ...


def _date(body: Mapping[str, Any], key: str, optional: bool = False) -> datetime | None:
    value = body.get(key)
    if value is None and optional:
        return None
    try:
        timestamp = _string(body, key)
        match = re.fullmatch(
            r"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})",
            timestamp,
        )
        if match is None:
            raise ValueError("Expected RFC3339 timestamp")
        fraction = "." + match[2].ljust(6, "0")[:6] if match[2] is not None else ""
        offset = "+00:00" if match[3] == "Z" else match[3]
        parsed = datetime.fromisoformat(match[1] + fraction + offset)
        if parsed.tzinfo is None:
            raise ValueError("Timezone required")
        return parsed
    except ValueError as error:
        raise ZtSecurityProtocolError(f"Response field {key} must be a timestamp with timezone") from error


@dataclass(frozen=True)
class ActionContext:
    subject: str
    action: str
    resource: str
    tenant_id: str
    attributes: JsonObject = field(default_factory=dict)

    def __post_init__(self) -> None:
        nonempty(self.subject, "subject")
        nonempty(self.action, "action")
        nonempty(self.resource, "resource")
        resource_type, separator, resource_id = self.resource.partition("/")
        if not separator or not resource_type or not resource_id:
            raise ZtSecurityConfigurationError("SDK resource must use type/id")
        valid_uuid(self.tenant_id, "tenant_id")
        if not isinstance(self.attributes, dict):
            raise ZtSecurityConfigurationError("attributes must be a dictionary")

    def to_dict(self) -> JsonObject:
        return {
            "subject": self.subject, "action": self.action, "resource": self.resource,
            "tenantId": valid_uuid(self.tenant_id, "tenant_id"), "attributes": deepcopy(self.attributes),
        }


@dataclass(frozen=True)
class Principal:
    id: str
    type: str
    attributes: JsonObject = field(default_factory=dict)

    def to_dict(self) -> JsonObject:
        nonempty(self.id, "principal.id")
        nonempty(self.type, "principal.type")
        if not isinstance(self.attributes, dict):
            raise ZtSecurityConfigurationError("principal.attributes must be a dictionary")
        return {"id": self.id, "type": self.type, "attributes": deepcopy(self.attributes)}


@dataclass(frozen=True)
class Action:
    name: str

    def to_dict(self) -> JsonObject:
        return {"name": nonempty(self.name, "action.name")}


@dataclass(frozen=True)
class Resource:
    type: str
    id: str
    attributes: JsonObject = field(default_factory=dict)

    def to_dict(self) -> JsonObject:
        nonempty(self.type, "resource.type")
        nonempty(self.id, "resource.id")
        if not isinstance(self.attributes, dict):
            raise ZtSecurityConfigurationError("resource.attributes must be a dictionary")
        return {"type": self.type, "id": self.id, "attributes": deepcopy(self.attributes)}


@dataclass(frozen=True)
class EvaluationRequest:
    principal: Principal
    action: Action
    resource: Resource
    context: JsonObject = field(default_factory=dict)

    def to_dict(self) -> JsonObject:
        if not isinstance(self.context, dict):
            raise ZtSecurityConfigurationError("context must be a dictionary")
        return {
            "principal": self.principal.to_dict(), "action": self.action.to_dict(),
            "resource": self.resource.to_dict(), "context": deepcopy(self.context),
        }


@dataclass(frozen=True)
class ApiModel:
    raw: Mapping[str, Any] = field(default_factory=dict, repr=False, compare=False, kw_only=True)

    def to_dict(self) -> JsonObject:
        """Return the wire representation, including additive server fields."""
        if self.raw:
            return deepcopy(dict(self.raw))
        def wire(value: Any) -> Any:
            if isinstance(value, ApiModel):
                return value.to_dict()
            if isinstance(value, Enum):
                return value.value
            if isinstance(value, datetime):
                return value.isoformat()
            if isinstance(value, (list, tuple)):
                return [wire(item) for item in value]
            if isinstance(value, Mapping):
                return {key: wire(item) for key, item in value.items()}
            return deepcopy(value)
        result: JsonObject = {}
        for item in fields(self):
            if item.name == "raw":
                continue
            head, *tail = item.name.split("_")
            result[head + "".join(part.capitalize() for part in tail)] = wire(getattr(self, item.name))
        return result

    @classmethod
    def from_dict(cls, value: Any) -> ApiModel:
        raise NotImplementedError("Use a concrete response model")


@dataclass(frozen=True)
class Risk(ApiModel):
    score: float
    level: str

    @classmethod
    def from_dict(cls, value: Any) -> Risk:
        body = _object(value, "risk")
        score = body.get("score")
        if isinstance(score, bool) or not isinstance(score, (int, float)) or not math.isfinite(score):
            raise ZtSecurityProtocolError("risk.score must be finite")
        if not 0 <= score <= 100:
            raise ZtSecurityProtocolError("risk.score must be between 0 and 100")
        return cls(float(score), _string(body, "level"), raw=body)


@dataclass(frozen=True)
class PolicyDecision(ApiModel):
    request_id: str
    decision: Decision
    reason: str
    id: str | None = None
    trace_id: str | None = None
    risk: Risk | None = None

    @property
    def requires_approval(self) -> bool:
        return self.decision in (Decision.STEP_UP, Decision.REQUIRE_APPROVAL)

    @classmethod
    def from_dict(cls, value: Any) -> PolicyDecision:
        body = _object(value)
        trace = body.get("traceId")
        if trace is not None and not isinstance(trace, str):
            raise ZtSecurityProtocolError("traceId must be a string")
        return cls(
            _uuid(body, "requestId"), _enum(Decision, body, "decision"), _string(body, "reason"),
            _uuid(body, "id", True), trace,
            Risk.from_dict(body["risk"]) if body.get("risk") is not None else None, raw=body,
        )


@dataclass(frozen=True)
class Evidence(ApiModel):
    id: str
    type: str
    subject: str
    payload: JsonObject
    digest: str

    @classmethod
    def from_dict(cls, value: Any) -> Evidence:
        body = _object(value)
        digest = _string(body, "digest")
        if len(digest) != 64 or any(char not in "0123456789abcdefABCDEF" for char in digest):
            raise ZtSecurityProtocolError("Evidence digest must be SHA-256 hex")
        return cls(_uuid(body, "id"), _string(body, "type"), _string(body, "subject"),
                   _object(body.get("payload"), "payload"), digest, raw=body)


@dataclass(frozen=True)
class Approval(ApiModel):
    id: str
    request_id: str
    status: ApprovalStatus
    approver_id: str | None = None
    expires_at: datetime | None = None

    @classmethod
    def from_dict(cls, value: Any) -> Approval:
        body = _object(value)
        approver = body.get("approverId")
        if approver is not None and not isinstance(approver, str):
            raise ZtSecurityProtocolError("approverId must be a string")
        return cls(_uuid(body, "id"), _uuid(body, "requestId"),
                   _enum(ApprovalStatus, body, "status"), approver,
                   _date(body, "expiresAt", True), raw=body)


@dataclass(frozen=True)
class ExecutionContract(ApiModel):
    id: str
    action: str
    resource: str
    decision_id: str
    status: ContractStatus
    expires_at: datetime
    approval_id: str | None = None
    evidence_ids: tuple[str, ...] = ()

    @classmethod
    def from_dict(cls, value: Any) -> ExecutionContract:
        body = _object(value)
        ids = body.get("evidenceIds", [])
        if not isinstance(ids, list):
            raise ZtSecurityProtocolError("evidenceIds must be an array")
        evidence_ids = tuple(_uuid({"id": value}, "id") for value in ids)
        return cls(
            _uuid(body, "id"), _string(body, "action"), _string(body, "resource"),
            _uuid(body, "decisionId"), _enum(ContractStatus, body, "status"),
            _date(body, "expiresAt"), _uuid(body, "approvalId", True), evidence_ids, raw=body,
        )


@dataclass(frozen=True)
class Execution(ApiModel):
    id: str
    contract_id: str
    status: ExecutionStatus
    result: JsonObject

    @classmethod
    def from_dict(cls, value: Any) -> Execution:
        body = _object(value)
        return cls(_uuid(body, "id"), _uuid(body, "contractId"),
                   _enum(ExecutionStatus, body, "status"),
                   _object(body.get("result"), "result"), raw=body)


@dataclass(frozen=True)
class Verification(ApiModel):
    id: str
    execution_id: str
    verified: bool
    reason: str

    @classmethod
    def from_dict(cls, value: Any) -> Verification:
        body = _object(value)
        return cls(_uuid(body, "id"), _uuid(body, "executionId"),
                   _boolean(body, "verified"), _string(body, "reason"), raw=body)


@dataclass(frozen=True)
class SecurityEvent(ApiModel):
    event_id: str
    event_type: str
    tenant_id: str
    subject: str
    occurred_at: datetime
    payload: JsonObject
    trace_id: str | None = None

    @classmethod
    def from_dict(cls, value: Any) -> SecurityEvent:
        body = _object(value)
        trace = body.get("traceId")
        if trace is not None and not isinstance(trace, str):
            raise ZtSecurityProtocolError("traceId must be a string")
        return cls(
            _uuid(body, "eventId"), _string(body, "eventType"), _uuid(body, "tenantId"),
            _string(body, "subject"), _date(body, "occurredAt"),
            _object(body.get("payload"), "payload"), trace, raw=body,
        )


@dataclass(frozen=True)
class Mission(ApiModel):
    id: str
    external_task_id: str
    agent_identity_id: str
    tool_ids: tuple[str, ...]
    status: str
    expires_at: datetime

    @classmethod
    def from_dict(cls, value: Any) -> Mission:
        body = _object(value)
        tools = body.get("toolIds")
        if not isinstance(tools, list) or not tools:
            raise ZtSecurityProtocolError("Mission must include delegated toolIds")
        return cls(
            _uuid(body, "id"), _string(body, "externalTaskId"), _uuid(body, "agentIdentityId"),
            tuple(_uuid({"id": tool}, "id") for tool in tools), _string(body, "status"),
            _date(body, "expiresAt"), raw=body,
        )


def _execution_succeeded(execution: JsonObject | None, verification: JsonObject | None) -> bool:
    return (
        execution is not None and verification is not None
        and execution.get("status") == "SUCCEEDED" and verification.get("verified") is True
    )


@dataclass(frozen=True)
class GovernedExecutionResult:
    decision: JsonObject
    evidence: JsonObject | None
    approval: JsonObject | None
    contract: JsonObject | None
    execution: JsonObject | None
    verification: JsonObject | None

    @property
    def decision_model(self) -> PolicyDecision:
        return PolicyDecision.from_dict(self.decision)

    @property
    def pending_approval(self) -> bool:
        return (
            self.decision_model.requires_approval and self.execution is None
            and self.approval is not None and self.approval.get("status") == "PENDING"
        )

    @property
    def succeeded(self) -> bool:
        return _execution_succeeded(self.execution, self.verification)


@dataclass(frozen=True)
class ResumedExecutionResult:
    contract: ExecutionContract
    execution: Execution
    verification: Verification | None

    @property
    def succeeded(self) -> bool:
        return self.execution.status == ExecutionStatus.SUCCEEDED and (
            self.verification is not None and self.verification.verified
        )

