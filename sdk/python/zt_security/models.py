from dataclasses import dataclass, field
from typing import Any, Literal

Decision = Literal["ALLOW", "DENY", "REQUIRE_APPROVAL"]


@dataclass(frozen=True)
class ActionContext:
    subject: str
    action: str
    resource: str
    tenant_id: str
    attributes: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class GovernedExecutionResult:
    decision: dict[str, Any]
    evidence: dict[str, Any] | None
    approval: dict[str, Any] | None
    contract: dict[str, Any] | None
    execution: dict[str, Any] | None
    verification: dict[str, Any] | None
