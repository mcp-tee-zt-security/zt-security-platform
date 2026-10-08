export type EventEnvelope = {
  eventId: string;
  eventType: string;
  occurredAt: string;
  tenantId: string;
  workspaceId?: string;
  subject: string;
  correlationId?: string;
  causationId?: string;
  traceId?: string;
  payload: Record<string, unknown>;
  evidenceRefs?: string[];
};

export class EventStream {
  constructor(private readonly client: { request: <T>(method: string, path: string) => Promise<T> }) {}

  replay<T = EventEnvelope>(path: string, limit = 100): Promise<T[]> {
    return this.client.request<T[]>("GET", `${path}?limit=${limit}`);
  }
}
