from __future__ import annotations

import json
from typing import Any

import pytest
import requests

from zt_security import ZtSecurityClient

TENANT = "11111111-1111-1111-1111-111111111111"
WORKSPACE = "88888888-8888-8888-8888-888888888801"
REQUEST = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
DECISION = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
CONTRACT = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
APPROVAL = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
EXECUTION = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
VERIFICATION = "ffffffff-ffff-4fff-8fff-ffffffffffff"


def response(status: int, payload: Any = None, *, headers=None, raw: bytes | None = None):
    value = requests.Response()
    value.status_code = status
    value.reason = "HTTP failure" if status >= 400 else "OK"
    value._content = raw if raw is not None else json.dumps(payload).encode()
    value._content_consumed = True
    value.headers.update(headers or {})
    return value


class ScriptedSession(requests.Session):
    def __init__(self, script):
        super().__init__()
        self.script = list(script)
        self.calls = []
        self.closed = False

    def request(self, method, url, **kwargs):
        self.calls.append((method, url, kwargs))
        if not self.script:
            raise AssertionError("Unexpected extra HTTP request")
        value = self.script.pop(0)
        if isinstance(value, Exception):
            raise value
        return value

    def close(self):
        self.closed = True
        super().close()


def decision(state="ALLOW"):
    return {"id": DECISION, "requestId": REQUEST, "decision": state,
            "reason": "Fixture policy result", "traceId": REQUEST,
            "risk": {"score": 20, "level": "LOW"}}


def contract(state="READY"):
    return {"id": CONTRACT, "decisionId": DECISION, "action": "payment.transfer",
            "resource": "bank_account/ACC-1001", "status": state,
            "expiresAt": "2099-01-01T00:00:00Z", "evidenceIds": [],
            "approvalId": APPROVAL if state == "PENDING_APPROVAL" else None}


def approval():
    return {"id": APPROVAL, "requestId": REQUEST, "status": "PENDING"}


def execution(state="UNSUPPORTED"):
    return {"id": EXECUTION, "contractId": CONTRACT, "status": state,
            "result": {"executed": state == "SUCCEEDED"}}


@pytest.fixture(autouse=True)
def no_real_sleep(monkeypatch):
    monkeypatch.setattr("zt_security.transport.time.sleep", lambda seconds: None)
    monkeypatch.setattr("zt_security.transport.random.uniform", lambda low, high: 0)


@pytest.fixture
def make_client():
    clients = []

    def make(script, **kwargs):
        session = ScriptedSession(script)
        client = ZtSecurityClient(
            "http://localhost:8080", "test-secret", TENANT, WORKSPACE,
            session=session, **kwargs
        )
        clients.append((client, session))
        return client, session

    yield make
    for client, session in clients:
        client.close()
        session.close()

