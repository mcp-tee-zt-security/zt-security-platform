# Changelog

## 2.1.0 — Unreleased

- Target common SDK contract revision 1.0.0 on the integrated 4.75 API.
- Add typed requests and governed lifecycle/mission/event response models.
- Retain dictionary-returning client methods; add typed alternatives and py.typed.
- Reuse HTTP sessions and support context-managed cleanup and borrowed sessions.
- Add service-client authentication and bearer token/provider authentication.
- Classify HTTP/network/configuration/protocol errors and preserve correlation metadata.
- Limit automatic retries to GET/HEAD and idempotent evaluate requests; honor Retry-After.
- Stop pending approvals and resume saved contracts without new SDK preparation.
- Preserve known resource IDs and failure stage on workflow exceptions for reconciliation.
- Parse JSON event snapshots instead of incorrectly expecting SSE.
- Never classify unsupported/unverified execution as successful.
- Add mock HTTP, workflow and contract fixture tests; not executed in this session.

Compatibility tightening: UUID/resource validation, unknown enum rejection,
scoped/authentication header overrides, redirects, unsafe write replay, and
arbitrary SSE replay paths. Packaging and actual-server release checks remain pending.
