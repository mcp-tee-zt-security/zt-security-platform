from __future__ import annotations

import pytest

from zt_security import ZtSecurityProtocolError
from conftest import REQUEST, TENANT, WORKSPACE, response


def event(tenant=TENANT):
    return {"eventId": REQUEST, "tenantId": tenant, "workspaceId": WORKSPACE,
            "eventType": "EXECUTION_CREATED", "occurredAt": "2026-10-08T10:00:00Z",
            "subject": "payment-agent", "payload": {}, "traceId": "trace & another=value"}


def test_json_replay_is_not_parsed_as_sse(make_client):
    client, session = make_client([response(200, [event()])])
    events = list(client.events.replay(trace_id="trace & another=value"))
    assert events[0].subject == "payment-agent"
    assert session.calls[0][2]["params"] == {"limit": 100, "traceId": "trace & another=value"}
    assert "traceId=" not in session.calls[0][1]


def test_cross_tenant_event_is_rejected(make_client):
    client, session = make_client([response(200, [event(REQUEST)])])
    with pytest.raises(ZtSecurityProtocolError):
        client.replay_events()


def test_additive_fields_survive_model_roundtrip(make_client):
    body = {**event(), "futureField": {"new": True}}
    client, session = make_client([response(200, [body])])
    assert client.replay_events_typed()[0].to_dict()["futureField"] == {"new": True}


def test_java_nanosecond_timestamp_is_supported_on_python_310(make_client):
    timestamp = "2026-10-08T10:00:00.123456789Z"
    client, session = make_client([response(200, [{**event(), "occurredAt": timestamp}])])
    result = client.replay_events_typed()[0]
    assert result.occurred_at.microsecond == 123456
    assert result.to_dict()["occurredAt"] == timestamp

