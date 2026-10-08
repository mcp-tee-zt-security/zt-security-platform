import type { ActionContext } from "./models.js";

export class AgentRuntime {
  constructor(private readonly client: any) {}

  register(agent: Record<string, unknown>) {
    return this.client.registerAgent(agent);
  }

  mission(agentId: string, mission: Record<string, unknown>) {
    return this.client.createMission(agentId, mission);
  }

  async executeGoverned(
    agentId: string,
    context: ActionContext,
    options: Record<string, unknown> = {},
  ) {
    const mission = await this.mission(agentId, {
      objective: context.action,
      resource: context.resource,
    });
    const execution = await this.client.governance.execute({ ...context,
      attributes: { ...context.attributes, task_id: mission.externalTaskId,
        tool_id: context.attributes?.tool_id ?? mission.toolIds[0] }
    }, options);
    return { mission, execution };
  }
}
