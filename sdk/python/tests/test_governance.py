from __future__ import annotations

import pytest
import requests

from zt_security import ActionContext, ZtSecurityProtocolError, ZtSecurityTimeout
from conftest import (
    APPROVAL, CONTRACT, EXECUTION, TENANT, VERIFICATION, approval, contract, decision, execution, response,
)


def context():
    return ActionContext("payment-agent", "payment.transfer", "bank_account/ACC-1001", TENANT)


def test_pending_approval_stops_before_external_execution(make_client):
    client, session = make_client([
        response(200, decision("STEP_UP")), response(200, approval()),
        response(200, contract("PENDING_APPROVAL")),
    ])
    result = client.governance.execute(context())
    assert result.pending_approval
    assert result.execution is None
    assert result.contract["id"] == CONTRACT
    assert not result.succeeded
    assert len(session.calls) == 3


def test_denied_action_creates_no_evidence_or_contract(make_client):
    client, session = make_client([response(200, decision("DENY"))])
    result = client.governance.execute(context(), evidence={"subject": "payment-agent"})
    assert result.contract is None
    assert result.evidence is None
    assert not result.succeeded
    assert len(session.calls) == 1


def test_resume_uses_saved_contract_without_evaluate_or_new_approval(make_client):
    client, session = make_client([
        response(200, contract()), response(200, execution("SUCCEEDED")),
        response(200, {"id": VERIFICATION, "executionId": EXECUTION,
                       "verified": True, "reason": "Connector verified"}),
    ])
    result = client.governance.resume(CONTRACT)
    assert result.succeeded
    assert all("/actions/evaluate" not in call[1] for call in session.calls)
    assert all("/approvals" not in call[1] for call in session.calls)


def test_unsupported_execution_is_never_reported_as_success(make_client):
    client, session = make_client([
        response(200, decision()), response(200, contract()),
        response(200, contract()), response(200, execution()),
    ])
    result = client.governance.execute(context())
    assert result.execution["status"] == "UNSUPPORTED"
    assert result.verification is None
    assert not result.succeeded
    assert len(session.calls) == 4


def test_false_connector_verification_is_not_success(make_client):
    client, session = make_client([
        response(200, contract()), response(200, execution("SUCCEEDED")),
        response(200, {"id": VERIFICATION, "executionId": EXECUTION,
                       "verified": False, "reason": "Connector verification failed"}),
    ])
    assert not client.governance.resume(CONTRACT).succeeded


def test_verification_from_another_execution_is_rejected(make_client):
    client, session = make_client([
        response(200, contract()), response(200, execution("SUCCEEDED")),
        response(200, {"id": VERIFICATION, "executionId": CONTRACT,
                       "verified": True, "reason": "Wrong execution"}),
    ])
    with pytest.raises(ZtSecurityProtocolError):
        client.governance.resume(CONTRACT)


def test_partial_preparation_preserves_known_evidence_id(make_client):
    client, session = make_client([
        response(200, decision()),
        response(200, {"id": APPROVAL, "type": "CHANGE_REQUEST", "subject": "payment-agent",
                       "payload": {}, "digest": "0" * 64}),
        requests.ReadTimeout(),
    ])
    with pytest.raises(ZtSecurityTimeout) as caught:
        client.governance.prepare(context(), evidence={"subject": "payment-agent"})
    assert caught.value.workflow["stage"] == "create_contract"
    assert caught.value.workflow["evidence_id"] == APPROVAL
    assert "contract_id" not in caught.value.workflow
    assert len(session.calls) == 3

