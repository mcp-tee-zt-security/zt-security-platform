import { AgentRuntime } from "./agent.js";
import { ZtSecurityHttpError } from "./errors.js";
import { GovernanceFacade } from "./governance.js";

export type Json = Record<string, unknown>;

export type ClientOptions = {
  baseUrl: string;
  apiKey: string;
  tenantId: string;
  workspaceId?: string;
  timeoutMs?: number;
  retries?: number;
};

export class ZtClient {
  public readonly governance: GovernanceFacade;
  public readonly agents: AgentRuntime;

  constructor(private readonly options: ClientOptions) {
    this.governance = new GovernanceFacade(this);
    this.agents = new AgentRuntime(this);
  }

  private headers(extra: Record<string, string> = {}): Record<string, string> {
    return {
      "content-type": "application/json",
      "x-api-key": this.options.apiKey,
      "x-tenant-id": this.options.tenantId,
      ...(this.options.workspaceId
        ? { "x-workspace-id": this.options.workspaceId }
        : {}),
      ...extra,
    };
  }

  async request<T>(
    method: string,
    path: string,
    body?: unknown,
    key?: string,
  ): Promise<T> {
    let last: unknown;
    const retries = this.options.retries ?? 2;

    for (let attempt = 0; attempt <= retries; attempt += 1) {
      const controller = new AbortController();
      const timer = setTimeout(
        () => controller.abort(),
        this.options.timeoutMs ?? 10_000,
      );

      try {
        const response = await fetch(
          `${this.options.baseUrl.replace(/\/$/, "")}${path}`,
          {
            method,
            headers: this.headers(key ? { "idempotency-key": key } : {}),
            body: body === undefined ? undefined : JSON.stringify(body),
            signal: controller.signal,
          },
        );
        const text = await response.text();

        if (response.ok) {
          return (text ? JSON.parse(text) : {}) as T;
        }

        if (![429, 502, 503, 504].includes(response.status)) {
          throw new ZtSecurityHttpError(
            response.status,
            text,
            response.headers.get("x-request-id") ?? undefined,
          );
        }
        last = new Error(text);
      } catch (error) {
        last = error;
      } finally {
        clearTimeout(timer);
      }

      if (attempt < retries) {
        await new Promise((resolve) =>
          setTimeout(resolve, 250 * 2 ** attempt),
        );
      }
    }

    throw last;
  }

  evaluate<T = unknown>(request: Json, key?: string) {
    return this.request<T>("POST", "/v1/actions/evaluate", request, key);
  }

  startRuntimeSession<T = unknown>(input: Json) {
    return this.request<T>("POST", "/v1/runtime/sessions", input);
  }

  runtimeCheck<T = unknown>(id: string, request: Json) {
    return this.request<T>("POST", `/v1/runtime/sessions/${id}/check`, request);
  }

  runtimeSession<T = unknown>(id: string) {
    return this.request<T>("GET", `/v1/runtime/sessions/${id}`);
  }

  endRuntimeSession<T = unknown>(id: string) {
    return this.request<T>("POST", `/v1/runtime/sessions/${id}/end`, {});
  }

  policyLint<T = unknown>(text: string) {
    return this.request<T>("POST", "/v1/governance/policies/lint", {
      policyText: text,
    });
  }

  policyBlastRadius<T = unknown>(id: string) {
    return this.request<T>(
      "GET",
      `/v1/governance/policies/${id}/blast-radius`,
    );
  }

  exportPolicy<T = unknown>(id: string) {
    return this.request<T>("GET", `/v1/governance/policies/${id}/as-code`);
  }

  resolveIdentity<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/identity/resolve", body);
  }

  verifyAttestation<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/attestation/verify", body);
  }

  checkCapability<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/capabilities/check", body);
  }

  createEvidence<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/evidence", body);
  }

  verifyEvidence<T = unknown>(id: string) {
    return this.request<T>("POST", `/v1/evidence/${id}/verify`, {});
  }

  requestApproval<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/approvals", body);
  }

  approve<T = unknown>(id: string, body: Json = {}) {
    return this.request<T>("POST", `/v1/approvals/${id}/approve`, body);
  }

  createExecutionContract<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/execution/contracts", body);
  }

  executeContract<T = unknown>(id: string, body: Json = {}) {
    return this.request<T>("POST", `/v1/execution/contracts/${id}/execute`, body);
  }

  verifyExecution<T = unknown>(id: string, body: Json = {}) {
    return this.request<T>("POST", `/v1/execution/${id}/verify`, body);
  }

  registerOrganization<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/federation/organizations", body);
  }

  establishTrust<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/federation/trust", body);
  }

  registerAgent<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/agents", body);
  }

  createMission<T = unknown>(id: string, body: Json) {
    return this.request<T>("POST", `/v1/agents/${id}/missions`, body);
  }

  createSimulation<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/simulations", body);
  }

  runSimulation<T = unknown>(id: string, body: Json = {}) {
    return this.request<T>("POST", `/v1/simulations/${id}/run`, body);
  }

  applyKubernetesPolicy<T = unknown>(body: Json) {
    return this.request<T>("POST", "/v1/kubernetes/policies", body);
  }

  observabilityMetrics<T = unknown>() {
    return this.request<T>("GET", "/v1/observability/metrics");
  }

  replayEvents<T = unknown>(traceId?: string, limit = 100) {
    const query = new URLSearchParams({ limit: String(limit) });
    if (traceId) {
      query.set("traceId", traceId);
    }
    return this.request<T>("GET", `/v1/observability/events?${query.toString()}`);
  }
}
