# Data-plane policy bundle v2

Java `GET /v1/policy-bundles/fast` and Rust use this coordinated protocol. V1 has no compatibility fallback.

JSON fields: canonicalVersion=2, version, tenantId, nullable workspaceId, RFC3339 generatedAt, nullable Base64 signature, nullable keyId, lowercase hex bundleHash, and policies.

- bundleHash: SHA-256 of the canonical bundle payload.
- signature: Ed25519 over the exact canonical bundle payload.
- version: sha256: followed by SHA-256 of the canonical policy-set payload.

The policy-set revision is stable across refresh timestamps, so pinning permits fresh publication of unchanged content.

## Byte encoding

Each nullable field is UTF-8. Null is `-\n`. Non-null is `<decimal UTF-8 byte length>:<value>\n`. Empty string is `0:\n` and differs from null. Line breaks/separators in values count toward the byte length. Decimal numbers have no padding or leading plus; booleans are true/false.

Policies are sorted by ascending signed integer priority, unsigned lexicographic UTF-8 name bytes, then ascending version. Actions retain their JSON array order.

Canonical bundle payload starts with `zt-policy-bundle-v2\n`, then these fields:

1. canonicalVersion
2. tenantId
3. workspaceId
4. version
5. generatedAt
6. keyId
7. policy count
8. each sorted policy, with fields below

Each policy uses name, version, priority, effect, principalType, action count, each action, resourceType, condition, fastPath, in that order. Counts also use the field encoding.

Canonical policy-set payload starts with `zt-policy-set-v2\n`, then tenantId, workspaceId, policy count and the same policy fields. Signature and bundleHash are excluded from both payloads.

Java signing uses PKCS#8 Ed25519 DER Base64 (`ZT_POLICY_SIGNING_PRIVATE_KEY_B64`). Rust verification uses raw 32-byte public-key Base64 (`ZT_POLICY_PUBLIC_KEY_B64`). Evidence signing uses a raw 32-byte seed; a PKCS#8 key cannot be copied into that setting.

## Scope and lifecycle

The data plane accepts only its configured tenant/workspace. Java explicitly applies the workspace and avoids the tenant-only policy cache. Absence of workspace selects tenant-wide policies.

Evaluation metadata including fastPath, condition, key ID and scope is covered by the signature. Changing fastPath and rewriting the unsigned hash/revision cannot repair a signature. Duplicate/unsupported selector clauses remain deferred.

An accepted bundle remains usable until its age limit even after update rejection. Timestamp rollback and same-timestamp conflicting content are rejected. Unsigned development is explicitly reported and never marked signature-valid.

## Shared test vector

Rust tests/security.rs and Java PolicyBundleControllerTest contain the same canonical byte vector. Policy name `정책|x` occupies eight UTF-8 bytes; its field is `8:정책|x\n`. Java must use byte length, not UTF-16 string length. The vector also checks null fields and fast eligibility. Tests were added without execution.
