from __future__ import annotations

from typing import TYPE_CHECKING

from .errors import ZtSecurityConfigurationError, ZtSecurityError, ZtSecurityProtocolError
from .models import (
    ActionContext, Approval, ApprovalStatus, Decision, ExecutionStatus,
    GovernedExecutionResult, JsonObject, PolicyDecision, ResumedExecutionResult,
)

if TYPE_CHECKING:
    from .client import ZtSecurityClient


def _attach_checkpoint(error: ZtSecurityError, checkpoint: JsonObject) -> None:
    # IDs/stage only: do not attach request payloads, credentials, or HTTP sessions.
    error.workflow = {**checkpoint, **(error.workflow or {})}


class GovernanceFacade:
    """Explicit prepare, approve, execute and verify lifecycle."""

    def __init__(self, client: ZtSecurityClient):
        self.client = client

    def evaluate(
        self, context: ActionContext, *, idempotency_key: str | None = None,
    ) -> JsonObject:
        return self.client.evaluate(context, idempotency_key)

    def evaluate_typed(
        self, context: ActionContext, *, idempotency_key: str | None = None,
    ) -> PolicyDecision:
        return self.client.evaluate_typed(context, idempotency_key)

    def prepare(
        self,
        context: ActionContext,
        *,
        evidence: JsonObject | None = None,
        approval: JsonObject | Approval | None = None,
        idempotency_key: str | None = None,
    ) -> GovernedExecutionResult:
        """Evaluate and persist a contract; never invoke an external action."""
        checkpoint: JsonObject = {"stage": "evaluate"}
        try:
            decision = self.evaluate_typed(context, idempotency_key=idempotency_key)
            wire_decision = decision.to_dict()
            checkpoint.update(decision_id=decision.id, request_id=decision.request_id)
            if decision.decision == Decision.DENY:
                return GovernedExecutionResult(wire_decision, None, None, None, None, None)
            if decision.id is None:
                raise ZtSecurityProtocolError("SDK decision is missing its durable contract identifier")
            wire_approval = approval.to_dict() if isinstance(approval, Approval) else approval
            approval_model = Approval.from_dict(wire_approval) if wire_approval is not None else None
            if approval_model is not None:
                if approval_model.request_id != decision.request_id:
                    raise ZtSecurityConfigurationError(
                        "Approval belongs to another decision; resume the original contract instead"
                    )
                if approval_model.status in (ApprovalStatus.REJECTED, ApprovalStatus.EXPIRED):
                    raise ZtSecurityConfigurationError("Cannot use a rejected or expired approval")
                checkpoint["approval_id"] = approval_model.id
            checkpoint["stage"] = "create_evidence"
            created_evidence = self.client.create_evidence(evidence) if evidence is not None else None
            if created_evidence is not None:
                checkpoint["evidence_id"] = created_evidence["id"]
            if decision.requires_approval and wire_approval is None:
                checkpoint["stage"] = "request_approval"
                wire_approval = self.client.request_approval(
                    {"action": context.action, "resource": context.resource, "decision": wire_decision}
                )
                approval_model = Approval.from_dict(wire_approval)
                checkpoint["approval_id"] = approval_model.id
                if approval_model.request_id != decision.request_id:
                    raise ZtSecurityProtocolError("Server returned approval for another decision")
            checkpoint["stage"] = "create_contract"
            contract = self.client.create_execution_contract({
                "action": context.action, "resource": context.resource,
                "policyDecision": wire_decision,
                "evidenceIds": [created_evidence["id"]] if created_evidence is not None else [],
                "approval": wire_approval,
            })
            return GovernedExecutionResult(
                wire_decision, created_evidence, wire_approval, contract, None, None
            )
        except ZtSecurityError as error:
            _attach_checkpoint(error, checkpoint)
            raise

    def execute(
        self,
        context: ActionContext,
        *,
        evidence: JsonObject | None = None,
        approval: JsonObject | Approval | None = None,
        idempotency_key: str | None = None,
    ) -> GovernedExecutionResult:
        prepared = self.prepare(
            context, evidence=evidence, approval=approval, idempotency_key=idempotency_key
        )
        if prepared.contract is None:
            return prepared
        if prepared.decision_model.requires_approval:
            if prepared.approval is None or prepared.approval.get("status") != "APPROVED":
                return prepared
        try:
            executed = self.resume(prepared.contract["id"])
        except ZtSecurityError as error:
            checkpoint: JsonObject = {
                "decision_id": prepared.decision_model.id,
                "request_id": prepared.decision_model.request_id,
                "contract_id": prepared.contract["id"],
            }
            if prepared.evidence is not None:
                checkpoint["evidence_id"] = prepared.evidence["id"]
            if prepared.approval is not None:
                checkpoint["approval_id"] = prepared.approval["id"]
            _attach_checkpoint(error, checkpoint)
            raise
        return GovernedExecutionResult(
            prepared.decision, prepared.evidence, prepared.approval, prepared.contract,
            executed.execution.to_dict(),
            executed.verification.to_dict() if executed.verification is not None else None,
        )

    def resume(self, contract_id: str) -> ResumedExecutionResult:
        """Use the saved contract; never re-run preparation or create a new approval."""
        contract_id = self.client._id(contract_id, "contract_id")
        checkpoint: JsonObject = {"stage": "load_contract", "contract_id": contract_id}
        try:
            contract = self.client.get_execution_contract_typed(contract_id)
            checkpoint["stage"] = "execute_contract"
            execution = self.client.execute_contract_typed(contract.id)
            checkpoint["execution_id"] = execution.id
            verification = None
            if execution.status == ExecutionStatus.SUCCEEDED:
                checkpoint["stage"] = "verify_execution"
                verification = self.client.verify_execution_typed(execution.id)
            return ResumedExecutionResult(contract, execution, verification)
        except ZtSecurityError as error:
            _attach_checkpoint(error, checkpoint)
            raise

