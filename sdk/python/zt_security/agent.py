from __future__ import annotations

from dataclasses import replace
from typing import TYPE_CHECKING, Any

from .errors import ZtSecurityConfigurationError, ZtSecurityError
from .models import ActionContext, JsonObject

if TYPE_CHECKING:
    from .client import ZtSecurityClient


class AgentRuntime:
    """Identity-bound missions backed by the server's existing task/tool permissions."""

    def __init__(self, client: ZtSecurityClient):
        self.client = client

    def register(self, agent: JsonObject) -> JsonObject:
        return self.client.register_agent(agent)

    def mission(self, agent_id: str, mission: JsonObject) -> JsonObject:
        return self.client.create_mission(agent_id, mission)

    def execute_governed(
        self, agent_id: str, context: ActionContext, **kwargs: Any,
    ) -> dict[str, Any]:
        self.client._body(context)  # Validate scope/JSON before creating a task.
        mission = self.client.create_mission_typed(
            agent_id, {"objective": context.action, "resource": context.resource}
        )
        tool_id = context.attributes.get("tool_id", mission.tool_ids[0])
        if tool_id not in mission.tool_ids:
            raise ZtSecurityConfigurationError("Selected tool is not delegated to the mission")
        context = replace(
            context, attributes={**context.attributes, "task_id": mission.external_task_id,
                                 "tool_id": tool_id}
        )
        try:
            execution = self.client.governance.execute(context, **kwargs)
        except ZtSecurityError as error:
            error.workflow = {"mission_id": mission.id, "external_task_id": mission.external_task_id,
                              **(error.workflow or {})}
            raise
        return {"mission": mission.to_dict(), "execution": execution}

