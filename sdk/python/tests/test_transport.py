from __future__ import annotations

import pytest
import requests

from zt_security import (
    ActionContext, ZtSecurityAuthenticationError, ZtSecurityConfigurationError,
    ZtSecurityProtocolError, ZtSecurityRateLimitError, ZtSecurityTimeout,
    ZtSecurityUnsupportedError, ZtSecurityClient,
    ZtSecurityServerError,
)
from conftest import TENANT, decision, response


def context():
    return ActionContext("payment-agent", "payment.transfer", "bank_account/ACC-1001", TENANT)


def test_evaluate_reuses_one_key_after_ambiguous_timeout(make_client):
    client, session = make_client([requests.ReadTimeout(), response(200, decision())])
    result = client.evaluate(context())
    assert result["decision"] == "ALLOW"
    first, second = session.calls
    assert first[2]["headers"]["Idempotency-Key"] == second[2]["headers"]["Idempotency-Key"]
    assert first[2]["json"] == second[2]["json"]


def test_create_evidence_is_never_automatically_replayed(make_client):
    client, session = make_client([requests.ReadTimeout()])
    with pytest.raises(ZtSecurityTimeout) as caught:
        client.create_evidence({"subject": "payment-agent"})
    assert caught.value.ambiguous is True
    assert len(session.calls) == 1


def test_idempotency_header_does_not_make_unknown_writes_retryable(make_client):
    client, session = make_client([response(503, {"error": "UNAVAILABLE"})])
    with pytest.raises(ZtSecurityServerError) as caught:
        client.http.post("/v1/agents", {}, idempotency_key="caller-key")
    assert getattr(caught.value, "status_code", None) == 503
    assert len(session.calls) == 1


def test_authentication_failure_is_not_retried_and_secret_is_redacted(make_client):
    client, session = make_client([
        response(401, {"error": "UNAUTHORIZED", "message": "rejected test-secret"},
                 headers={"X-Request-Id": "request-123"})
    ])
    with pytest.raises(ZtSecurityAuthenticationError) as caught:
        client.http.get("/v1/observability/metrics")
    assert len(session.calls) == 1
    assert caught.value.request_id == "request-123"
    assert "test-secret" not in str(caught.value)
    assert "test-secret" not in str(caught.value.body)


def test_retry_after_is_honored(make_client, monkeypatch):
    delays = []
    monkeypatch.setattr("zt_security.transport.time.sleep", delays.append)
    client, session = make_client([
        response(429, {"error": "RATE_LIMIT"}, headers={"Retry-After": "2"}),
        response(200, {"eventCount": 0}),
    ])
    assert client.observability_metrics()["eventCount"] == 0
    assert delays == [2.0]
    assert len(session.calls) == 2


def test_long_retry_after_returns_control_without_retry(make_client):
    client, session = make_client([
        response(429, {"error": "RATE_LIMIT"}, headers={"Retry-After": "120"})
    ])
    with pytest.raises(ZtSecurityRateLimitError) as caught:
        client.observability_metrics()
    assert caught.value.retry_after == 120
    assert len(session.calls) == 1


def test_retry_after_http_date(make_client, monkeypatch):
    monkeypatch.setattr("zt_security.transport.time.time", lambda: 0)
    client, session = make_client([
        response(429, {"error": "RATE_LIMIT"},
                 headers={"Retry-After": "Thu, 01 Jan 1970 00:00:02 GMT"}),
        response(200, {}),
    ])
    delays = []
    monkeypatch.setattr("zt_security.transport.time.sleep", delays.append)
    client.observability_metrics()
    assert delays == [2.0]


def test_redirect_is_not_followed(make_client):
    client, session = make_client([response(302, {}, headers={"Location": "https://other.example"})])
    with pytest.raises(ZtSecurityProtocolError):
        client.observability_metrics()
    assert session.calls[0][2]["allow_redirects"] is False
    assert len(session.calls) == 1


def test_successful_html_response_is_protocol_error(make_client):
    client, session = make_client([response(200, raw=b"<html>login</html>")])
    with pytest.raises(ZtSecurityProtocolError):
        client.observability_metrics()
    assert len(session.calls) == 1


def test_unknown_decision_cannot_start_execution(make_client):
    client, session = make_client([response(200, {**decision(), "decision": "NEW_STATE"})])
    with pytest.raises(ZtSecurityProtocolError):
        client.governance.execute(context())
    assert len(session.calls) == 1


def test_scoped_headers_cannot_be_overridden(make_client):
    client, session = make_client([])
    with pytest.raises(ZtSecurityConfigurationError):
        client.http.get("/v1/observability/events", headers={"x-tenant-id": TENANT})
    assert not session.calls


def test_caller_owned_session_is_not_closed_by_client(make_client):
    client, session = make_client([])
    client.close()
    assert not session.closed
    with pytest.raises(ZtSecurityConfigurationError):
        client.observability_metrics()


def test_owned_session_is_closed(monkeypatch):
    from conftest import ScriptedSession

    session = ScriptedSession([])
    monkeypatch.setattr("zt_security.transport.requests.Session", lambda: session)
    with ZtSecurityClient("http://localhost:8080", "secret", TENANT):
        pass
    assert session.closed


def test_bearer_provider_runs_per_attempt_and_no_api_key_is_sent(monkeypatch):
    from conftest import ScriptedSession

    session = ScriptedSession([response(503, {}), response(200, {})])
    tokens = iter(["token-one", "token-two"])
    with ZtSecurityClient(
        "http://localhost:8080", tenant_id=TENANT,
        bearer_token=lambda: next(tokens), session=session,
    ) as client:
        client.observability_metrics()
    assert [call[2]["headers"]["Authorization"] for call in session.calls] == [
        "Bearer token-one", "Bearer token-two"
    ]
    assert all("X-API-Key" not in call[2]["headers"] for call in session.calls)


def test_unsupported_feature_has_dedicated_error(make_client):
    client, session = make_client([response(501, {"error": "ATTESTATION_VERIFIER_NOT_CONFIGURED"})])
    with pytest.raises(ZtSecurityUnsupportedError):
        client.verify_attestation({})
    assert len(session.calls) == 1


def test_service_client_header(make_client):
    client, session = make_client([response(200, {})], client_id="service-a")
    client.observability_metrics()
    assert session.calls[0][2]["headers"]["X-Client-Id"] == "service-a"


@pytest.mark.parametrize("bad_tenant", ["tenant-a", "", None])
def test_invalid_tenant_is_rejected_before_network(bad_tenant):
    with pytest.raises(ZtSecurityConfigurationError):
        ZtSecurityClient("http://localhost:8080", "secret", bad_tenant)

