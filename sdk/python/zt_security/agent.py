from typing import Any

from .models import ActionContext


class AgentRuntime:
    """High-level agent and governed mission facade."""

    def __init__(self, client: Any):
        self.client = client

    def register(self, agent: dict[str, Any]) -> dict[str, Any]:
        return self.client.register_agent(agent)

    def mission(self, agent_id: str, mission: dict[str, Any]) -> dict[str, Any]:
        return self.client.create_mission(agent_id, mission)

    def execute_governed(
        self,
        agent_id: str,
        context: ActionContext,
        **kwargs: Any,
    ) -> dict[str, Any]:
        mission = self.mission(
            agent_id,
            {
                "objective": context.action,
                "resource": context.resource,
            },
        )
        execution = self.client.governance.execute(context, **kwargs)
        return {"mission": mission, "execution": execution}
