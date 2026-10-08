from __future__ import annotations

from copy import deepcopy
from pathlib import Path

import pytest
import yaml
from jsonschema import Draft4Validator

from zt_security import ActionContext, ExecutionContract, PolicyDecision, ZtSecurityServerError
from conftest import CONTRACT, TENANT, contract, decision, response


CONTRACT_PATH = Path(__file__).resolve().parents[2] / "contracts" / "sdk-api.yaml"


@pytest.fixture(scope="module")
def api_contract():
    return yaml.safe_load(CONTRACT_PATH.read_text(encoding="utf-8"))


def json_schema(value):
    """Translate OpenAPI 3.0 nullable into its JSON Schema equivalent for fixture tests."""
    if isinstance(value, list):
        return [json_schema(item) for item in value]
    if not isinstance(value, dict):
        return value
    result = {key: json_schema(item) for key, item in value.items() if key != "nullable"}
    if value.get("nullable") and isinstance(result.get("type"), str):
        result["type"] = [result["type"], "null"]
    return result


def validate_fixture(api_contract, schema_name, payload):
    schema = json_schema({
        "$ref": f"#/components/schemas/{schema_name}",
        "components": deepcopy(api_contract["components"]),
    })
    Draft4Validator(schema).validate(payload)


def test_sdk_request_and_reply_share_the_common_contract(api_contract):
    request = ActionContext(
        "payment-agent", "payment.transfer", "bank_account/ACC-1001", TENANT,
        {"amount": 100000, "task_id": "payment-demo-task",
         "tool_id": "33333333-3333-3333-3333-333333333301"},
    ).to_dict()
    validate_fixture(api_contract, "SdkRequest", request)
    reply = {**decision(), "additionalServerField": "retained"}
    validate_fixture(api_contract, "Decision", reply)
    assert PolicyDecision.from_dict(reply).to_dict() == reply


def test_nullable_approval_reference_matches_contract_model(api_contract):
    reply = contract()
    validate_fixture(api_contract, "ExecutionContract", reply)
    assert ExecutionContract.from_dict(reply).approval_id is None


def test_event_contract_is_a_json_array(api_contract):
    operation = api_contract["paths"]["/v1/observability/events"]["get"]
    schema = operation["responses"]["200"]["content"]["application/json"]["schema"]
    assert schema["type"] == "array"
    assert "text/event-stream" not in operation["responses"]["200"]["content"]


def test_all_non_replayable_post_contracts_remain_single_attempt(api_contract, make_client):
    for path, methods in api_contract["paths"].items():
        operation = methods.get("post", {})
        if operation.get("x-sdk-retry-policy") != "no-automatic-retry":
            continue
        resolved = path.replace("{id}", CONTRACT).replace("{incidentId}", "incident-one")
        client, session = make_client([response(503, {"error": "UNAVAILABLE"})])
        with pytest.raises(ZtSecurityServerError):
            client.http.post(resolved, {}, idempotency_key="explicit-key")
        assert len(session.calls) == 1, path
