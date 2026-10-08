import type { ActionContext, GovernedExecutionResult } from "./models.js";

export class GovernanceFacade {
  constructor(private readonly client: any) {}

  async evaluate(context: ActionContext) {
    return this.client.evaluate({
      subject: context.subject,
      action: context.action,
      resource: context.resource,
      tenantId: context.tenantId,
      attributes: context.attributes ?? {},
    });
  }

  async execute(
    context: ActionContext,
    options: { evidence?: any; approval?: any } = {},
  ): Promise<GovernedExecutionResult> {
    const decision = await this.evaluate(context);
    if (decision.decision === "DENY") {
      return { decision };
    }

    const evidence = options.evidence
      ? await this.client.createEvidence(options.evidence)
      : undefined;
    const approval =
      ["STEP_UP", "REQUIRE_APPROVAL"].includes(decision.decision)
        ? (options.approval ??
          (await this.client.requestApproval({
            action: context.action,
            resource: context.resource,
            decision,
          })))
        : options.approval;

    const contract = await this.client.createExecutionContract({
      action: context.action,
      resource: context.resource,
      policyDecision: decision,
      evidenceIds: evidence?.id ? [evidence.id] : [],
      approval,
    });
    if (["STEP_UP", "REQUIRE_APPROVAL"].includes(decision.decision) && approval?.status !== "APPROVED") {
      return { decision, evidence, approval, contract };
    }
    const execution = await this.client.executeContract(contract.id, {});
    const verification = await this.client.verifyExecution(execution.id, {});

    return {
      decision,
      evidence,
      approval,
      contract,
      execution,
      verification,
    };
  }
}
