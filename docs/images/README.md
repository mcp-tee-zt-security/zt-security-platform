# Architecture diagrams

The root README displays the following English diagrams. Both PNG files are stored in this repository so the documentation works independently of local source paths.

| Asset | Purpose |
| --- | --- |
| [architecture-current.png](architecture-current.png) | Current implementation and integration boundaries |
| [architecture-target.png](architecture-target.png) | Planned deployment and trust boundaries; not yet implemented |

## Current implementation

The current diagram distinguishes Java's full authorization and direct MCP execution path from the separate Rust policy-only fast path. General SDK execution requires a separately installed connector. Rust evidence signing is optional. Forwarded identity claims are unverified, and no real TEE attestation or capability verifier is installed. Infrastructure icons describe configuration assets, not a claim of production deployment or certification.

## Planned architecture

The target diagram separates the managed Control Plane from the customer VPC/on-premises Data Plane. It distinguishes initial Nitro attestation of the Rust policy engine from later protection of MCP execution and business-tool credentials. Metadata export is a customer-side component that sends configured information to the Control Plane. Short-lived trust bounds offline revocation delay; the diagram does not claim instantaneous revocation or continuous hardware verification.

## Reference originals

The user-provided originals are retained unchanged under [references/](references/):

- [Current architecture reference](references/architecture-current-original.png)
- [Target architecture reference](references/architecture-target-original.png)

These originals contain superseded labels and connections. Use the corrected diagrams above for current documentation.

## Editing brief

The corrected raster assets were edited with the built-in ImageGen tool using the supplied originals as references. The editing instructions were:

- Current: retain the English enterprise infographic style; show authenticated clients entering the integrated Java backend; connect Java directly to registered MCP servers and business APIs; keep Rust separate as `POLICY_ONLY`; disclose unverified identity, absent attestation/capability verification, optional evidence signatures and the general SDK connector requirement; label approval as request-bound human approval and Redis as backend cache.
- Target: retain the visual style; display “Planned architecture — not yet implemented”; separate the managed Control Plane from the customer environment; show fresh evidence, nonce and public-key binding, short-lived trust and renewal; distinguish the initial Rust TEE milestone from later secure MCP execution; place business-tool credentials in the planned protected connector boundary; label the local export component as “Metadata Export (customer side)”; disclose trust-expiry enforcement and revocation delay.

The README text and component API documentation remain the authoritative description of behavior. Update these diagrams when runtime boundaries change.
