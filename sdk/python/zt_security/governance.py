from __future__ import annotations

from typing import Any

from .models import ActionContext, GovernedExecutionResult


class GovernanceFacade:
    """Execute the canonical governed execution lifecycle."""

    def __init__(self, client: Any):
        self.client = client

    def evaluate(self, context: ActionContext) -> dict[str, Any]:
        return self.client.evaluate(
            {
                "subject": context.subject,
                "action": context.action,
                "resource": context.resource,
                "tenantId": context.tenant_id,
                "attributes": context.attributes,
            }
        )

    def execute(
        self,
        context: ActionContext,
        *,
        evidence: dict[str, Any] | None = None,
        approval: dict[str, Any] | None = None,
    ) -> GovernedExecutionResult:
        decision = self.evaluate(context)
        if decision.get("decision") == "DENY":
            return GovernedExecutionResult(
                decision,
                evidence,
                approval,
                None,
                None,
                None,
            )

        created_evidence = (
            self.client.create_evidence(evidence) if evidence is not None else None
        )
        created_approval = approval
        if decision.get("decision") == "REQUIRE_APPROVAL" and not created_approval:
            created_approval = self.client.request_approval(
                {
                    "action": context.action,
                    "resource": context.resource,
                    "decision": decision,
                }
            )

        evidence_ids = []
        if created_evidence and created_evidence.get("id"):
            evidence_ids.append(created_evidence["id"])

        contract = self.client.create_execution_contract(
            {
                "action": context.action,
                "resource": context.resource,
                "policyDecision": decision,
                "evidenceIds": evidence_ids,
                "approval": created_approval,
            }
        )
        execution = (
            self.client.execute_contract(contract["id"], {})
            if contract.get("id")
            else None
        )
        verification = (
            self.client.verify_execution(execution["id"], {})
            if execution and execution.get("id")
            else None
        )

        return GovernedExecutionResult(
            decision,
            created_evidence,
            created_approval,
            contract,
            execution,
            verification,
        )
