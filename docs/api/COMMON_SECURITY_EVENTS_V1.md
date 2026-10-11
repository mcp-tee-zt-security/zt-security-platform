# Common security activity contract — v1

Workflow Chat, MCP order tools, retrieval/file access and runtime evaluation use `security_activities`. This append-only observation ledger complements the original audit/evidence records. It never grants authorization or bypasses the existing execution gates.

## Event fields

| Field | Meaning |
| --- | --- |
| schemaVersion | Contract version, currently 1 |
| eventId / sourceEventId | Common ledger UUID and original domain record/transition reference |
| tenantId / workspaceId | Original server-validated scope |
| source | WORKFLOW, MCP, FILE, AUTH, RUNTIME, SYNC, CONTROL, OTHER; future sources use the same contract |
| eventType / stage | Concrete event and AUTHORIZATION, EXECUTION, LIFECYCLE or OBSERVATION |
| occurredAt | UTC observation time; imported events retain original timestamps |
| correlationId / requestId | Trusted lifecycle group and optional policy evaluation UUID |
| caller / humanSubject / aiSubject | Authenticated caller, recorded delegating human and evaluated AI; missing historical attribution stays missing |
| action / resourceType / resourceId | Operation and stable target reference |
| decision / status | Policy decision, when applicable, and actual stage status |
| riskScore | Actual measured score; null means not measured |
| analysisEligible | A decisive AI authorization sample, not an approval, delegation or execution marker |
| metadata | Allowlisted evidence references, policy references, reason and measured counters |

No raw prompts, tool arguments, file contents, bearer tokens, refresh tokens or credentials are copied into the common ledger or delivery envelope. Original module records remain in their existing stores. Do not put content or secrets in resource identifiers or event labels.

## Internal producers

Use `SecurityEventService.publish(CommonSecurityEvent)` inside the domain transaction, after the authoritative authorization or execution result is known. Supply IDs from authenticated identities and persisted bindings, rather than client assertions. Choose a stable `originId` for a logical observation. Retrying the same logical observation must reuse its origin. File/policy audit origins use their original audit UUID; workflow snapshots use their persisted record UUID. Lifecycle updates use a distinct persisted transition origin.

The publisher appends the common record and its versioned outbox delivery in the same database transaction. An identical typed origin/event is idempotent. Existing `enqueue` producers are bridged as lifecycle observations; each enqueue remains a separate observation and retains the returned outbox ID used by older evidence references. New adapters should use the typed producer contract.

There is deliberately no public API for an AI or client to declare its own trusted ALLOW event. New upstream clients still connect through the existing gateways and adapters.

## Reads and scope

- `GET /v1/events?hours=24&limit=50`: recent common activity, limited to 1,000 rows.
- `GET /v1/events/summary?hours=24`: counts across the entire selected time window, independent of the recent-row limit.
- `GET /v1/events/contract`: stage and schema conventions.
- `GET /v1/audit/events`: full paginated common history with source, decision and exact subject filters; existing detail endpoint uses common event UUIDs.

All history/summary requests use `X-Tenant-Id` and `X-Workspace-Id`. Workspace JWT access is checked against current grants; privileged auditors can view the selected workspace, other callers can view their own attributed records. Database RLS matches the exact original scope. Scope-less historical observations remain scope-less; they are not reassigned to a guessed demo workspace.

`/v1/events` now returns recorded activity rather than only pending delivery rows. Delivery workers continue using the internal pending-outbox repository. Consumers of the old endpoint/payload must migrate to the version-1 envelope.

## Counting and analysis

A file request commonly produces human delegation, AI authorization and human revocation. All three remain visible; only the AI authorization is a risk-analysis sample. Runtime adapters can record the same policy request as the policy evaluator; analysis deduplicates by request UUID, preferring the original authorization record. Separate actual re-evaluations, such as the check before resumed execution, retain their own UUID and count separately.

Risk and forecast reads use up to 10,000 newest scoped, deduplicated decision samples. The common summary totals are calculated independently across the full selected time window.

File ACL/policy evaluation currently does not compute a runtime risk score. These decisions participate in outcome-pressure analysis but their score remains null. Aggregate outcome pressure is labeled separately from measured event risk and is not a detected anomaly or threat score. A normal policy DENY does not fabricate a runtime threat or active runtime session.

Forecasts require at least five measured decisions across five minutes. Missing measurements and insufficient samples return `INSUFFICIENT_DATA`, null forecast/probability/confidence and `OBSERVE_MORE_DATA`. The preventive proposal endpoint computes its evidence on the server and rejects insufficient evidence; it does not trust forecast values submitted by the browser.

Security Graph shows recorded actions, identities, policy references and resources in the selected scope. Authorization edges are `EVALUATED`; `EXECUTED` requires an execution-stage success/tool-error record. A denied request cannot become an execution edge. Nodes without measured risk carry `riskMeasured=false`; the visualization fallback is not a zero-risk measurement.

Runtime anomaly records remain real detector outputs and are associated with scoped request IDs. Compliance, control effectiveness and governance records still require their own assessments, executed controls and evidence; common activity alone does not certify those outcomes. Specialized control feedback/profile storage retains its existing tenant-level contract; common event history in Audit Logs is explicitly workspace scoped.

## Historical import and limitations

Migration V13 projects existing policy audits, file audits, runtime events, saved MCP states, workflow records, retained approval states and legacy module outbox observations without modifying original records. It selects each original tenant/workspace explicitly, respects forced RLS, uses deterministic import IDs and marks metadata `imported=true`. A historical MCP state is only the state retained by its source table; transitions that were never recorded cannot be reconstructed. Imported records are not replayed to the delivery outbox.

The new ledger is protected against SQL updates/deletes. This is not TEE sealing, a new cryptographic hash chain, a guarantee against a database administrator, or end-to-end exactly-once upstream execution. The existing audit-chain verifier continues to verify its original policy audit chain.
