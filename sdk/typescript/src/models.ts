export type Decision = "ALLOW" | "DENY" | "STEP_UP" | "REQUIRE_APPROVAL";

export type ActionContext = {
  subject: string;
  action: string;
  resource: string;
  tenantId: string;
  attributes?: Record<string, unknown>;
};

export type GovernedExecutionResult = {
  decision: unknown;
  evidence?: unknown;
  approval?: unknown;
  contract?: unknown;
  execution?: unknown;
  verification?: unknown;
};
